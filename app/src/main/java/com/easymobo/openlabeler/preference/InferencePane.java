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

import com.easymobo.openlabeler.ui.InputFileChooser;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.controlsfx.tools.Borders;
import org.fxmisc.easybind.EasyBind;

import java.lang.invoke.MethodHandles;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InferencePane extends VBox implements Category
{
    @FXML
    private GridPane gpTensorFlow;
    @FXML
    private CheckBox chkUseInference;
    @FXML
    private ColorPicker pickerHintStrokeColor;
    @FXML
    private InputFileChooser txtTFLabelMapFile, txtTFSavedModelDir;

    private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

    private final BooleanProperty dirtyProperty =  new SimpleBooleanProperty(false);
    private ResourceBundle bundle = ResourceBundle.getBundle("bundle");

    public InferencePane() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/preference/InferencePane.fxml"), bundle);
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
            getChildren().addAll(
                    Borders.wrap(gpTensorFlow).lineBorder().title(bundle.getString("menu.tensorFlow")).buildAll()
            );
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to load FXML", ex);
        }

        // Bind Properties
        BooleanBinding changes[] = {
                chkUseInference.selectedProperty().isNotEqualTo(Settings.useInferenceProperty),
                pickerHintStrokeColor.valueProperty().isNotEqualTo(Settings.hintStrokeColorProperty),
                txtTFLabelMapFile.textProperty().isNotEqualTo(Settings.tfLabelMapFileProperty),
                txtTFSavedModelDir.textProperty().isNotEqualTo(Settings.tfSavedModelDirProperty),
        };
        dirtyProperty.bind(EasyBind.combine(
                FXCollections.observableArrayList(changes), stream -> stream.reduce((a, b) -> a | b).orElse(false)));

        load();
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
        return bundle.getString("menu.inference");
    }

    @Override
    public void load() {
        chkUseInference.setSelected(Settings.isUseInference());
        pickerHintStrokeColor.setValue(Settings.getHintStrokeColor());
        txtTFLabelMapFile.setText(Settings.getTFLabelMapFile());
        txtTFSavedModelDir.setText(Settings.getTFSavedModelDir());
    }

    @Override
    public void save() {
        if (!isDirty()) {
            return;
        }
        Settings.setUseInference(chkUseInference.isSelected());
        Settings.setHintStrokeColor(pickerHintStrokeColor.getValue());
        Settings.setTFLabelMapFile(txtTFLabelMapFile.getText());
        Settings.setTFSavedModelDir(txtTFSavedModelDir.getText());
    }
}
