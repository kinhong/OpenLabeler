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

package com.easymobo.openlabeler.tensorflow;

import com.easymobo.openlabeler.model.HintModel;
import com.easymobo.openlabeler.preference.LabelMapItem;
import com.easymobo.openlabeler.preference.Settings;
import com.easymobo.openlabeler.util.AppUtils;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Tensor;
import org.tensorflow.TensorFlow;
import org.tensorflow.framework.DataType;
import org.tensorflow.framework.MetaGraphDef;
import org.tensorflow.framework.SignatureDef;
import org.tensorflow.framework.TensorInfo;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.ndarray.buffer.DataBuffers;
import org.tensorflow.types.TFloat32;
import org.tensorflow.types.TString;
import org.tensorflow.types.TUint8;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class ObjectDetector implements AutoCloseable
{
    private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

    private SavedModelBundle model;
    private ResourceBundle bundle = ResourceBundle.getBundle("bundle");

    // Monitors TF saved model directory status
    private WatchService watcher;
    private WatchKey tfSavedModelWatchKey;

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
            AppUtils.watchAndUpdate(watcher, "TF Saved Model Watcher", this::update);
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
                    printSignature(model);
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
            List<Tensor<?>> outputs;
            Tensor<?> input = null;
            String operation = "";
            BufferedImage img = ImageIO.read(imageFile);

            MetaGraphDef m = MetaGraphDef.parseFrom(model.metaGraphDef().toByteArray());
            SignatureDef sig = m.getSignatureDefOrThrow("serving_default");

            try {
                if (model.graph().operation("image_tensor") != null) {
                    operation = "image_tensor";
                    input = makeImageTensor(img);
                }
                else if (model.graph().operation("encoded_image_string_tensor") != null) {
                    operation = "encoded_image_string_tensor";
                    input = makeImageStringTensor(imageFile);
                }
                else if (sig.containsInputs("input_tensor")) {
                    TensorInfo tensorInfo = sig.getInputsOrThrow("input_tensor");
                    operation = tensorInfo.getName();
                    input = tensorInfo.getDtype() == DataType.DT_STRING ? makeImageStringTensor(imageFile) : makeImageTensor(img);
                }
                outputs = model.session()
                      .runner()
                      .feed(operation, input)
                      .fetch(sig.getOutputsOrThrow("detection_scores").getName())
                      .fetch(sig.getOutputsOrThrow("detection_classes").getName())
                      .fetch(sig.getOutputsOrThrow("detection_boxes").getName())
                      .run();
            }
            finally {
                if (input != null) {
                    input.close();
                }
            }
            try (Tensor<TFloat32> scoresT = outputs.get(0).expect(TFloat32.DTYPE);
                 Tensor<TFloat32> classesT = outputs.get(1).expect(TFloat32.DTYPE);
                 Tensor<TFloat32> boxesT = outputs.get(2).expect(TFloat32.DTYPE)) {
                // All these tensors have:
                // - 1 as the first dimension
                // - maxObjects as the second dimension
                // While boxesT will have 4 as the third dimension (2 sets of (x, y) coordinates).
                // This can be verified by looking at scoresT.shape() etc.
                int maxObjects = (int) scoresT.shape().asArray()[1];
                for (int i = 0; i < maxObjects; i++) {
                    float score = scoresT.data().getFloat(0, i);
                    if (score < 0.5) {
                        continue;
                    }
                    float ymin = boxesT.data().getFloat(0, i, 0) * img.getHeight();
                    float xmin = boxesT.data().getFloat(0, i, 1) * img.getWidth();
                    float ymax = boxesT.data().getFloat(0, i, 2) * img.getHeight();
                    float xmax = boxesT.data().getFloat(0, i, 3) * img.getWidth();
                    int id = (int) classesT.data().getFloat(0, i);
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

    private static void printSignature(SavedModelBundle model) {
        try {
            MetaGraphDef m = MetaGraphDef.parseFrom(model.metaGraphDef().toByteArray());
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
        catch (Exception ex) {}
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

    private static Tensor<?> makeImageTensor(BufferedImage img) throws IOException {
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
        Shape shape = Shape.of(BATCH_SIZE, img.getHeight(), img.getWidth(), CHANNELS);
        return TUint8.tensorOf(shape, DataBuffers.of(data));
    }

    /**
     * See <a href="https://github.com/tensorflow/tensorflow/issues/24331#issuecomment-447523402">GitHub issue</a>
     */
    private static Tensor<?> makeImageStringTensor(File imageFile) throws IOException {
        var content = FileUtils.readFileToByteArray(imageFile);
        return TString.tensorOfBytes(Shape.of(1), DataBuffers.ofObjects(content));
    }

    /**
     * See <a href="https://github.com/tensorflow/tensorflow/issues/8244#issuecomment-477854356">GitHub issue</a>
     */
    private static ByteBuffer stringArrayToBuffer(String[] values) throws IOException {
        long offsets[] = new long[values.length];
        byte[][] data = new byte[values.length][];
        int dataSize = 0;

        // Convert strings to encoded bytes and calculate required data size, including a varint for each of them
        for (int i = 0; i < values.length; ++i) {
            byte[] byteValue = values[i].getBytes("UTF-8");
            data[i] = byteValue;
            int length = byteValue.length + varintLength(byteValue.length);
            dataSize += length;
            if (i < values.length - 1) {
                offsets[i + 1] = offsets[i] + length;
            }
        }

        // Important: buffer must follow native byte order
        ByteBuffer buffer = ByteBuffer.allocate(dataSize + (offsets.length * 8)).order(ByteOrder.nativeOrder());

        // First, write offsets to each elements in the buffer
        for (int i = 0; i < offsets.length; ++i) {
            buffer.putLong(offsets[i]);
        }

        // Second, write strings bytes, each preceded by its length encoded as a varint
        for (int i = 0; i < data.length; ++i) {
            encodeVarint(buffer, data[i].length);
            buffer.put(data[i]);
        }

        return buffer.rewind();
    }

    private static void encodeVarint(ByteBuffer buffer, int value) {
        int v = value;
        while (v >= 0x80) {
            buffer.put((byte)((v & 0x7F) | 0x80));
            v >>= 7;
        }
        buffer.put((byte)v);
    }

    private static int varintLength(int length) {
        int len = 1;
        while (length >= 0x80) {
            length >>= 7;
            ++len;
        }
        return len;
    }
}
