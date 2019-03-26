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

import com.easymobo.openlabeler.util.Util;
import com.easymobo.openlabeler.util.Util.ImageTableCell;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.MenuItem;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.apache.commons.lang3.SystemUtils;

import java.awt.*;
import java.io.File;
import java.net.MalformedURLException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MediaTableView extends TableView<MediaTableView.MediaFile>
{
    @FXML
    private TableColumn<MediaFile, Boolean> colAnnotated;
    @FXML
    private TableColumn<MediaFile, Image> colThumb;
    @FXML
    private TableColumn<MediaFile, String> colName;
    @FXML
    private CheckBox chkShowAll;

    private static final Logger LOG = Logger.getLogger(MediaTableView.class.getCanonicalName());

    private ResourceBundle bundle;
    private FilteredList<MediaFile> filtered;

    public MediaTableView() {
        bundle = ResourceBundle.getBundle("bundle");
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MediaTableView.fxml"), bundle);
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to load FXML", ex);
        }

        colAnnotated.setCellFactory(param -> new AnnotatedTableCell());
        colAnnotated.setCellValueFactory(cell -> cell.getValue().isAnnotatedProperty());

        colThumb.setCellFactory(param -> new ImageTableCell());
        colThumb.setCellValueFactory(cell -> cell.getValue().thumbProperty());

        colName.setCellFactory(param -> new NameTableCell());
        colName.setCellValueFactory(cell -> cell.getValue().nameProperty());

        filtered = new FilteredList(getItems());
        setItems(filtered);

        getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        getSelectionModel().selectedItemProperty().addListener((observable, oldFile, newFile) -> {
            if (oldFile != null && oldFile.isAnnotated() && !chkShowAll.isSelected()) {
                Platform.runLater(() -> {
                    // Force a refresh
                    filtered.setPredicate(null);
                    filtered.setPredicate(mediaFile -> !mediaFile.isAnnotated());
                });
            }
        });

        setRowFactory(tableView -> {
            final TableRow<MediaFile> row = new TableRow();
            row.setOnContextMenuRequested(event -> {
                if (row.isEmpty()) {
                    return;
                }
                ContextMenu menu = row.getContextMenu();
                menu.getItems().clear();

                MediaFile file = tableView.getSelectionModel().getSelectedItem();
                String media = bundle.getString("menu.showMediaInFileBrowser");
                String annotation = bundle.getString("menu.showAnnotationInFileBrowser");
                if (SystemUtils.IS_OS_MAC) {
                    media = bundle.getString("menu.revealMediaInFinder");
                    annotation = bundle.getString("menu.revealAnnotationInFinder");
                }
                else if (SystemUtils.IS_OS_WINDOWS) {
                    media = bundle.getString("menu.showMediaInExplorer");
                    annotation = bundle.getString("menu.showAnnotationInExplorer");
                }
                MenuItem item = new MenuItem(media);
                item.setOnAction(ev -> Desktop.getDesktop().browseFileDirectory(file));
                menu.getItems().add(item);
                if (file.isAnnotated()) {
                    item = new MenuItem(annotation);
                    item.setOnAction(ev -> Desktop.getDesktop().browseFileDirectory(Util.getAnnotationFile(file)));
                    menu.getItems().add(item);
                }
            });

            // only display context menu for non-null items:
            final ContextMenu rowMenu = new ContextMenu();
            row.contextMenuProperty().bind(
                    Bindings.when(Bindings.isNotNull(row.itemProperty())).then(rowMenu).otherwise((ContextMenu) null));
            return row;
        });
    }

    public ObservableList<MediaFile> getSource() {
        return (ObservableList<MediaFile>)filtered.getSource();
    }

    public void onShowAll(ActionEvent actionEvent) {
        if (chkShowAll.isSelected()) {
            filtered.setPredicate(null);
        }
        else {
            filtered.setPredicate(mediaFile -> !mediaFile.isAnnotated());
        }
        int index = getSelectionModel().getSelectedIndex() < 0 ? 0 : getSelectionModel().getSelectedIndex();
        getSelectionModel().select(index);
        scrollTo(index);
    }

    private class AnnotatedTableCell extends TableCell<MediaFile, Boolean>
    {
        private Rectangle rect = new Rectangle();

        public AnnotatedTableCell() {
            setPadding(Insets.EMPTY);
            setAlignment(Pos.CENTER);
            rect.setX(0);
            rect.setY(0);
            rect.setWidth(10);
            rect.setHeight(10);
            rect.setFill(Color.TRANSPARENT);
            setGraphic(rect);
        }

        @Override
        protected void updateItem(Boolean annotated, boolean empty) {
            super.updateItem(annotated, empty);
            if (empty) {
                rect.setFill(Color.TRANSPARENT);
            }
            else {
                rect.setFill(annotated ? Color.LIGHTGREEN : Color.TRANSPARENT);
            }
        }
    };

    private class NameTableCell extends TableCell<MediaFile, String>
    {
        @Override
        protected void updateItem(String name, boolean empty) {
            super.updateItem(name, empty);
            if (empty) {
                setText(null);
                setTooltip(null);
            }
            else {
                setText(name);
                MediaFile file = MediaFile.class.cast(getTableRow().getItem());
                if (file != null) {
                    setTooltip(new Tooltip(file.getAbsolutePath()));
                }
            }
        }
    }

    public static class MediaFile extends File
    {
        public MediaFile(File file) {
            super(file.getAbsolutePath());
        }

        private BooleanProperty isAnnotatedProperty = new SimpleBooleanProperty(false);
        public ReadOnlyBooleanProperty isAnnotatedProperty() {
            return isAnnotatedProperty;
        }
        public boolean isAnnotated() {
            return isAnnotatedProperty.get();
        }
        public MediaFile refresh() {
            isAnnotatedProperty.set(Util.getAnnotationFile(this).exists());
            return this;
        }

        private ObjectProperty<Image> thumbProperty;
        public ReadOnlyObjectProperty<Image> thumbProperty() {
            if (thumbProperty == null) {
                Image image = null;
                try {
                    image = new Image(toURI().toURL().toExternalForm(), 120, 120, true, false, true);
                }
                catch (MalformedURLException ex) {
                    LOG.log(Level.SEVERE, "Unable to load image", ex);
                }

                thumbProperty = new SimpleObjectProperty(image);
            }
            return thumbProperty;
        }

        private StringProperty nameProperty;
        public ReadOnlyStringProperty nameProperty() {
            if (nameProperty == null) {
                nameProperty = new SimpleStringProperty(getName());
            }
            return nameProperty;
        }
    }
}
