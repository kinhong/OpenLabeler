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

import com.easymobo.openlabeler.preference.Settings;
import com.easymobo.openlabeler.ui.MediaTableView.MediaFile;
import com.easymobo.openlabeler.util.Util;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.control.TableView.TableViewSelectionModel;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.file.StandardWatchEventKinds.*;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class MediaPane extends BorderPane implements AutoCloseable
{
    @FXML
    private MediaTableView tvMedia;
    @FXML
    private Label fileStats;

    private static final Logger LOG = Logger.getLogger(MediaPane.class.getCanonicalName());

    private ResourceBundle bundle;

    // Monitors stats on # of files annotated in a directory
    private WatchService watcher;
    private WatchKey imageDirWatchKey, annotationDirWatchKey;

    public MediaPane() {
        bundle = ResourceBundle.getBundle("bundle");
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MediaPane.fxml"), bundle);
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to load FXML", ex);
        }

        ListProperty<File> items = new SimpleListProperty(tvMedia.getSource());
        items.sizeProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.intValue() <= 0) {
                fileStats.setText("");
            }
        });

        try {
            watcher = FileSystems.getDefault().newWatchService();
        }
        catch (IOException ex) {
            LOG.log(Level.SEVERE, "Unable to create new file watcher", ex);
        }
    }

    private ReadOnlyIntegerProperty mediaSizeProperty;
    public ReadOnlyIntegerProperty sizeProperty() {
        if (mediaSizeProperty == null) {
            mediaSizeProperty = new SimpleListProperty(tvMedia.getSource()).sizeProperty();
        }
        return mediaSizeProperty;
    }

    public TableViewSelectionModel<MediaFile> getSelectionModel() {
        return tvMedia.getSelectionModel();
    }

    public void openFileOrDir(File file) {
        File[] files = new File[] { file };
        if (file.isDirectory()) {
            files = file.listFiles((dir, name) -> {
                name = name.toLowerCase();
                return name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".gif");
            });
            Arrays.sort(files);
            if (files.length <= 0) {
                Util.showInformation(bundle.getString("menu.alert"), bundle.getString("msg.noMediaFiles"));
                return;
            }
        }

        tvMedia.getSource().clear();
        Arrays.stream(files).forEach(f -> tvMedia.getSource().add(new MediaFile(f)));
        tvMedia.getSelectionModel().select(0);
        Settings.recentFiles.add(file.getAbsolutePath());
        watch(file);
    }

    public void onPrevMediaFile(ActionEvent actionEvent) {
        // JavaFX bug - TableView#selectionModel#select does not scroll into view
        Util.fireKeyPressedEvent(tvMedia, KeyCode.UP);
    }

    public void onNextMediaFile(ActionEvent actionEvent) {
        // JavaFX bug - TableView#selectionModel#select does not scroll into view
        Util.fireKeyPressedEvent(tvMedia, KeyCode.DOWN);
    }

    public void clear() {
        tvMedia.getSource().clear();
    }

    @Override
    public void close() {
        Optional.ofNullable(imageDirWatchKey).ifPresent(key -> key.cancel());
        Optional.ofNullable(annotationDirWatchKey).ifPresent(key -> key.cancel());
        IOUtils.closeQuietly(watcher);
    }

    /**
     * Spawns a thread to gather stats on # of files annotated in a media directory
     *
     * @param file the medial file or directory
     */
    public void watch(File file) {
        try {
            if (file.isDirectory()) {
                // Watch changes in media directory
                Path inputPath = Paths.get(file.getAbsolutePath());
                if (imageDirWatchKey != null && !imageDirWatchKey.watchable().equals(inputPath)) {
                    imageDirWatchKey.cancel();
                }
                imageDirWatchKey = inputPath.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                new Thread(() -> {
                    try {
                        WatchKey key;
                        while ((key = watcher.take()) != null) {
                            for (WatchEvent<?> event : key.pollEvents()) {
                                if (event.kind() == OVERFLOW) {
                                    continue;
                                }
                                if (event.kind() == ENTRY_CREATE) {
                                    Path path = (Path)event.context();
                                    Platform.runLater(() -> tvMedia.getSource().add(new MediaFile(path.toFile())));
                                }
                                else if (event.kind() == ENTRY_DELETE) {
                                    Path path = (Path)event.context();
                                    Platform.runLater(() -> tvMedia.getSource().remove(new MediaFile(path.toFile())));
                                }
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
                }, "Media Directory Watcher").start();
            }

            Path annotationPath = Paths.get(file.isDirectory() ? file.getAbsolutePath() : file.getParent(), Settings.getAnnotationDir());
            annotationPath = annotationPath.normalize();
            if (!annotationPath.toFile().exists()) {
                annotationPath.toFile().mkdir();
            }

            // Initial stats
            updateFileStats(tvMedia.getSource());

            if (annotationDirWatchKey != null && !annotationDirWatchKey.watchable().equals(annotationPath)) {
                annotationDirWatchKey.cancel();
            }
            annotationDirWatchKey = annotationPath.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            new Thread(() -> {
                try {
                    WatchKey key;
                    while ((key = watcher.take()) != null) {
                        for (WatchEvent<?> event : key.pollEvents()) {
                            if (event.kind() == OVERFLOW) {
                                continue;
                            }
                            // Observe changes and update stats
                            updateFileStats(tvMedia.getSource());
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
            }, "Annotations Directory Watcher").start();
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to watch", ex);
        }
    }

    public void updateFileStats() {
        updateFileStats(tvMedia.getSource());
    }

    private void updateFileStats(List<MediaFile> files) {
        final int annotated[] = {0};
        files.stream().forEach(media -> {
            media.refresh();
            if (Util.getAnnotationFile(media).exists()) {
                annotated[0]++;
            }
        });

        Platform.runLater(() -> {
            fileStats.setText(MessageFormat.format(bundle.getString("msg.fileStats"), annotated[0], files.size()));
        });
    }
}
