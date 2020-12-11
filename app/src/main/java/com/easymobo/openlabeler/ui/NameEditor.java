/*
 * Copyright (c) 2020. Kin-Hong Wong. All Rights Reserved.
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

package com.easymobo.openlabeler.ui;

import com.easymobo.openlabeler.preference.NameColor;
import com.easymobo.openlabeler.preference.Settings;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.apache.commons.collections4.IteratorUtils;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NameEditor extends VBox
{
    @FXML
    private TextField textField;
    @FXML
    private Button button;
    @FXML
    private ListView<String> list;

    private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

    private ResourceBundle bundle = ResourceBundle.getBundle("bundle");

    public NameEditor() {
        this("");
    }

    public NameEditor(String label) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/NameEditor.fxml"), bundle);
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to load FXML", ex);
        }

        button.setOnAction(e -> textField.setText(""));

        List<String> labels = IteratorUtils.toList(Settings.recentNamesProperty.stream().map(NameColor::getName).iterator());
        FilteredList<String> filtered = new FilteredList<>(FXCollections.observableArrayList(labels), s -> true);
        textField.textProperty().addListener(obs -> {
            String filter = textField.getText();
            if (filter == null || filter.length() == 0) {
                filtered.setPredicate(s -> true);
            }
            else {
                filtered.setPredicate(s -> s.startsWith(filter));
            }
        });

        list.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);

                setOnMouseClicked(value -> {
                    if (!empty && value.getClickCount() == 2) {
                        textField.setText(item);
                        ((Stage) getScene().getWindow()).close();
                    }
                });
            }
        });

        list.setItems(filtered);
        textField.setText(label);
        textField.selectAll();
    }

    public static String getLastLabel(ResourceBundle bundle) {
        if (Settings.recentNamesProperty.size() > 0) {
           return Settings.recentNamesProperty.get(0).getName();
        }
        return "";
    }

    public String showPopup(double screenX, double screenY, Window window) {
        Scene scene = new Scene(this);
        Stage popupStage = new Stage(StageStyle.UNDECORATED);
        String label = textField.getText();
        popupStage.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            switch (event.getCode()) {
                case ESCAPE: {
                    textField.setText(label); // discard change
                    popupStage.close();
                    break;
                }
                case ENTER: {
                    int index = list.getSelectionModel().getSelectedIndex();
                    if (index >= 0) {
                        textField.setText(list.getSelectionModel().getSelectedItem());
                    }
                    if (textField.getText().trim().isEmpty()) {
                        textField.setText(label);
                    }
                    popupStage.close();
                    break;
                }
                case UP: {
                    list.requestFocus();
                    break;
                }
                case DOWN: {
                    list.requestFocus();
                    if (list.getSelectionModel().getSelectedIndex() < 0) {
                        list.getSelectionModel().select(0);
                    }
                    break;
                }
                // JavaFX bug - ListView#selectionModel#select does not scroll into view
                /*
                case UP: {
                    int index = list.getSelectionModel().getSelectedIndex();
                    index = index < 0 ? 0 : (index == 0 ? list.getItems().size() - 1 : index - 1);
                    list.getSelectionModel().select(index);
                    event.consume();
                    break;
                }
                case DOWN: {
                    int index = list.getSelectionModel().getSelectedIndex();
                    index = index < 0 ? 0 : (index == list.getItems().size() - 1 ? 0 : index + 1);
                    list.getSelectionModel().select(index);
                    event.consume();
                    break;
                }
                */
            }
        });
        popupStage.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                if (textField.getText().trim().isEmpty()) {
                    textField.setText(label);
                }
                popupStage.hide();
            }
        });
        popupStage.initOwner(window);
        popupStage.initModality(Modality.WINDOW_MODAL);
        popupStage.setScene(scene);

        // Show the stage close to the mouse pointer
        popupStage.setX(screenX + 10);
        popupStage.setY(screenY + 10);

        popupStage.showAndWait();
        return textField.getText();
    }
}
