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
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import object_detection.protos.StringIntLabelMapOuterClass.StringIntLabelMapItem;

import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LabelMapPane extends BorderPane
{
    @FXML
    private LabelMapTableView tvLabelMap;
    @FXML
    private Button btnAddRow, btnRemoveRow;

    private static final Logger LOG = Logger.getLogger(LabelMapPane.class.getCanonicalName());

    private ResourceBundle bundle = ResourceBundle.getBundle("bundle");

    public LabelMapPane() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/preference/LabelMapPane.fxml"), bundle);
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to load FXML", ex);
        }
    }

    public ObservableList<LabelMapItem> getItems() {
        return tvLabelMap.getItems();
    }

    public void onAddRow(ActionEvent actionEvent) {
        tvLabelMap.getItems().add(new LabelMapItem(StringIntLabelMapItem.newBuilder().build()));
    }

    public void onRemoveRow(ActionEvent actionEvent) {
        tvLabelMap.getItems().remove(tvLabelMap.getSelectionModel().getSelectedItem());
    }
}
