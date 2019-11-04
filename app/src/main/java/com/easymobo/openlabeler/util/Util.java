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

package com.easymobo.openlabeler.util;

import com.easymobo.openlabeler.preference.Settings;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class Util
{
    public static void fireKeyPressedEvent(Node target, KeyCode code) {
        KeyEvent event = new KeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.CHAR_UNDEFINED, "", code, false, false, false, false);
        target.fireEvent(event);
        event.consume();
    }

    public static File getAnnotationFile(File media) {
        Path parent = Paths.get(media.getParent());
        if (StringUtils.isNotEmpty(Settings.getAnnotationDir())) {
            parent = Paths.get(parent.toString(), Settings.getAnnotationDir());
            parent = parent.normalize();
        }
        String path = parent + File.separator + FilenameUtils.getBaseName(media.getName()) + ".xml";
        return new File(path);
    }

    public static class ImageTableCell<S, T> extends TableCell<S, T>
    {
        private ImageView imageView;

        public ImageTableCell() {
            setAlignment(Pos.CENTER);
            imageView = new ImageView();
            imageView.setFitHeight(40);
            imageView.setFitWidth(40);
            imageView.setPreserveRatio(true);
            setGraphic(imageView);
        }

        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            imageView.setImage(Image.class.cast(item));
        }
    }

    public static void watchAndUpdate(WatchService watcher, String name, Function<Path, Void> update) {
        new Thread(() -> {
            try {
                WatchKey key;
                while ((key = watcher.take()) != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == OVERFLOW) {
                            continue;
                        }
                        update.apply((Path)event.context());
                    }
                    // reset the key
                    boolean valid = key.reset();
                    if (!valid) {
                        break;
                    }
                }
            }
            catch (Exception ex) {
                return;
            }
        }, name).start();
    }

    public static class EditableTableCell<S, T> extends TableCell<S, T>
    {
        protected TextField textField;
        protected ChangeListener<? super Boolean> changeListener;
        protected final StringConverter<T> converter;

        public EditableTableCell (StringConverter<T> converter) {
            this.converter = converter;
            changeListener = (observable, oldValue, newValue) -> {
                if (!newValue) {
                    commitEdit(converter.fromString(textField.getText()));
                }
            };
        }

        @Override
        public void startEdit() {
            if (!editableProperty().get()){
                return;
            }
            if (!isEmpty()) {
                super.startEdit();
                createTextField();
                setText(null);
                setGraphic(textField);
                Platform.runLater(() -> {
                    textField.requestFocus();
                    textField.selectAll();
                });
            }
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(converter.toString(getItem()));
            setGraphic(null);
        }

        @Override
        public void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);

            if (empty) {
                setText(null);
                setGraphic(null);
            }
            else {
                if (isEditing()) {
                    if (textField != null) {
                        textField.setText(getString());
                        textField.selectAll();
                    }
                    setText(null);
                    setGraphic(textField);
                }
                else {
                    setText(getString());
                    setGraphic(null);
                }
            }
        }

        protected void createTextField() {
            textField = new TextField(getString());
            textField.setMinWidth(this.getWidth() - this.getGraphicTextGap() * 2);
            textField.focusedProperty().addListener(changeListener);
            textField.setOnAction(evt -> commitEdit(converter.fromString(textField.getText())));

            textField.setOnKeyPressed(ke -> {
                switch (ke.getCode()) {
                    case ESCAPE:
                        textField.focusedProperty().removeListener(changeListener);
                        cancelEdit();
                        break;
                    case TAB:
                        commitEdit(converter.fromString(textField.getText()));
                        editNextCell(!ke.isShiftDown());
                        break;
                }
            });
        }

        protected String getString() {
            return getItem() == null ? "" : getItem().toString();
        }

        private void editNextCell(boolean forward) {
            List<TableColumn<S, ?>> columns = new ArrayList<>();
            for (TableColumn<S, ?> column : getTableView().getColumns()) {
                columns.addAll(getLeaves(column));
            }
            // There is no other column that supports editing.
            if (columns.size() < 2) {
                return;
            }
            int rowIndex = getTableRow().getIndex();
            int colIndex = columns.indexOf(getTableColumn());
            if (forward) {
                colIndex++;
                if (colIndex > columns.size() - 1) {
                    colIndex = 0;
                    rowIndex++;
                    if (rowIndex > getTableView().getItems().size() - 1) {
                        rowIndex = 0;
                    }
                }
            }
            else {
                 colIndex--;
                if (colIndex < 0) {
                    colIndex = columns.size() - 1;
                    rowIndex--;
                    if (rowIndex < 0) {
                        rowIndex = getTableView().getItems().size() - 1;
                    }
                }
            }
            getTableView().getSelectionModel().clearSelection(rowIndex, columns.get(colIndex));
            getTableView().edit(rowIndex, columns.get(colIndex));
        }

        private List<TableColumn<S, ?>> getLeaves(TableColumn<S, ?> root) {
            List<TableColumn<S, ?>> columns = new ArrayList<>();
            if (root.getColumns().isEmpty()) {
                // We only want the leaves that are editable.
                if (root.isEditable()) {
                    columns.add(root);
                }
                return columns;
            }
            else {
                for (TableColumn<S, ?> column : root.getColumns()) {
                    columns.addAll(getLeaves(column));
                }
                return columns;
            }
        }
    }

    public static class ColorTableCell<T> extends TableCell<T, Color> {
        private final ColorPicker colorPicker;

        public ColorTableCell(TableColumn<T, Color> column) {
            colorPicker = new ColorPicker();
            colorPicker.editableProperty().bind(column.editableProperty());
            colorPicker.disableProperty().bind(column.editableProperty().not());
            colorPicker.setOnShowing(event -> {
                final TableView<T> tableView = getTableView();
                tableView.getSelectionModel().select(getTableRow().getIndex());
                tableView.edit(tableView.getSelectionModel().getSelectedIndex(), column);
            });
            colorPicker.valueProperty().addListener((observable, oldValue, newValue) -> {
                if (isEditing()) {
                    commitEdit(newValue);
                }
            });
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(Color item, boolean empty) {
            super.updateItem(item, empty);

            setText(null);
            if (empty) {
                setGraphic(null);
            }
            else {
                colorPicker.setValue(item);
                setGraphic(this.colorPicker);
            }
        }
    }

    public static Alert createAlert(AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        return alert;
    }

    public static void showInformation(String title, String message) {
        createAlert(AlertType.INFORMATION, title, message).showAndWait();
    }

    public static Optional<ButtonType> showConfirmation(String title, String message) {
        return createAlert(AlertType.CONFIRMATION, title, message).showAndWait();
    }

    public static <T> T getTransform(Node node, Class<T> clz) {
        return node.getTransforms().stream()
                .filter(transform -> transform.getClass().equals(clz)).map(clz::cast).findFirst().get();
    }
}
