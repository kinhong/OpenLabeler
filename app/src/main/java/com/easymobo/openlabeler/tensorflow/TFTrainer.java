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
 *
 */

package com.easymobo.openlabeler.tensorflow;

import com.easymobo.openlabeler.preference.LabelMapItem;
import com.easymobo.openlabeler.preference.Settings;
import com.easymobo.openlabeler.util.Util;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.google.protobuf.TextFormat;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import object_detection.protos.InputReaderOuterClass;
import object_detection.protos.Pipeline;
import object_detection.protos.StringIntLabelMapOuterClass;
import object_detection.protos.StringIntLabelMapOuterClass.StringIntLabelMap;
import object_detection.protos.StringIntLabelMapOuterClass.StringIntLabelMapItem;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;

public class TFTrainer implements AutoCloseable
{
    private static final Logger LOG = Logger.getLogger(TFTrainer.class.getCanonicalName());
    private static final String EXPORTER = "OpenLabeler-Exporter";

    private ResourceBundle bundle;

    // Monitors TF training status
    private WatchService watcher;
    private WatchKey tfTrainDirWatchKey;

    // Docker
    private static DockerClient dockerClient = DockerClientBuilder.getInstance().build();

    private SimpleIntegerProperty checkpointProperty = new SimpleIntegerProperty(-1);
    public IntegerProperty checkpointProperty() {
        return checkpointProperty;
    }

    public TFTrainer(ResourceBundle bundle) {
        this.bundle = bundle;
    }

    public void init() {
        try {
            watcher = FileSystems.getDefault().newWatchService();
        }
        catch (IOException ex) {
            LOG.log(Level.SEVERE, "Unable to initialize trainer", ex);
        }

        watch(getTrainPath(Settings.getTFDataDir()));
        Settings.tfDataDirProperty.addListener((observable, oldValue, newValue) -> {
            watch(Paths.get(newValue));
        });
    }

    @Override
    public void close() {
        Optional.ofNullable(tfTrainDirWatchKey).ifPresent(key -> key.cancel());
        IOUtils.closeQuietly(watcher);
    }

    public static Path getDataPath() {
        return Paths.get(Settings.getTFDataDir());
    }

    public static Path getTrainRecordPath(String dataDir) {
        return Paths.get(dataDir, "train.record");
    }

    public static Path getEvalRecordPath(String dataDir) {
        return Paths.get(dataDir, "eval.record");
    }

    public static Path getLabelMapPath(String dataDir) {
        return Paths.get(dataDir, "label_map.pbtxt");
    }

    public static Path getModelConfigPath(String baseModelDir) {
        return Paths.get(baseModelDir, "model.config");
    }

    public static Path getPipelinePath(String baseModelDir) {
        return Paths.get(baseModelDir, "pipeline.config");
    }

    public static Path getTrainPath(String baseModelDir) {
        return Paths.get(baseModelDir, "train");
    }

    public static Path getSavedModelPath(String baseModelDir) {
        return Paths.get(baseModelDir, "train", "saved_model");
    }

    public static boolean canTrain(String dataDir, String baseModeDir) {
        return getTrainRecordPath(dataDir).toFile().exists()
                && getEvalRecordPath(dataDir).toFile().exists()
                && getLabelMapPath(dataDir).toFile().exists()
                && getModelConfigPath(baseModeDir).toFile().exists();
    }

    private static boolean isRunning(Container container) {
        return !(container.getStatus().contains("Exited") || container.getStatus().contains("Created"));
    }

    private void watch(Path trainPath) {
        try {
            File trainDir = trainPath.toFile();
            if (!trainDir.exists()) {
                return;
            }
            if (tfTrainDirWatchKey != null && !tfTrainDirWatchKey.watchable().equals(trainDir)) {
                tfTrainDirWatchKey.cancel();
            }
            update(null);
            tfTrainDirWatchKey = trainPath.register(watcher, ENTRY_CREATE);
            Util.watchAndUpdate(watcher, "TF Train Directory Watcher", this::update);
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to watch", ex);
        }
    }

    private Void update(Path path) {
        int checkpoint = getTrainCkpt(Settings.getTFDataDir());
        if (checkpoint >= 0) {
            Platform.runLater(() -> checkpointProperty.set(checkpoint));
        }
        return null;
    }

    public static List<LabelMapItem> getLabelMapItems(String dataDir) {
        return getLabelMapItems(getLabelMapPath(dataDir));
    }

    public static List<LabelMapItem> getLabelMapItems(Path path) {
        try {
            if (path.toFile().exists()) {
                String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                StringIntLabelMap.Builder builder = StringIntLabelMap.newBuilder();
                TextFormat.merge(text, builder);
                StringIntLabelMap proto = builder.build();
                return proto.getItemList().stream().map(i -> new LabelMapItem(i)).collect(Collectors.toList());
            }
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to parse label map", ex);
        }
        return Collections.emptyList();
    }

    public static boolean isTraining() {
        List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
        for (Container container : containers) {
            String name = Settings.getContainerName();
            if (ArrayUtils.isNotEmpty(container.getNames()) && container.getNames()[0].contains(name)) {
                return isRunning(container);
            }
        }
        return false;
    }

    public static void exportGraph(int checkpoint) {
        try {
            // Clean up
            List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
            for (Container container : containers) {
                if (ArrayUtils.isNotEmpty(container.getNames()) && container.getNames()[0].contains(EXPORTER)) {
                    if (!isRunning(container)) {
                        dockerClient.removeContainerCmd(container.getId()).exec();
                    }
                    else {
                        // Still exporting, wait for a while for the current export to complete
                        Thread.sleep(5000);
                    }
                }
            }

            FileUtils.deleteDirectory(getSavedModelPath(Settings.getTFBaseModelDir()).toFile());

            // Export inference graph
            CreateContainerResponse container = dockerClient.createContainerCmd(Settings.getDockerImage())
                    .withName(EXPORTER)
                    .withWorkingDir("/root")
                    .withHostName(Settings.getContainerHostName())
                    .withCmd("python3", "/root/object_detection/export_inference_graph.py",
                            "--input_type=image_tensor",
                            "--trained_checkpoint_prefix=" + getDockerModelTrainPath() + "/model.ckpt-" + checkpoint,
                            "--pipeline_config_path=" + getDockerModelConfigPath(),
                            "--output_directory=" + getDockerModelTrainPath())
                    .withHostConfig(new HostConfig().withAutoRemove(true).withBinds(getDockerBinds())).exec();

            dockerClient.startContainerCmd(container.getId()).exec();
            LOG.info("Exporting inference graph for checkpoint " + checkpoint);
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to export checkpoint " + checkpoint, ex);
        }
    }

    public static void saveLabelMap(List<LabelMapItem> items, String dataDir) throws IOException {
        if (items.size() <= 0) {
            LOG.severe("No label map items");
        }
        StringIntLabelMapOuterClass.StringIntLabelMap.Builder builder = StringIntLabelMapOuterClass.StringIntLabelMap.newBuilder();
        items.forEach(item -> {
            builder.addItem(StringIntLabelMapItem.newBuilder().setId(item.getId()).setName(item.getName()).setDisplayName(item.getDisplayName()));
        });
        String labelMap = TextFormat.printToString(builder.build());
        if (StringUtils.isNotEmpty(labelMap)) {
            getLabelMapPath(dataDir).toFile().getParentFile().mkdirs();
            Files.write(getLabelMapPath(dataDir), labelMap.getBytes());
            LOG.info("Created " + getLabelMapPath(dataDir));
        }
    }

    public static int getTrainCkpt(String baseModelDir) {
        File trainDir = getTrainPath(baseModelDir).toFile();
        if (!trainDir.exists()) {
            return -1;
        }
        // Find the latest checkpoint
        Pattern pattern = Pattern.compile("model.ckpt-([0-9]+).meta");
        int ckpt = 0;
        for (File file : trainDir.listFiles()) {
            Matcher matcher = pattern.matcher(file.getName());
            if (matcher.matches()) {
                ckpt = Math.max(ckpt, Integer.valueOf(matcher.group(1)));
            }
        }
        return ckpt;
    }

    public static void train(boolean start, boolean restart) {
        try {
            String dataDir = Settings.getTFDataDir();
            String baseModelDir = Settings.getTFDataDir();
            String containerName = Settings.getContainerName();
            if (start) {
                Container trainer = null;
                List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
                for (Container container : containers) {
                    if (ArrayUtils.isNotEmpty(container.getNames())
                            && container.getNames()[0].contains(containerName)) {
                        trainer = container;
                        break;
                    }
                }
                if (restart) {
                    List<LabelMapItem> items = getLabelMapItems(dataDir);
                    FileUtils.deleteDirectory(getDataPath().toFile());
                    FileUtils.deleteDirectory(getTrainPath(baseModelDir).toFile());
                    FileUtils.deleteDirectory(getSavedModelPath(baseModelDir).toFile());
                    getDataPath().toFile().mkdirs();
                    LOG.info("Finished clean up");

                    saveLabelMap(items, dataDir);
                    TFRecordCreator recordCreator = new TFRecordCreator(Paths.get(Settings.getTFImageDir()), Paths.get(Settings.getTFAnnotationDir()), getDataPath());
                    recordCreator.create(baseModelDir);

                    // Kill/remove existing training
                    if (trainer != null) {
                        if (isRunning(trainer)) {
                            dockerClient.killContainerCmd(trainer.getId()).exec();
                        }
                        if (!isRunning(trainer)) {
                            dockerClient.removeContainerCmd(trainer.getId()).exec();
                        }
                    }

                    // Recreate the trainer
                    PipelineConfig config = new PipelineConfig(baseModelDir);
                    CreateContainerResponse container = dockerClient.createContainerCmd(Settings.getDockerImage())
                            .withName(containerName)
                            .withWorkingDir("/root")
                            .withCmd("python3", "/root/object_detection/model_main.py",
                                    "--alsologtostderr",
                                    "--pipeline_config_path=" + getDockerModelConfigPath(),
                                    "--model_dir=" + getDockerModelTrainPath(),
                                    "--num_train_steps=" + config.getNumTrainSteps(),
                                    "--sample_1_of_n_eval_examples=" + 1,
                                    getDockerModelConfigPath())
                            .withHostConfig(new HostConfig().withBinds(getDockerBinds())).exec();

                    dockerClient.startContainerCmd(container.getId()).exec();
                }
                else {
                    if (trainer == null) {
                        LOG.warning("TFTrainer cannot be found");
                        return;
                    }
                    dockerClient.startContainerCmd(trainer.getId()).exec();
                }
            }
            else { // Stop training
                List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
                for (Container container : containers) {
                    if (ArrayUtils.isNotEmpty(container.getNames()) && container.getNames()[0].contains(containerName)) {
                        dockerClient.stopContainerCmd(container.getId()).exec();
                    }
                }
            }
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to train", ex);
        }
    }
    /**
     * A wrapper around TensorFlow's TrainEvalPipelineConfig
     */
    public static class PipelineConfig
    {
        private final String baseModelDir;
        private Pipeline.TrainEvalPipelineConfig config;

        public PipelineConfig(String baseModelDir) {
            this.baseModelDir = baseModelDir;
            reload();
        }

        public void reload() {
            if (getModelConfigPath(baseModelDir).toFile().exists()) {
                config = parse(getModelConfigPath(baseModelDir));
            }
            else if (getPipelinePath(baseModelDir).toFile().exists()) {
                config = parse(getPipelinePath(baseModelDir));
            }
            config = updatePaths(config);
        }

        public void save() throws IOException {
            if (config == null) {
                return;
            }
            String pipelineConfig = TextFormat.printToString(config);
            Files.write(getModelConfigPath(baseModelDir), pipelineConfig.getBytes());
            LOG.info("Created "+ getModelConfigPath(baseModelDir));
        }

        private Pipeline.TrainEvalPipelineConfig parse(Path path) {
            try {
                String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                Pipeline.TrainEvalPipelineConfig.Builder builder = Pipeline.TrainEvalPipelineConfig.newBuilder();
                TextFormat.Parser parser = TextFormat.Parser.newBuilder().build();

                // Skip unknown fields
                Field f = parser.getClass().getDeclaredField("allowUnknownFields");
                f.setAccessible(true);
                f.set(parser, true);

                parser.merge(text, builder);
                return builder.build();
            }
            catch (Exception ex) {
                LOG.log(Level.SEVERE, "Unable to parse pipeline", ex);
            }
            return null;
        }

        public String getType() {
            if (config != null  && config.getModel().hasSsd()) {
                return config.getModel().getSsd().getFeatureExtractor().getType();
            }
            else if (config != null && config.getModel().hasFasterRcnn()) {
                return config.getModel().getFasterRcnn().getFeatureExtractor().getType();
            }
            return null;
        }

        public int getNumTrainSteps() {
            if (config != null) {
                return config.getTrainConfig().getNumSteps();
            }
            return 200000;
        }

        // Update pipeline config with paths expected in docker container
        private Pipeline.TrainEvalPipelineConfig updatePaths(Pipeline.TrainEvalPipelineConfig config) {
            if (config == null) {
                return config;
            }
            // train_config.fine_tune_checkpoint
            Pipeline.TrainEvalPipelineConfig.Builder builder = config.toBuilder();
            builder.setTrainConfig(config.getTrainConfig().toBuilder().setFineTuneCheckpoint(getDockerFineTuneCkptPath()));

            // train_input_reader
            InputReaderOuterClass.InputReader.Builder b = config.getTrainInputReader().toBuilder();
            b.setLabelMapPath(getDockerLabelMapPath());
            builder.setTrainInputReader(b.setTfRecordInputReader(b.getTfRecordInputReader().toBuilder().setInputPath(0, getDockerTrainRecordPath())));

            // eval_input_reader
            InputReaderOuterClass.InputReader.Builder b2 = config.getEvalInputReader(0).toBuilder();
            b2.setLabelMapPath(getDockerLabelMapPath());
            builder.setEvalInputReader(0, b2.setTfRecordInputReader(b2.getTfRecordInputReader().toBuilder().setInputPath(0, getDockerEvalRecordPath())));

            return builder.build();
        }
    }

    // Paths for configuring pipeline in docker container
    public static String getDockerLabelMapPath() {
        return "/root/data/label_map.pbtxt";
    }

    public static String getDockerTrainRecordPath() {
        return "/root/data/train.record";
    }

    public static String getDockerEvalRecordPath() {
        return "/root/data/eval.record";
    }

    public static String getDockerFineTuneCkptPath() {
        return "/root/model/model.ckpt";
    }

    public static String getDockerModelTrainPath() {
        return "/root/model/train";
    }

    public static String getDockerModelConfigPath() {
        return "/root/model/model.config";
    }

    public static Bind[] getDockerBinds() {
        Bind dataDir = Bind.parse(Settings.getTFDataDir() + ":/root/data");
        Bind modelDir = Bind.parse(Settings.getTFBaseModelDir() + ":/root/model");
        return new Bind[] { dataDir, modelDir };
    }
}
