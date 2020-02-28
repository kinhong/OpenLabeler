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

package com.easymobo.openlabeler.ui;

import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.util.ResourceBundle;

import static org.kordamp.ikonli.materialdesign.MaterialDesign.MDI_FOLDER_OUTLINE;

public class InputFileChooser extends HBox
{
    private TextField txt = new TextField();
    private Button btn = new Button(null, FontIcon.of(MDI_FOLDER_OUTLINE, 18));
    private ResourceBundle bundle = ResourceBundle.getBundle("bundle");
    ;
    private boolean file;

    public InputFileChooser() {
        getChildren().addAll(txt, btn);
        setHgrow(txt, Priority.ALWAYS);
        btn.getStyleClass().add("flat");
        btn.setOnAction(event -> onOpen(event));
    }

    public boolean isFile() {
        return file;
    }

    public void setFile(boolean file) {
        this.file = file;
    }

    private void onOpen(ActionEvent event) {
        File fileOrDir;
        if (file) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(bundle.getString("menu.chooseDir"));
            fileOrDir = chooser.showOpenDialog(((Node) event.getSource()).getScene().getWindow());
        }
        else {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(bundle.getString("menu.chooseDir"));
            fileOrDir = chooser.showDialog(((Node) event.getSource()).getScene().getWindow());
        }
        if (fileOrDir != null) {
            setText(fileOrDir.getAbsolutePath());
        }
    }

    public final StringProperty textProperty() {
        return txt.textProperty();
    }

    public void setText(String value) {
        txt.setText(value);
    }

    public String getText() {
        return txt.getText();
    }
}
