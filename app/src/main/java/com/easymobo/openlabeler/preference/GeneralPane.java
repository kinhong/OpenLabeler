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

import com.easymobo.openlabeler.util.Util;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.fxmisc.easybind.EasyBind;

import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GeneralPane extends GridPane implements Category
{
    @FXML
    private CheckBox chkSaveEveryChange, chkAutoSetName;
    @FXML
    private TextField textAnnotationsDir;
    @FXML
    private ColorPicker pickerObjectStrokeColor;
    @FXML
    private Button btnClearUsedNames;

    private static final Logger LOG = Logger.getLogger(GeneralPane.class.getCanonicalName());

    private final BooleanProperty dirtyProperty =  new SimpleBooleanProperty(false);
    private String name;
    private ResourceBundle bundle;

    public GeneralPane(String name, ResourceBundle bundle) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/GeneralPane.fxml"), bundle);
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to load FXML", ex);
        }
        this.name = name;
        this.bundle = bundle;

        // Bind Properties
        BooleanBinding changes[] = {
                chkSaveEveryChange.selectedProperty().isNotEqualTo(Settings.saveEveryChangeProperty),
                textAnnotationsDir.textProperty().isNotEqualTo(Settings.annotationDirProperty),
                chkAutoSetName.selectedProperty().isNotEqualTo(Settings.autoSetNameProperty),
                pickerObjectStrokeColor.valueProperty().isNotEqualTo(Settings.objectStrokeColorProperty),
        };
        dirtyProperty.bind(EasyBind.combine(
                FXCollections.observableArrayList(changes), stream -> stream.reduce((a, b) -> a | b).orElse(false)));

        load();
    }

    public void onClearUsedNames(ActionEvent actionEvent) {
        Settings.recentNames.clear();
        Util.showInformation(bundle.getString("menu.alert"), bundle.getString("msg.usedNamesCleared"));
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
        return name;
    }

    @Override
    public void load() {
        chkSaveEveryChange.setSelected(Settings.isSaveEveryChange());
        textAnnotationsDir.setText(Settings.getAnnotationDir());
        chkAutoSetName.setSelected(Settings.getAutoSetName());
        pickerObjectStrokeColor.setValue(Settings.getObjectStrokeColor());
    }

    @Override
    public void save() {
        if (!isDirty()) {
            return;
        }
        Settings.setSaveEveryChange(chkSaveEveryChange.isSelected());
        Settings.setAnnotationDir(textAnnotationsDir.getText());
        Settings.setAutoSetName(chkAutoSetName.isSelected());
        Settings.setObjectStrokeColor(pickerObjectStrokeColor.getValue());
    }
}
