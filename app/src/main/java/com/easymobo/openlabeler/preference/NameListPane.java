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

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.layout.BorderPane;

import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NameListPane extends BorderPane
{
    @FXML
    private ListView listName;
    @FXML
    private Button btnAddRow, btnRemoveRow;

    private static final Logger LOG = Logger.getLogger(NameListPane.class.getCanonicalName());

    private ResourceBundle bundle;

    public NameListPane() {
        bundle = ResourceBundle.getBundle("bundle");
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/NameListPane.fxml"), bundle);
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to load FXML", ex);
        }

        listName.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listName.setCellFactory(TextFieldListCell.forListView());
        listName.setOnEditCommit((EventHandler<ListView.EditEvent<String>>) t -> {
            listName.getItems().set(t.getIndex(), t.getNewValue());
            listName.getSelectionModel().clearAndSelect(t.getIndex());
        });
    }

    public ObservableList<String> getItems() {
        return listName.getItems();
    }

    public void onAddItem(ActionEvent actionEvent) {
        listName.getItems().add("");
        // JavaFX bug: the cells are not being updated before calling edit
        listName.scrollTo(listName.getItems().size() - 1);
        listName.layout();
        listName.edit(listName.getItems().size() - 1);
    }

    public void onRemoveItem(ActionEvent actionEvent) {
        listName.getItems().removeAll(listName.getSelectionModel().getSelectedItems());
    }
}
