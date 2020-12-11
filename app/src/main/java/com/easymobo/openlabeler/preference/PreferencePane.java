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

package com.easymobo.openlabeler.preference;

import com.easymobo.openlabeler.util.AppUtils;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.fxmisc.easybind.EasyBind;

import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.easymobo.openlabeler.OpenLabeler.APP_ICON;

public class PreferencePane extends DialogPane
{
    private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

    @FXML
    private TabPane tabs;

    private ResourceBundle bundle = ResourceBundle.getBundle("bundle");

    public PreferencePane() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/preference/PreferencePane.fxml"), bundle);
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to load FXML", ex);
        }
        getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CLOSE);

        bindProperties();
    }

    public void showAndWait() {
        Dialog dialog = new Dialog();
        dialog.setDialogPane(this);
        dialog.setTitle(bundle.getString("label.preferences"));
        dialog.setResizable(true);

        Stage stage = (Stage)getScene().getWindow();
        stage.getIcons().add(new Image(getClass().getClassLoader().getResourceAsStream(APP_ICON)));

        lookupButton(ButtonType.CLOSE).addEventFilter(
            ActionEvent.ACTION,
            event -> {
                if (!lookupButton(ButtonType.APPLY).isDisabled()) {
                    var res = AppUtils.showConfirmation(bundle.getString("label.alert"), bundle.getString("msg.confirmClose"));
                    if (res.get() != ButtonType.OK) {
                        event.consume();
                    }
                }
            }
        );

        Optional<ButtonType> result = dialog.showAndWait();
        result.ifPresent(res -> {
            if (res.equals(ButtonType.APPLY)) {
                getCategories().forEach(c -> c.save());
            }
        });
    }

    private void bindProperties() {
        addTab(new GeneralPane());
        addTab(new InferencePane());
        addTab(new TrainingPane());

        ObservableList<ObservableValue<Boolean>> dirties = EasyBind.map(getCategories(), c -> c.dirtyProperty());
        lookupButton(ButtonType.APPLY).disableProperty().bind(EasyBind.combine(dirties, stream -> stream.allMatch(a -> !a)));

        tabs.getSelectionModel().select(Settings.getPrefTabIndex());
        Settings.prefTabIndexProperty.bind(tabs.getSelectionModel().selectedIndexProperty());
    }

    private ObservableList<Category> getCategories() {
        return EasyBind.map(tabs.getTabs(), t -> Category.class.cast(t.getContent()));
    }

    private void addTab(Category category) {
        Tab tab = new Tab(category.getName());
        tab.setContent((Node)category);
        tabs.getTabs().add(tab);
    }
}
