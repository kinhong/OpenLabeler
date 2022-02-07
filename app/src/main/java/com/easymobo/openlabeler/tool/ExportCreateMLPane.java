/*
 * Copyright (c) 2022. Kin-Hong Wong. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.easymobo.openlabeler.tool;

import com.easymobo.openlabeler.model.Annotation;
import com.easymobo.openlabeler.model.BoundBox;
import com.easymobo.openlabeler.model.ObjectModel;
import com.easymobo.openlabeler.preference.Settings;
import com.easymobo.openlabeler.ui.InputFileChooser;
import com.easymobo.openlabeler.util.AppUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.commons.lang3.StringUtils;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.easymobo.openlabeler.OpenLabeler.APP_ICON;

public class ExportCreateMLPane extends DialogPane
{
   @FXML
   private InputFileChooser dirMedia, dirAnnotation, fileOutput;
   @FXML
   private CheckBox chkFormatJSON;

   private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());
   private ResourceBundle bundle = ResourceBundle.getBundle("bundle");
   private JAXBContext jaxbContext;
   private CreateML createML;

   public ExportCreateMLPane() {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/tool/ExportCreateML.fxml"), bundle);
      loader.setRoot(this);
      loader.setController(this);

      try {
         loader.load();
         jaxbContext = JAXBContext.newInstance(Annotation.class);
      }
      catch (Exception ex) {
         LOG.log(Level.SEVERE, "Unable to load FXML", ex);
      }

      bindProperties();
   }

   public void showAndWait(Annotation model) {
      Dialog dialog = new Dialog();
      dialog.setDialogPane(this);
      dialog.setTitle(StringUtils.stripEnd(bundle.getString("menu.exportCreateML"), "..."));
      dialog.setResizable(true);

      Stage stage = (Stage)getScene().getWindow();
      stage.getIcons().add(new Image(getClass().getClassLoader().getResourceAsStream(APP_ICON)));

      ButtonType export = new ButtonType(bundle.getString("label.export"), ButtonBar.ButtonData.APPLY);
      getButtonTypes().addAll(export, ButtonType.CLOSE);
      getScene().getWindow().setOnCloseRequest(e -> save());

      // defaults
      dirMedia.setText(model.getFile().getParent());
      dirAnnotation.setText(AppUtils.getAnnotationFile(model.getFile()).getParent());
      fileOutput.setFilters(new FileChooser.ExtensionFilter("JSON Files", "*.json", "*.JSON"));
      chkFormatJSON.setSelected(createML.isFormatJSON());

      Button exportBtn = (Button) lookupButton(export);
      exportBtn.disableProperty().bind(fileOutput.textProperty().isEmpty());
      exportBtn.addEventFilter(ActionEvent.ACTION, event -> {
         save();
         exportCreateML(createML, dirMedia.toFile(), dirAnnotation.toFile(), fileOutput.toFile());
         event.consume(); // Don't close dialog
      });

      Optional<ButtonType> result = dialog.showAndWait();
      result.ifPresent(res -> {
         if (res.equals(ButtonType.CLOSE)) {
            save();
         }
      });
   }

   private void bindProperties() {
      ObjectMapper mapper = AppUtils.createJSONMapper();
      try {
         if (!Settings.getToolCreateMLJson().isBlank()) {
            createML = mapper.readValue(Settings.getToolCreateMLJson(), CreateML.class);
         }
         if (createML == null) {
            createML = new CreateML();
         }

         // Output
         chkFormatJSON.selectedProperty().bindBidirectional(createML.formatJSONProperty);
         fileOutput.textProperty().bindBidirectional(createML.outputProperty);
      } catch (Exception ex) {
         LOG.log(Level.WARNING, "Unable to initialize CreateML info section", ex);
      }
   }

   private void save() {
      ObjectMapper mapper = AppUtils.createJSONMapper();
      try {
         Settings.setToolCreateMLJson(mapper.writeValueAsString(createML));
      }
      catch (Exception ex) {
         LOG.log(Level.WARNING, "Unable to save CreateML info section", ex);
      }
   }

   private void exportCreateML(CreateML template, File mediaDir, File annotationDir, File output) {
      int imageCount = 0, annotationCount = 0, errorCount = 0;
      try {
         JsonGenerator writer = new JsonFactory().createGenerator(output, JsonEncoding.UTF8);
         writer.setCodec(AppUtils.createJSONMapper());
         if (chkFormatJSON.isSelected()) {
            writer.useDefaultPrettyPrinter();
         }

         LOG.info("Starting export CreateML JSON");
         writer.writeStartArray();

         var annotations = annotationDir.listFiles((dir, name) -> {
            name = name.toLowerCase();
            return name.endsWith(".xml");
         });
         for (File annotation : annotations) {
            Annotation model;
            try {
               Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
               model = (Annotation) unmarshaller.unmarshal(annotation);
            } catch (Exception ex) {
               errorCount++;
               LOG.warning(String.format("Unable to read annotation from %s", annotation));
               continue;
            }
            File image = Paths.get(dirMedia.getText(), model.getFilename()).toFile();
            if (image == null || !image.exists()) {
               LOG.warning(String.format("Image %s in %s does not exist", image, annotation));
               errorCount++;
               continue;
            }

            try {
               writer.writeStartObject();
               writer.writeStringField("image", model.getFilename());
               writer.writeArrayFieldStart("annotations");
               for (ObjectModel objModel : model.getObjects()) {
                  try {
                     writeAnnotation(writer, objModel);
                     annotationCount++;
                  } catch (IOException ex) {
                     errorCount++;
                     LOG.warning(String.format("Unable to write annotation %s from %s", objModel.getName(), annotation));
                  }
               }
               writer.writeEndArray(); // annotations
               imageCount++;
               writer.writeEndObject();
            }
            catch (NumberFormatException ex) {
               errorCount++;
               LOG.warning(String.format("Unable to extract ID from %s", image));
               continue;
            } catch (IOException ioex) {
               errorCount++;
               LOG.warning(String.format("Unable to write image from %s", image));
               continue;
            }
         }
         writer.writeEndArray();
         writer.flush();

         // Show export summary
         var msg = errorCount <= 0 ? AppUtils.format(bundle, "msg.exportCreateMLNoError", imageCount, annotationCount, output) :
               AppUtils.format(bundle, "msg.exportCreateMLWithError", imageCount, annotationCount, errorCount, output);
         AppUtils.showInformation(bundle.getString("label.export"), msg);
         LOG.info(msg);
      } catch (Exception ex) {
         LOG.log(Level.SEVERE, "Unable to export CreateML", ex);
         AppUtils.showError(bundle.getString("label.alert"), bundle.getString("msg.unableToExport"));
      }
   }

   private void writeAnnotation(JsonGenerator writer, ObjectModel model) throws IOException {
      writer.writeStartObject();

      writer.writeStringField("label", model.getName());

      BoundBox bb = model.getBoundBox();
      Map<String, Integer> coordinates = new HashMap() {{
         put("x", Math.round((bb.getXMin() + bb.getXMax()) / 2));
         put("y", Math.round((bb.getYMin() + bb.getYMax()) / 2));
         put("width", Math.round(bb.getWidth()));
         put("height", Math.round(bb.getHeight()));
      }};
      writer.writeObjectField("coordinates", coordinates);

      writer.writeEndObject();
   }

   // CreateML Json template
   private static class CreateML {
      @JsonIgnore
      public final BooleanProperty formatJSONProperty = new SimpleBooleanProperty(false);
      public boolean isFormatJSON() {
         return formatJSONProperty.get();
      }
      public void setFormatJSON(boolean formatJSON) {
         this.formatJSONProperty.setValue(formatJSON);
      }

      @JsonIgnore
      public final StringProperty outputProperty = new SimpleStringProperty();
      public String getOutput() {
         return outputProperty.get();
      }
      public void setOutput(String output) {
         this.outputProperty.setValue(output);
      }
   }
}
