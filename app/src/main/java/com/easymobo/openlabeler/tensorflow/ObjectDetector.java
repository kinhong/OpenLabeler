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

package com.easymobo.openlabeler.tensorflow;

import com.easymobo.openlabeler.model.HintModel;
import com.easymobo.openlabeler.preference.LabelMapItem;
import com.easymobo.openlabeler.preference.Settings;
import com.easymobo.openlabeler.util.Util;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Tensor;
import org.tensorflow.TensorFlow;
import org.tensorflow.framework.MetaGraphDef;
import org.tensorflow.framework.SignatureDef;
import org.tensorflow.framework.TensorInfo;
import org.tensorflow.types.UInt8;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class ObjectDetector implements AutoCloseable
{
    private static final Logger LOG = Logger.getLogger(ObjectDetector.class.getCanonicalName());

    private SavedModelBundle model;
    private ResourceBundle bundle;

    // Monitors TF saved model directory status
    private WatchService watcher;
    private WatchKey tfSavedModelWatchKey;

    public ObjectDetector(ResourceBundle bundle) {
        this.bundle = bundle;
    }

    public void init() {
        synchronized (this) {
            try {
                watcher = FileSystems.getDefault().newWatchService();
            }
            catch (IOException ex) {
                LOG.log(Level.SEVERE, "Unable to create new file watcher", ex);
            }

            watch(Paths.get(Settings.getTFSavedModelDir()));
            Settings.tfSavedModelDirProperty.addListener((observable, oldValue, newValue) -> {
                watch(Paths.get(newValue));
            });

            LOG.log(Level.INFO, "TensorFlow: " + TensorFlow.version());
        }
    }

    @Override
    public void close() {
        if (model != null) {
            model.close();
        }
        Optional.ofNullable(tfSavedModelWatchKey).ifPresent(key -> key.cancel());
        IOUtils.closeQuietly(watcher);
    }

    private SimpleStringProperty statusProperty = new SimpleStringProperty();
    public StringProperty statusProperty() {
        return statusProperty;
    }

    private void watch(Path savedModelPath) {
        try {
            Path savedModelParent = savedModelPath.getParent();
            if (savedModelParent == null) {
                return;
            }
            if (!savedModelParent.toFile().exists()) {
                LOG.info(savedModelParent.toString() + " does not exist");
                return;
            }
            if (tfSavedModelWatchKey != null && !tfSavedModelWatchKey.watchable().equals(tfSavedModelWatchKey)) {
                tfSavedModelWatchKey.cancel();
            }
            update(null);
            tfSavedModelWatchKey = savedModelParent.register(watcher, ENTRY_CREATE, ENTRY_MODIFY);
            Util.watchAndUpdate(watcher, "TF Saved Model Watcher", this::update);
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to watch", ex);
        }
    }

    private Void update(Path path) {
        try {
            File savedModelFile = new File(Settings.getTFSavedModelDir());
            if (savedModelFile.exists() && (path == null || "saved_model".equals(path.toString()))) {
                if (path != null) { // coming from watched file
                    Thread.sleep(5000); // Wait a while for model to be exported
                }
                synchronized (ObjectDetector.this) {
                    if (model != null) {
                        model.close();
                    }
                    model = SavedModelBundle.load(savedModelFile.getAbsolutePath(), "serve");
                    String message = MessageFormat.format(bundle.getString("msg.loadedSavedModel"), savedModelFile);
                    LOG.info(message);
                    Platform.runLater(() -> statusProperty.set(message));
                }
            }
            else if (!savedModelFile.exists() && path == null) {
                LOG.info(savedModelFile.toString() + " does not exist");
            }
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to update " + path, ex);
        }
        return null;
    }

    public List<HintModel> detect(File imageFile) throws IOException {
        synchronized (ObjectDetector.this) {
            String[] labels = loadLabels();
            if (ArrayUtils.isEmpty(labels) || model == null) {
                return Collections.emptyList();
            }
            List<HintModel> hints = new ArrayList();
            //printSignature(model);
            List<Tensor<?>> outputs;
            BufferedImage img = ImageIO.read(imageFile);
            try (Tensor<UInt8> input = makeImageTensor(img)) {
                outputs = model.session()
                        .runner()
                        .feed("image_tensor", input)
                        .fetch("detection_scores")
                        .fetch("detection_classes")
                        .fetch("detection_boxes")
                        .run();
            }
            try (Tensor<Float> scoresT = outputs.get(0).expect(Float.class);
                 Tensor<Float> classesT = outputs.get(1).expect(Float.class);
                 Tensor<Float> boxesT = outputs.get(2).expect(Float.class)) {
                // All these tensors have:
                // - 1 as the first dimension
                // - maxObjects as the second dimension
                // While boxesT will have 4 as the third dimension (2 sets of (x, y) coordinates).
                // This can be verified by looking at scoresT.shape() etc.
                int maxObjects = (int) scoresT.shape()[1];
                float[] scores = scoresT.copyTo(new float[1][maxObjects])[0];
                float[] classes = classesT.copyTo(new float[1][maxObjects])[0];
                float[][] boxes = boxesT.copyTo(new float[1][maxObjects][4])[0];
                // Collect all objects whose score is at least 0.5.
                for (int i = 0; i < scores.length; ++i) {
                    float score = scores[i];
                    if (score < 0.5) {
                        continue;
                    }
                    float ymin = boxes[i][0] * img.getHeight();
                    float xmin = boxes[i][1] * img.getWidth();
                    float ymax = boxes[i][2] * img.getHeight();
                    float xmax = boxes[i][3] * img.getWidth();
                    int id = (int) classes[i];
                    if (id < labels.length) {
                        HintModel model = new HintModel(labels[id], xmin, ymin, xmax, ymax);
                        model.setScore(score);
                        hints.add(model);
                    }
                }
                if (hints.size() <= 0) {
                    LOG.info("No objects detected with a high enough score.");
                    Platform.runLater(() -> statusProperty.set(bundle.getString("msg.noObjectsDetected")));
                }
            }
            return hints;
        }
    }

    private static void printSignature(SavedModelBundle model) throws Exception {
        MetaGraphDef m = MetaGraphDef.parseFrom(model.metaGraphDef());
        SignatureDef sig = m.getSignatureDefOrThrow("serving_default");
        int numInputs = sig.getInputsCount();
        int i = 1;
        LOG.info("MODEL SIGNATURE");
        LOG.info("Inputs:");
        for (Map.Entry<String, TensorInfo> entry : sig.getInputsMap().entrySet()) {
            TensorInfo t = entry.getValue();
            LOG.info(String.format("%d of %d: %-20s (Node name in graph: %-20s, type: %s)",
                    i++, numInputs, entry.getKey(), t.getName(), t.getDtype()));
        }
        int numOutputs = sig.getOutputsCount();
        i = 1;
        LOG.info("Outputs:");
        for (Map.Entry<String, TensorInfo> entry : sig.getOutputsMap().entrySet()) {
            TensorInfo t = entry.getValue();
            LOG.info(String.format("%d of %d: %-20s (Node name in graph: %-20s, type: %s)",
                    i++, numOutputs, entry.getKey(), t.getName(), t.getDtype()));
        }
        LOG.info("-----------------------------------------------");
    }

    private static String[] loadLabels() {
        try {
            Path labelMapPath = Paths.get(Settings.getTFLabelMapFile());
            List<LabelMapItem> items = TFTrainer.getLabelMapItems(labelMapPath);
            int maxId = items.stream().mapToInt(item -> item.getId()).max().orElse(0);
            String[] ret = new String[maxId + 1];
            for (LabelMapItem item : items) {
                ret[item.getId()] = item.getName();
            }
            return ret;
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to parse label map", ex);
        }
        return new String[1];
    }

    private static void bgr2rgb(byte[] data) {
        for (int i = 0; i < data.length; i += 3) {
            byte tmp = data[i];
            data[i] = data[i + 2];
            data[i + 2] = tmp;
        }
    }

    private static Tensor<UInt8> makeImageTensor(BufferedImage img) throws IOException {
        if (img.getType() == BufferedImage.TYPE_BYTE_INDEXED
              || img.getType() == BufferedImage.TYPE_BYTE_BINARY
              || img.getType() == BufferedImage.TYPE_BYTE_GRAY
              || img.getType() == BufferedImage.TYPE_USHORT_GRAY) {
            BufferedImage bgr = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            bgr.getGraphics().drawImage(img, 0, 0, null);
            img = bgr;
        }
        if (img.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            throw new IOException(
                    String.format(
                            "Expected 3-byte BGR encoding in BufferedImage, found %d. This code could be made more robust",
                            img.getType()));
        }
        byte[] data = ((DataBufferByte) img.getData().getDataBuffer()).getData();
        // ImageIO.read seems to produce BGR-encoded images, but the model expects RGB.
        bgr2rgb(data);
        final long BATCH_SIZE = 1;
        final long CHANNELS = 3;
        long[] shape = new long[]{BATCH_SIZE, img.getHeight(), img.getWidth(), CHANNELS};
        return Tensor.create(UInt8.class, shape, ByteBuffer.wrap(data));
    }
}
