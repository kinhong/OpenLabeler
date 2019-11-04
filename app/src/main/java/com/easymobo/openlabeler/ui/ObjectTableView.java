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

import com.easymobo.openlabeler.preference.NameColor;
import com.easymobo.openlabeler.preference.Settings;
import com.easymobo.openlabeler.util.Util.ImageTableCell;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.CheckBox;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.image.Image;
import org.apache.commons.collections4.IteratorUtils;
import org.fxmisc.easybind.EasyBind;

import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ObjectTableView extends TableView<ObjectTag>
{
    @FXML
    private TableColumn<ObjectTag, Boolean> visibleColumn;
    @FXML
    private TableColumn<ObjectTag, Image> thumbColumn;
    @FXML
    private TableColumn<ObjectTag, String> nameColumn;
    @FXML
    private TableColumn<ObjectTag, String> coordsColumn;
    @FXML
    private CheckBox showAllCheckBox;

    private static final Logger LOG = Logger.getLogger(ObjectTableView.class.getCanonicalName());

    private ResourceBundle bundle;
    private ObservableValue<Long> visibleCount;

    public ObjectTableView() {
        super(null);
        bundle = ResourceBundle.getBundle("bundle");
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ObjectTableView.fxml"), bundle);
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to load FXML", ex);
        }

        getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        showAllCheckBox.setAllowIndeterminate(true);

        visibleColumn.setCellFactory(CheckBoxTableCell.forTableColumn(visibleColumn));
        visibleColumn.setCellValueFactory(cell -> cell.getValue().visibleProperty());

        thumbColumn.setCellFactory(param -> new ImageTableCell());
        thumbColumn.setCellValueFactory(cell -> cell.getValue().thumbProperty());

        List<String> names = IteratorUtils.toList(Settings.recentNamesProperty.stream().map(NameColor::getName).iterator());
        nameColumn.setCellFactory(ComboBoxTableCell.forTableColumn(FXCollections.observableList(names)));
        nameColumn.setCellValueFactory(cell -> cell.getValue().nameProperty());

        itemsProperty().addListener((observable, oldValue, newValue) -> {
            ObservableList<ObservableValue<Boolean>> visibles = EasyBind.map(newValue, ObjectTag::visibleProperty);
            visibleCount = EasyBind.combine(visibles, stream -> stream.filter(b -> b).count());
            visibleCount.addListener((obs, oldCount, newCount) -> {
                if (newCount == 0) {
                    showAllCheckBox.setIndeterminate(false);
                    showAllCheckBox.setSelected(false);
                }
                else if (newCount < getItems().size()) {
                    showAllCheckBox.setIndeterminate(true);
                }
                else {
                    showAllCheckBox.setIndeterminate(false);
                    showAllCheckBox.setSelected(true);
                }
            });
        });
    }

    public void onShowAll(ActionEvent actionEvent) {
        boolean select = showAllCheckBox.isIndeterminate() || showAllCheckBox.isSelected();
        getItems().stream().forEach(item -> item.setVisible(select));
    }
}
