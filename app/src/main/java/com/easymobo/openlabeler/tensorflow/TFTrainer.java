/*
 * Copyright (c) 2024. Kin-Hong Wong. All Rights Reserved.
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

import com.easymobo.openlabeler.preference.LabelMapItem;
import com.easymobo.openlabeler.preference.Settings;
import com.easymobo.openlabeler.util.AppUtils;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.google.protobuf.TextFormat;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import object_detection.protos.*;
import object_detection.protos.StringIntLabelMapOuterClass.StringIntLabelMap;
import object_detection.protos.StringIntLabelMapOuterClass.StringIntLabelMapItem;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
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
    private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());
    private static final String EXPORTER = "OpenLabeler-Exporter";

    private ResourceBundle bundle = ResourceBundle.getBundle("bundle");

    // Monitors TF training status
    private WatchService watcher;
    private WatchKey tfTrainDirWatchKey;

    // Docker
    private static DockerClientConfig dockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
    private static DockerHttpClient dockerHttpClient = new ApacheDockerHttpClient.Builder().dockerHost(dockerClientConfig.getDockerHost()).build();
    private static DockerClient dockerClient = DockerClientBuilder.getInstance().withDockerHttpClient(dockerHttpClient).build();

    private SimpleIntegerProperty checkpointProperty = new SimpleIntegerProperty(-1);
    public IntegerProperty checkpointProperty() {
        return checkpointProperty;
    }

    public void init() {
        try {
            watcher = FileSystems.getDefault().newWatchService();
        }
        catch (IOException ex) {
            LOG.log(Level.SEVERE, "Unable to initialize trainer", ex);
        }

        watch(getModelDirPath(Settings.getTFDataDir()));
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

    public static Path getModelDirPath(String baseModelDir) {
        return Paths.get(baseModelDir, "temp");
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
            AppUtils.watchAndUpdate(watcher, "TF Train Directory Watcher", this::update);
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

    public static Info getInfo() {
        try {
            return dockerClient.infoCmd().exec();
        }
        catch (Exception ex) {
            return null;
        }
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

    public static void createTrainData(List<LabelMapItem> items) {
        try {
            FileUtils.deleteDirectory(getDataPath().toFile());
            String dataDir = Settings.getTFDataDir();
            getDataPath().toFile().mkdirs();
            saveLabelMap(items, dataDir);
            LOG.info("Creating training data in " + dataDir + "...");
            TFRecordCreator recordCreator = new TFRecordCreator(Paths.get(Settings.getTFImageDir()), Paths.get(Settings.getTFAnnotationDir()), getDataPath());
            recordCreator.createData(items);
            LOG.info("Created training data in " + dataDir);
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to create training data", ex);
        }
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
                    .withCmd("python3", "/root/object_detection/exporter_main_v2.py",
                          "--trained_checkpoint_dir=" + getDockerModelDirPath() ,
                          "--pipeline_config_path=" + getDockerModelConfigPath(),
                          "--output_directory=" + getDockerExportGraphPath())
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
        File trainDir = getModelDirPath(baseModelDir).toFile();
        if (!trainDir.exists()) {
            return -1;
        }
        // Find the latest checkpoint
        Pattern pattern = Pattern.compile("ckpt-([0-9]+).index");
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
            String baseModelDir = Settings.getTFBaseModelDir();
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
                    List<LabelMapItem> items = getLabelMapItems(Settings.getTFDataDir());
                    createTrainData(items);

                    FileUtils.deleteDirectory(getModelDirPath(baseModelDir).toFile());
                    FileUtils.deleteDirectory(getSavedModelPath(baseModelDir).toFile());
                    LOG.info("Finished cleaning up train directory");

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
                            .withCmd("python3", "/root/object_detection/model_main_tf2.py",
                                    "--alsologtostderr",
                                    "--pipeline_config_path=" + getDockerModelConfigPath(),
                                    "--model_dir=" + getDockerModelDirPath(),
                                    "--num_train_steps=" + config.getNumTrainSteps(),
                                    "--sample_1_of_n_eval_examples=" + 1,
                                    getDockerModelConfigPath())
                            .withHostConfig(new HostConfig().withBinds(getDockerBinds())).exec();
                    LOG.info("Created new container " + container.getId());

                    dockerClient.startContainerCmd(container.getId()).exec();
                    LOG.info("Started new container " + container.getId());
                }
                else { // Continue training
                    if (trainer == null) {
                        LOG.warning("TFTrainer docker container not found");
                        return;
                    }
                    dockerClient.startContainerCmd(trainer.getId()).exec();
                    LOG.info("Started container " + trainer.getId());
                }
            }
            else { // Stop training
                List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
                for (Container container : containers) {
                    if (ArrayUtils.isNotEmpty(container.getNames()) && container.getNames()[0].contains(containerName)) {
                        dockerClient.stopContainerCmd(container.getId()).withTimeout(1).exec();
                        LOG.info("Stopped container " + container.getId());
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
            config = update(config);
        }

        public void save() throws IOException {
            if (config == null) {
                return;
            }
            config = update(config);
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
        private Pipeline.TrainEvalPipelineConfig update(Pipeline.TrainEvalPipelineConfig config) {
            if (config == null) {
                return config;
            }
            // train_config.fine_tune_checkpoint
            Pipeline.TrainEvalPipelineConfig.Builder builder = config.toBuilder();
            builder.setTrainConfig(config.getTrainConfig().toBuilder()
                  .setBatchSize(Settings.getTFTrainBatchSize())
                  .setFineTuneCheckpoint(getDockerFineTuneCkptPath())
                  .setFineTuneCheckpointType("detection")
                  .setFineTuneCheckpointVersion(Train.CheckpointVersion.V2));

            // number of classes
            int numClasses = getLabelMapItems(Settings.getTFDataDir()).size();
            Model.DetectionModel.Builder modelBuilder = config.getModel().toBuilder();
            if (config.getModel().hasSsd()) {
                modelBuilder.setSsd(config.getModel().getSsd().toBuilder().setNumClasses(numClasses));
            }
            else if (config.getModel().hasFasterRcnn()) {
                modelBuilder.setFasterRcnn(config.getModel().getFasterRcnn().toBuilder().setNumClasses(numClasses));
            }
            builder.setModel(modelBuilder);

            // train_input_reader
            InputReaderOuterClass.InputReader.Builder b1 = config.getTrainInputReader().toBuilder();
            b1.setLabelMapPath(getDockerLabelMapPath());
            builder.setTrainInputReader(b1.setTfRecordInputReader(b1.getTfRecordInputReader().toBuilder().setInputPath(0, getDockerTrainRecordPath())));

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
        return "/root/model/checkpoint/ckpt-0";
    }

    public static String getDockerModelDirPath() {
        return "/root/model/temp";
    }

    public static String getDockerExportGraphPath() {
        return "/root/model/fine_tuned_model";
    }

    public static String getDockerModelConfigPath() {
        return "/root/model/model.config";
    }

    public static Bind[] getDockerBinds() {
        Bind dataDir = new Bind(Settings.getTFDataDir(), new Volume("/root/data"));
        Bind modelDir = new Bind(Settings.getTFBaseModelDir(), new Volume("/root/model"));
        return new Bind[] { dataDir, modelDir };
    }
}
