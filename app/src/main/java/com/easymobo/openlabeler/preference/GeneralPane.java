/*
 * Copyright (c) 2019. Kin-Hong Wong. All Rights Reserved.
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
 * ==============================================================================
 */

package com.easymobo.openlabeler.preference;

import com.easymobo.openlabeler.util.AppUtils;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.fxmisc.easybind.EasyBind;

import java.lang.invoke.MethodHandles;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GeneralPane extends VBox implements Category
{
   @FXML
   private CheckBox chkOpenLastMedia, chkSaveEveryChange, chkAutoSetName, chkAnimateOutline;
   @FXML
   private TextField textAnnotationsDir;
   @FXML
   private ColorPicker pickerObjectStrokeColor;
   @FXML
   private NameTablePane nameTablePane;

   private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

   private final BooleanProperty dirtyProperty = new SimpleBooleanProperty(false);
   private ResourceBundle bundle = ResourceBundle.getBundle("bundle");

   public GeneralPane() {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/preference/GeneralPane.fxml"), bundle);
      loader.setRoot(this);
      loader.setController(this);

      try {
         loader.load();
      }
      catch (Exception ex) {
         LOG.log(Level.SEVERE, "Unable to load FXML", ex);
      }

      // Bind Properties
      BooleanBinding changes[] = {
            chkOpenLastMedia.selectedProperty().isNotEqualTo(Settings.openLastMediaProperty),
            chkSaveEveryChange.selectedProperty().isNotEqualTo(Settings.saveEveryChangeProperty),
            textAnnotationsDir.textProperty().isNotEqualTo(Settings.annotationDirProperty),
            pickerObjectStrokeColor.valueProperty().isNotEqualTo(Settings.objectStrokeColorProperty),
            chkAutoSetName.selectedProperty().isNotEqualTo(Settings.autoSetNameProperty),
            chkAnimateOutline.selectedProperty().isNotEqualTo(Settings.animateOutlineProperty),
            new SimpleListProperty(nameTablePane.getItems()).isNotEqualTo(Settings.recentNamesProperty),
      };
      dirtyProperty.bind(EasyBind.combine(
            FXCollections.observableArrayList(changes), stream -> stream.reduce((a, b) -> a | b).orElse(false)));

      load();
   }

   public void onClearUsedNames(ActionEvent actionEvent) {
      Settings.recentNamesProperty.clear();
      AppUtils.showInformation(bundle.getString("label.alert"), bundle.getString("msg.usedNamesCleared"));
   }

   @Override
   public BooleanProperty dirtyProperty() {
      return dirtyProperty;
   }

   public boolean isDirty() {
      return dirtyProperty.get();
   }

   @Override
   public String getName() {
      return bundle.getString("label.general");
   }

   @Override
   public void load() {
      chkOpenLastMedia.setSelected(Settings.isOpenLastMedia());
      chkSaveEveryChange.setSelected(Settings.isSaveEveryChange());
      textAnnotationsDir.setText(Settings.getAnnotationDir());
      pickerObjectStrokeColor.setValue(Settings.getObjectStrokeColor());
      chkAutoSetName.setSelected(Settings.isAutoSetName());
      chkAnimateOutline.setSelected(Settings.isAnimateOutline());
      nameTablePane.setItems(Settings.recentNamesProperty.clone());
   }

   @Override
   public void save() {
      if (!isDirty()) {
         return;
      }
      Settings.setOpenLastMedia(chkOpenLastMedia.isSelected());
      Settings.setSaveEveryChange(chkSaveEveryChange.isSelected());
      Settings.setAnnotationDir(textAnnotationsDir.getText());
      Settings.setObjectStrokeColor(pickerObjectStrokeColor.getValue());
      Settings.setAutoSetName(chkAutoSetName.isSelected());
      Settings.setAnimateOutline(chkAnimateOutline.isSelected());
      Settings.recentNamesProperty.clear();
      Settings.recentNamesProperty.addAll(nameTablePane.getItems());
   }
}
