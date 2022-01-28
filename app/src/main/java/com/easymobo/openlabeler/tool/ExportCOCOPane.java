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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.converter.NumberStringConverter;
import org.apache.commons.lang3.StringUtils;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static com.easymobo.openlabeler.OpenLabeler.APP_ICON;
import static java.util.Map.Entry.comparingByValue;

public class ExportCOCOPane extends DialogPane
{
   @FXML
   private TextField txtInfoYear, txtInfoVersion, txtInfoDescription, txtInfoContributor, txtInfoUrl;
   @FXML
   private DatePicker datePicker;
   @FXML
   private InputFileChooser dirMedia, dirAnnotation, fileOutput;
   @FXML
   private TextField txtLicenseId, txtLicenseName, txtLicenseUrl;
   @FXML
   private RadioButton rbNameAsId, rbUsePathInXml;
   @FXML
   private CheckBox chkFormatJSON;

   private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());
   private ResourceBundle bundle = ResourceBundle.getBundle("bundle");
   private JAXBContext jaxbContext;
   private COCO coco;

   public ExportCOCOPane() {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/tool/ExportCOCO.fxml"), bundle);
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
      dialog.setTitle(StringUtils.stripEnd(bundle.getString("menu.exportCOCO"), "..."));
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
      rbNameAsId.setSelected(coco.isNameAsId());
      rbUsePathInXml.setSelected(coco.isUsePathInXml());
      chkFormatJSON.setSelected(coco.isFormatJSON());

      Button exportBtn = (Button) lookupButton(export);
      exportBtn.disableProperty().bind(fileOutput.textProperty().isEmpty());
      exportBtn.addEventFilter(ActionEvent.ACTION, event -> {
         save();
         exportCOCO(coco, dirMedia.toFile(), dirAnnotation.toFile(), fileOutput.toFile());
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
         if (!Settings.getToolCOCOJson().isBlank()) {
            coco = mapper.readValue(Settings.getToolCOCOJson(), COCO.class);
         }
         if (coco == null) {
            coco = new COCO();
         }
         // Fields that can only accept numbers
         txtInfoYear.setTextFormatter(AppUtils.createNumberTextFormatter());
         txtLicenseId.setTextFormatter(AppUtils.createNumberTextFormatter());

         // COCO info section
         Bindings.bindBidirectional(txtInfoYear.textProperty(), coco.info.yearProperty, new NumberStringConverter("#"));
         txtInfoVersion.textProperty().bindBidirectional(coco.info.versionProperty);
         txtInfoDescription.textProperty().bindBidirectional(coco.info.descriptionProperty);
         txtInfoContributor.textProperty().bindBidirectional(coco.info.contributorProperty);
         txtInfoUrl.textProperty().bindBidirectional(coco.info.urlProperty);
         datePicker.valueProperty().bindBidirectional(coco.info.dateCreatedProperty);

         // COCO images
         rbUsePathInXml.selectedProperty().bindBidirectional(coco.usePathInXmlProperty);
         rbNameAsId.selectedProperty().bindBidirectional(coco.nameAsIdProperty);
         dirMedia.disableProperty().bindBidirectional(coco.usePathInXmlProperty);

         // COCO license section
         Bindings.bindBidirectional(txtLicenseId.textProperty(), coco.license.idProperty, new NumberStringConverter("#"));
         txtLicenseName.textProperty().bindBidirectional(coco.license.nameProperty);
         txtLicenseUrl.textProperty().bindBidirectional(coco.license.urlProperty);

         // Output
         chkFormatJSON.selectedProperty().bindBidirectional(coco.formatJSONProperty);
         fileOutput.textProperty().bindBidirectional(coco.outputProperty);
      } catch (Exception ex) {
         LOG.log(Level.WARNING, "Unable to initialize COCO info section", ex);
      }
   }

   private void save() {
      ObjectMapper mapper = AppUtils.createJSONMapper();
      try {
         Settings.setToolCOCOJson(mapper.writeValueAsString(coco));
      }
      catch (Exception ex) {
         LOG.log(Level.WARNING, "Unable to save COCO info section", ex);
      }
   }

   private void exportCOCO(COCO template, File mediaDir, File annotationDir, File output) {
      var regex = Pattern.compile("[^\\d-_.]*([\\d-_.]+).*");
      var modelMap = new HashMap<File, Annotation>();
      var categoryMap = new HashMap<String, Integer>();
      int imageCount = 0, annotationCount = 0, errorCount = 0;
      try {
         JsonGenerator writer = new JsonFactory().createGenerator(output, JsonEncoding.UTF8);
         writer.setCodec(AppUtils.createJSONMapper());
         if (chkFormatJSON.isSelected()) {
            writer.useDefaultPrettyPrinter();
         }

         LOG.info("Starting export COCO JSON");
         writer.writeStartObject();

         // Info section
         writer.writeObjectField("info", template.info);

         // First pass, resolve image files and IDs, output images section
         var annotations = annotationDir.listFiles((dir, name) -> {
            name = name.toLowerCase();
            return name.endsWith(".xml");
         });
         writer.writeArrayFieldStart("images");
         var imageId = 1;
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
            File image = template.isUsePathInXml() ? new File(model.getPath())
                  : Paths.get(dirMedia.getText(), model.getFilename()).toFile();
            if (image == null || !image.exists()) {
               LOG.warning(String.format("Image %s in %s does not exist", image, annotation));
               errorCount++;
               continue;
            }

            try {
               var id = rbNameAsId.isSelected() ? extractIdFromName(regex, image.getName()) : imageId++;
               model.setId(id);

               writeImage(writer, model, model.getId(), template.license.getId());
               modelMap.put(annotation, model);
               imageCount++;
            } catch (NumberFormatException ex) {
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

         // Second pass, output JSON images and annotations sections
         writer.writeArrayFieldStart("annotations");
         var annotationId = 1;
         for (Map.Entry<File, Annotation> entry : modelMap.entrySet()) {
            var annotation = entry.getKey();
            var model = entry.getValue();
            for (ObjectModel objModel : model.getObjects()) {
               try {
                  writeAnnotation(writer, objModel, annotationId++, model.getId(), categoryMap);
                  annotationCount++;
               } catch (IOException ex) {
                  errorCount++;
                  LOG.warning(String.format("Unable to write annotation %s from %s", objModel.getName(), annotation));
               }
            }
         }
         writer.writeEndArray();

         // Categories
         writer.writeArrayFieldStart("categories");
         errorCount += categoryMap.entrySet().stream().sorted(comparingByValue()).mapToInt(entry -> {
            try {
               writeCategory(writer, entry.getValue(), entry.getKey());
            } catch (Exception ex) {
               LOG.warning(String.format("Unable to write category %s", entry.getKey()));
               return 1;
            }
            return 0;
         }).filter(i -> i > 0).count();
         writer.writeEndArray();

         // Licenses section
         writer.writeArrayFieldStart("licenses");
         writer.writeObject(template.license);
         writer.writeEndArray();

         writer.writeEndObject();
         writer.flush();

         // Show export summary
         var msg = errorCount <= 0 ? AppUtils.format(bundle, "msg.exportCOCONoError", imageCount, annotationCount, output) :
               AppUtils.format(bundle, "msg.exportCOCOWithError", imageCount, annotationCount, errorCount, output);
         AppUtils.showInformation(bundle.getString("label.export"), msg);
         LOG.info(msg);
      } catch (Exception ex) {
         LOG.log(Level.SEVERE, "Unable to export COCO", ex);
         AppUtils.showError(bundle.getString("label.alert"), bundle.getString("msg.unableToExport"));
      }
   }

   private int extractIdFromName(Pattern regex, String name) throws NumberFormatException {
      var matcher = regex.matcher(name);
      if (matcher.matches()) {
         var part = matcher.group(1).replaceAll("[-_.]", "");
         return Integer.parseInt(part);
      } else {
         throw new NumberFormatException();
      }
   }

   // Missing "coco_url", "flickr_url", "date_captured"
   private void writeImage(JsonGenerator writer, Annotation model, int id, int licenseId) throws IOException {
      writer.writeStartObject();
      writer.writeNumberField("id", id);
      if (licenseId > 0) {
         writer.writeNumberField("license", licenseId);
      }
      writer.writeNumberField("width", model.getSize().getWidth());
      writer.writeNumberField("height", model.getSize().getHeight());
      writer.writeStringField("file_name", model.getFilename());
      writer.writeEndObject();
   }

   private void writeAnnotation(JsonGenerator writer, ObjectModel model, int id, int imageId, Map<String, Integer> categoryMap) throws IOException {
      writer.writeStartObject();
      writer.writeNumberField("id", id);

      // Resolve categories and category IDs
      var category = model.getName();
      var categoryId = categoryMap.get(category);
      if (categoryId == null) {
         categoryId = categoryMap.entrySet().size() + 1;
         categoryMap.put(category, categoryId);
      }
      writer.writeNumberField("category_id", categoryId);

      writer.writeNumberField("image_id", imageId);
      writer.writeNumberField("iscrowd", 0);

      BoundBox bb = model.getBoundBox();
      writer.writeArrayFieldStart("segmentation");
      double[] points = new double[]{bb.getXMin(), bb.getYMin(), bb.getXMax(), bb.getYMax()};
      if (model.getPolygon() != null) {
         points = model.getPolygon().stream().mapToDouble(Double::doubleValue).toArray();
      }
      writer.writeArray(points, 0, points.length);
      writer.writeEndArray();

      writer.writeNumberField("area", model.area());
      writer.writeObjectField("bbox", new double[]{bb.getX(), bb.getY(), bb.getWidth(), bb.getHeight()});

      writer.writeEndObject();
   }

   private void writeCategory(JsonGenerator writer, int id, String name) throws IOException {
      writer.writeStartObject();
      writer.writeNumberField("id", id);
      writer.writeStringField("name", name);
      writer.writeEndObject();
   }

   // COCO Json template
   private static class COCO {
      public Info info = new Info();
      public License license = new License();

      @JsonIgnore
      public final BooleanProperty usePathInXmlProperty = new SimpleBooleanProperty(true);
      public boolean isUsePathInXml() {
         return usePathInXmlProperty.get();
      }
      public void setUserPathInXml(boolean usePath) {
         this.usePathInXmlProperty.setValue(usePath);
      }

      @JsonIgnore
      public final BooleanProperty nameAsIdProperty = new SimpleBooleanProperty(true);
      public boolean isNameAsId() {
         return nameAsIdProperty.get();
      }
      public void setNameAsId(boolean nameAsId) {
         this.nameAsIdProperty.setValue(nameAsId);
      }

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

   // COCO Json info section
   @JsonInclude(JsonInclude.Include.NON_NULL)
   @JsonPropertyOrder({"year", "version", "description", "contributor", "url", "date_created"})
   private static class Info {
      @JsonIgnore
      public final IntegerProperty yearProperty = new SimpleIntegerProperty(Year.now().getValue());

      @JsonProperty("year")
      public int getYear() {
         return yearProperty.get();
      }
      public void setYear(int year) {
         this.yearProperty.setValue(year);
      }

      @JsonIgnore
      public final StringProperty versionProperty = new SimpleStringProperty();

      @JsonProperty("version")
      public String getVersion() {
         return versionProperty.get();
      }
      public void setVersion(String version) {
         this.versionProperty.setValue(version);
      }

      @JsonIgnore
      public final StringProperty descriptionProperty = new SimpleStringProperty();

      @JsonProperty("description")
      public String getDescription() {
         return descriptionProperty.get();
      }
      public void setDescription(String description) {
         this.descriptionProperty.setValue(description);
      }

      @JsonIgnore
      public final StringProperty contributorProperty = new SimpleStringProperty();

      @JsonProperty("contributor")
      public String getContributor() {
         return contributorProperty.get();
      }
      public void setContributor(String contributor) {
         this.contributorProperty.setValue(contributor);
      }

      @JsonIgnore
      public final StringProperty urlProperty = new SimpleStringProperty();

      @JsonProperty("url")
      public String getUrl() {
         return urlProperty.get();
      }
      public void setUrl(String url) {
         this.urlProperty.setValue(url);
      }

      @JsonIgnore
      public final ObjectProperty<LocalDate> dateCreatedProperty = new SimpleObjectProperty(LocalDate.now());

      @JsonProperty("date_created")
      public String getDateCreated() {
         return DateTimeFormatter.ISO_LOCAL_DATE.format(dateCreatedProperty.get());
      }

      public void setDateCreated(String date) {
         this.dateCreatedProperty.setValue(LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE));
      }
   }

   // COCO Json license section
   @JsonInclude(JsonInclude.Include.NON_NULL)
   @JsonPropertyOrder({"id", "name", "url"})
   private static class License {
      @JsonIgnore
      public final IntegerProperty idProperty = new SimpleIntegerProperty(1);

      @JsonProperty("id")
      public int getId() {
         return idProperty.get();
      }
      public void setId(int id) {
         this.idProperty.setValue(id);
      }

      @JsonIgnore
      public final StringProperty nameProperty = new SimpleStringProperty();

      @JsonProperty("name")
      public String getName() {
         return nameProperty.get();
      }
      public void setName(String name) {
         this.nameProperty.setValue(name);
      }

      @JsonIgnore
      public final StringProperty urlProperty = new SimpleStringProperty();

      @JsonProperty("url")
      public String getUrl() {
         return urlProperty.get();
      }
      public void setUrl(String url) {
         this.urlProperty.setValue(url);
      }

      @JsonIgnore
      public boolean isValid() {
         return getId() > 0 && StringUtils.isNotBlank(getName());
      }
   }
}
