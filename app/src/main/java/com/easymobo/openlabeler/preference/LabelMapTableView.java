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

import com.easymobo.openlabeler.util.Util.EditableTableCell;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.converter.DefaultStringConverter;
import javafx.util.converter.IntegerStringConverter;

import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LabelMapTableView extends TableView<LabelMapItem>
{
    @FXML
    private TableColumn<LabelMapItem, Integer> colId;
    @FXML
    private TableColumn<LabelMapItem, String> colName;
    @FXML
    private TableColumn<LabelMapItem, String> colDisplayName;

    private static final Logger LOG = Logger.getLogger(LabelMapTableView.class.getCanonicalName());

    private ResourceBundle bundle;

    public LabelMapTableView() {
        bundle = ResourceBundle.getBundle("bundle");
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LabelMapTableView.fxml"), bundle);
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to load FXML", ex);
        }

        ObservableList<LabelMapItem> items = FXCollections.observableArrayList(LabelMapItem.extractor());
        setItems(items);
        getSelectionModel().setCellSelectionEnabled(true);

        colId.setCellFactory(param -> new EditableTableCell(new IntegerStringConverter() {
            @Override public Integer fromString(String value) {
                try {
                    return super.fromString(value);
                }
                catch (Exception ex) {
                    return null;
                }
            }
        }));
        colId.setCellValueFactory(new PropertyValueFactory("id"));
        colId.setOnEditCommit(event -> {
            int row = event.getTablePosition().getRow();
            LabelMapItem item = event.getTableView().getItems().get(row);
            int id = event.getNewValue() == null ? item.getId() : event.getNewValue();
            item.setId(id);
        });

        colName.setCellFactory(param -> new EditableTableCell(new DefaultStringConverter()));
        colName.setCellValueFactory(new PropertyValueFactory("name"));
        colName.setOnEditCommit(event -> {
            int row = event.getTablePosition().getRow();
            LabelMapItem item = event.getTableView().getItems().get(row);
            item.setName(event.getNewValue());
        });

        colDisplayName.setCellFactory(param -> new EditableTableCell(new DefaultStringConverter()));
        colDisplayName.setCellValueFactory(new PropertyValueFactory("displayName"));
        colDisplayName.setOnEditCommit(event -> {
            int row = event.getTablePosition().getRow();
            LabelMapItem item = event.getTableView().getItems().get(row);
            item.setDisplayName(event.getNewValue());
        });
    }
}
