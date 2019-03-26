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
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import org.fxmisc.easybind.EasyBind;

import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PreferencePane extends DialogPane
{
    @FXML
    private ListView<String> categoryList;
    @FXML
    private ScrollPane scroller;

    private static final Logger LOG = Logger.getLogger(PreferencePane.class.getCanonicalName());

    private ResourceBundle bundle;
    private ObservableList<Category> categories = FXCollections.observableArrayList();

    public PreferencePane(ResourceBundle bundle) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PreferencePane.fxml"), bundle);
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to load FXML", ex);
        }
        this.bundle = bundle;
        getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CLOSE);

        bindProperties();
    }

    public void showAndWait() {
        Dialog dialog = new Dialog();
        dialog.setDialogPane(this);
        dialog.setTitle(bundle.getString("menu.titlePrefs"));
        dialog.setResizable(true);

        lookupButton(ButtonType.CLOSE).addEventFilter(
            ActionEvent.ACTION,
            event -> {
                if (!lookupButton(ButtonType.APPLY).isDisabled()) {
                    var res = Util.showConfirmation(bundle.getString("menu.alert"), bundle.getString("msg.confirmClose"));
                    if (res.get() != ButtonType.OK) {
                        event.consume();
                    }
                }
            }
        );

        Optional<ButtonType> result = dialog.showAndWait();
        result.ifPresent(res -> {
            if (res.equals(ButtonType.APPLY)) {
                categories.forEach(c -> c.save());
            }
        });
    }

    private void bindProperties() {
        categories.add(new GeneralPane(bundle.getString("menu.general"), bundle));
        categories.add(new InferencePane(bundle.getString("menu.inference"), bundle));
        categories.add(new TrainingPane(bundle.getString("menu.train"), bundle));

        categoryList.getItems().addAll(EasyBind.map(categories, Category::getName));

        categoryList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            Category selected = categories.stream().filter(p -> p.getName().equals(newValue)).findFirst().get();
            scroller.setContent((Node) selected);
        });

        ObservableList<ObservableValue<Boolean>> dirties = EasyBind.map(categories, c -> c.dirtyProperty());
        lookupButton(ButtonType.APPLY).disableProperty().bind(EasyBind.combine(dirties, stream -> stream.allMatch(a -> !a)));

        categoryList.getSelectionModel().select(0);
    }
}
