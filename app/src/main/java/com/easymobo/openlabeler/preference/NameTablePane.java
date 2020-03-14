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

import com.easymobo.openlabeler.util.AppUtils.ColorTableCell;
import com.easymobo.openlabeler.util.AppUtils.EditableTableCell;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.util.converter.DefaultStringConverter;

import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NameTablePane extends BorderPane
{
    @FXML
    private TableView tableNameColor;
    @FXML
    private TableColumn<NameColor, String> colName;
    @FXML
    private TableColumn<NameColor, Color> colColor;
    @FXML
    private Button btnAddRow, btnRemoveRow;

    private static final Logger LOG = Logger.getLogger(NameTablePane.class.getCanonicalName());

    private ResourceBundle bundle = ResourceBundle.getBundle("bundle");

    public NameTablePane() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/preference/NameTablePane.fxml"), bundle);
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to load FXML", ex);
        }

        ObservableList<NameColor> items = FXCollections.observableArrayList(NameColor.extractor());
        tableNameColor.setItems(items);
        tableNameColor.getSelectionModel().setCellSelectionEnabled(true);

        colName.setCellValueFactory(new PropertyValueFactory("name"));
        colName.setCellFactory(param -> new EditableTableCell(new DefaultStringConverter()));
        colName.setOnEditCommit(event -> {
            int row = event.getTablePosition().getRow();
            NameColor item = event.getTableView().getItems().get(row);
            item.setName(event.getNewValue());
        });

        colColor.setCellValueFactory(new PropertyValueFactory("color"));
        colColor.setCellFactory(param -> new ColorTableCell(colColor));
        colColor.setOnEditCommit(event -> {
            int row = event.getTablePosition().getRow();
            NameColor item = event.getTableView().getItems().get(row);
            item.setColor(event.getNewValue());
        });
    }

    public void setItems(ObservableList<NameColor> items) {
        tableNameColor.getItems().addAll(items);
    }

    public ObservableList<NameColor> getItems() {
        return tableNameColor.getItems();
    }

    public void onAddItem(ActionEvent actionEvent) {
        tableNameColor.getItems().add(new NameColor());
        // JavaFX bug: the cells are not being updated before calling edit
        tableNameColor.scrollTo(tableNameColor.getItems().size() - 1);
        tableNameColor.layout();
        tableNameColor.edit(tableNameColor.getItems().size() - 1, colName);
    }

    public void onRemoveItem(ActionEvent actionEvent) {
        tableNameColor.getItems().removeAll(tableNameColor.getSelectionModel().getSelectedItems());
    }
}
