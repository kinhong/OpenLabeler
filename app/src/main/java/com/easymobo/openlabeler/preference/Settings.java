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

import com.easymobo.openlabeler.preference.PreferenceUtil.BooleanPrefProperty;
import com.easymobo.openlabeler.preference.PreferenceUtil.ColorPrefProperty;
import com.easymobo.openlabeler.preference.PreferenceUtil.StringPrefProperty;
import javafx.beans.property.*;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Collection;
import java.util.prefs.Preferences;

public class Settings
{
    private static Preferences pref = Preferences.userNodeForPackage(Settings.class);
    // General
    private static final String OPEN_LAST_MEDIA = "openLastMedia";
    private static final String SAVE_EVERY_CHANGE = "saveEveryChange";
    private static final String ANNOTATION_DIR = "annotationDir";
    private static final String AUTO_SET_NAME = "autoSetName";
    private static final String OBJ_STROKE_COLOR = "objectStrokeColor";
    // Training
    private static final String TF_IMAGE_DIR = "tfImageDir";
    private static final String TF_ANNOTATION_DIR = "tfAnnotationDir";
    private static final String TF_DATA_DIR = "tfDataDir";
    private static final String TF_BASE_MODEL_DIR = "tfBaseModelDir";
    private static final String DOCKER_IMAGE = "dockerImage";
    private static final String CONTAINER_HOST_NAME = "containerHostName";
    private static final String CONTAINER_NAME = "containerName";
    // Inference
    private static final String USE_INFERENCE = "useInference";
    private static final String TF_LABEL_MAP_FILE = "tfLabelMapFile";
    private static final String TF_SAVED_MODEL_DIR = "tfSavedModelDir";
    private static final String HINT_STROKE_COLOR = "HintBoxColor";
    // Other
    private static final String RECENT_FILES = "recentFiles";
    private static final String RECENT_NAMES = "recentNames";

    // Open last media file/folder
    public static final BooleanProperty openLastMediaProperty = new BooleanPrefProperty(pref, OPEN_LAST_MEDIA, true);
    public static boolean isOpenLastMedia() {
        return openLastMediaProperty.get();
    }
    public static void setOpenLastMedia(boolean save) {
        openLastMediaProperty.set(save);
    }

    // Save every change
    public static final BooleanProperty saveEveryChangeProperty = new BooleanPrefProperty(pref, SAVE_EVERY_CHANGE, false);
    public static boolean isSaveEveryChange() {
        return saveEveryChangeProperty.get();
    }
    public static void setSaveEveryChange(boolean save) {
        saveEveryChangeProperty.set(save);
    }

    // Annotation directory name
    public static final StringProperty annotationDirProperty = new StringPrefProperty(pref, ANNOTATION_DIR, "../annotations");
    public static String getAnnotationDir() {
        return annotationDirProperty.get();
    }
    public static void setAnnotationDir(String dir) {
        annotationDirProperty.set(dir);
    }

    // Auto fill name
    public static final BooleanProperty autoSetNameProperty = new BooleanPrefProperty(pref, AUTO_SET_NAME, true);
    public static boolean getAutoSetName() {
        return autoSetNameProperty.get();
    }
    public static void setAutoSetName(boolean use) {
        autoSetNameProperty.set(use);
    }

    // Object bounding box color
    public static final ObjectProperty<Color> objectStrokeColorProperty = new ColorPrefProperty(pref, OBJ_STROKE_COLOR, Color.LIMEGREEN);
    public static Color getObjectStrokeColor() {
        return objectStrokeColorProperty.get();
    }
    public static void setObjectStrokeColor(Color color) {
        objectStrokeColorProperty.set(color);
    }
    public static ReadOnlyObjectProperty<Color> objectFillColorProperty = new SimpleObjectProperty() {
        @Override
        public Color get() {
            Color base = getObjectStrokeColor();
            return new Color(base.getRed(), base.getGreen(), base.getBlue(), 0.3);
        }
    };
    public static Color getObjectFillColor() {
        return objectFillColorProperty.get();
    }

    // TensorFlow Image directory
    public static final StringProperty tfImageDirProperty = new StringPrefProperty(pref, TF_IMAGE_DIR, "");
    public static String getTFImageDir() {
        return tfImageDirProperty.get();
    }
    public static void setTFImageDir(String dir) {
        tfImageDirProperty.set(dir);
    }

    // TensorFlow Annotation directory
    public static final StringProperty tfAnnotationDirProperty = new StringPrefProperty(pref, TF_ANNOTATION_DIR, "");
    public static String getTFAnnotationDir() {
        return tfAnnotationDirProperty.get();
    }
    public static void setTFAnnotationDir(String dir) {
        tfAnnotationDirProperty.set(dir);
    }

    // TensorFlow data directory
    public static final StringProperty tfDataDirProperty = new StringPrefProperty(pref, TF_DATA_DIR, "");
    public static String getTFDataDir() {
        return tfDataDirProperty.get();
    }
    public static void setTFDataDir(String dir) {
        tfDataDirProperty.set(dir);
    }

    // TensorFlow base model directory
    public static final StringProperty tfBaseModelDirProperty = new StringPrefProperty(pref, TF_BASE_MODEL_DIR, "");
    public static String getTFBaseModelDir() {
        return tfBaseModelDirProperty.get();
    }
    public static void setTFBaseModelDir(String dir) {
        tfBaseModelDirProperty.set(dir);
    }

    // Docker Image
    public static final StringProperty dockerImageProperty = new StringPrefProperty(pref, DOCKER_IMAGE, "kinhong/openlabeler:latest-py3");
    public static String getDockerImage() {
        return dockerImageProperty.get();
    }
    public static void setDockerImage(String dir) {
        dockerImageProperty.set(dir);
    }

    // Docker Container Host Name
    public static final StringProperty containerHostNameProperty = new StringPrefProperty(pref, CONTAINER_HOST_NAME, "localhost");
    public static String getContainerHostName() {
        return containerHostNameProperty.get();
    }
    public static void setContainerHostName(String dir) {
        containerHostNameProperty.set(dir);
    }

    // Docker Container Name
    public static final StringProperty containerNameProperty = new StringPrefProperty(pref, CONTAINER_NAME, "OpenLabeler-Trainer");
    public static String getContainerName() {
        return containerNameProperty.get();
    }
    public static void setContainerName(String dir) {
        containerNameProperty.set(dir);
    }

    // Use inference
    public static final BooleanProperty useInferenceProperty = new BooleanPrefProperty(pref, USE_INFERENCE, false);
    public static boolean isUseInference() {
        return useInferenceProperty.get();
    }
    public static void setUseInference(boolean save) {
        useInferenceProperty.set(save);
    }

    // Object bounding box color
    public static final ObjectProperty<Color> hintStrokeColorProperty = new ColorPrefProperty(pref, HINT_STROKE_COLOR, Color.LIGHTSALMON);
    public static Color getHintStrokeColor() {
        return hintStrokeColorProperty.get();
    }
    public static void setHintStrokeColor(Color color) {
        hintStrokeColorProperty.set(color);
    }
    public static ReadOnlyObjectProperty<Color> hintFillColorProperty = new SimpleObjectProperty() {
        @Override
        public Color get() {
            Color base = getHintStrokeColor();
            return new Color(base.getRed(), base.getGreen(), base.getBlue(), 0.3);
        }
    };
    public static Color getHintFillColor() {
        return hintFillColorProperty.get();
    }

    // TensorFlow label map file
    public static final StringProperty tfLabelMapFileProperty = new StringPrefProperty(pref, TF_LABEL_MAP_FILE, "");
    public static String getTFLabelMapFile() {
        return tfLabelMapFileProperty.get();
    }
    public static void setTFLabelMapFile(String file) {
        tfLabelMapFileProperty.set(file);
    }

    // TensorFlow saved model directory
    public static final StringProperty tfSavedModelDirProperty = new StringPrefProperty(pref, TF_SAVED_MODEL_DIR, "");
    public static String getTFSavedModelDir() {
        return tfSavedModelDirProperty.get();
    }
    public static void setTFSavedModelDir(String dir) {
        tfSavedModelDirProperty.set(dir);
    }

    // Recent files
    public static final RecentList recentFiles = new RecentList(4, RECENT_FILES);

    // Recent labels
    public static final RecentList recentNames = new RecentList(50, RECENT_NAMES);

    public static class RecentList extends ArrayList<String>
    {
        private final int maxLength;
        private final String baseKey;

        public RecentList(int maxLength, String baseKey) {
            this.maxLength = maxLength;
            this.baseKey = baseKey;
            load();
        }

        @Override
        public boolean add(String element) {
            remove(element);
            add(0, element);
            reduce();
            save();
            return true;
        }

        @Override
        public boolean addAll(Collection<? extends String> elements) {
            elements.forEach(e -> {
                remove(e);
                add(0, e);
            });
            reduce();
            save();
            return true;
        }

        @Override
        public void clear() {
            super.clear();
            save();
        }

        public String getByPrefix(String prefix) {
            String pre = prefix.toLowerCase();
            return stream().map(String::toLowerCase).filter(item -> item.startsWith(pre)).findFirst().orElse(null);
        }

        private void reduce() {
            while (size() > maxLength) {
                remove(size() - 1);
            }
        }

        private void load() {
            for (int i = 0; i < maxLength; i++) {
                String val = pref.get(baseKey + i, ""); //$NON-NLS-1$
                if (val.equals("")) { /*$NON-NLS-1$*/
                    break;
                }
                super.add(val);
            }
        }

        private void save () {
            for (int i = 0; i < maxLength; i++) {
                if (i < size()) {
                    pref.put(baseKey + i, get(i));
                }
                else {
                    pref.remove(baseKey + i);
                }
            }
        }
    }
}
