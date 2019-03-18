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

import com.easymobo.openlabeler.tensorflow.TFTrainer;
import com.easymobo.openlabeler.ui.InputFileChooser;
import com.easymobo.openlabeler.util.Util;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.controlsfx.tools.Borders;
import org.fxmisc.easybind.EasyBind;

import java.io.IOException;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TrainingPane extends VBox implements Category
{
    @FXML
    private GridPane gpTrainingData, gpModel, gpDocker;
    @FXML
    private InputFileChooser txtTFImageDir, txtTFAnnotationDir, txtTFDataDir, txtTFBaseModelDir;
    @FXML
    private TextField txtDockerImage, txtContainerHostName, txtContainerName;
    @FXML
    private LabelMapPane labelMapPane;
    @FXML
    private Label labelNumSamples, labelModelType, labelTrainCkpt;
    @FXML
    private HBox boxTrain;

    private static final Logger LOG = Logger.getLogger(TrainingPane.class.getCanonicalName());

    private final BooleanProperty dirtyProperty =  new SimpleBooleanProperty(false);
    private String name;
    private ResourceBundle bundle;

    private TFTrainer.PipelineConfig config;

    public TrainingPane(String name, ResourceBundle bundle) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/TrainingPane.fxml"), bundle);
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
            getChildren().addAll(
                    Borders.wrap(gpTrainingData).lineBorder().title(bundle.getString("menu.trainingData")).buildAll(),
                    Borders.wrap(gpModel).lineBorder().title(bundle.getString("menu.model")).buildAll(),
                    Borders.wrap(gpDocker).lineBorder().title(bundle.getString("menu.docker")).buildAll()
            );

        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to load FXML", ex);
        }
        this.name = name;
        this.bundle = bundle;

        bindProperties();

        load();
    }

    @Override
    public BooleanProperty dirtyProperty() {
        return dirtyProperty;
    }
    public boolean isDirty() {
        if (!TFTrainer.getLabelMapPath(txtTFDataDir.getText()).toFile().exists()) {
            return true;
        }
        if (!TFTrainer.getModelConfigPath(txtTFBaseModelDir.getText()).toFile().exists()) {
            return true;
        }
        return dirtyProperty.get();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void load() {
        txtTFImageDir.setText(Settings.getTFImageDir());
        txtTFAnnotationDir.setText(Settings.getTFAnnotationDir());
        txtTFDataDir.setText(Settings.getTFDataDir());
        txtTFBaseModelDir.setText(Settings.getTFBaseModelDir());
        txtDockerImage.setText(Settings.getDockerImage());
        txtContainerHostName.setText(Settings.getContainerHostName());
        txtContainerName.setText(Settings.getContainerName());

        updateTraining();
    }

    @Override
    public void save() {
        if (!isDirty()) {
            return;
        }
        Settings.setTFImageDir(txtTFImageDir.getText());
        Settings.setTFAnnotationDir(txtTFAnnotationDir.getText());
        Settings.setTFDataDir(txtTFDataDir.getText());
        Settings.setTFBaseModelDir(txtTFBaseModelDir.getText());
        Settings.setDockerImage(txtDockerImage.getText());
        Settings.setContainerHostName(txtContainerHostName.getText());
        Settings.setContainerName(txtContainerName.getText());
        try {
            TFTrainer.saveLabelMap(labelMapPane.getItems(), txtTFDataDir.getText());
            config.save();
        }
        catch (IOException ex) {
            LOG.log(Level.SEVERE, "Unable to save changes", ex);
        }
    }

    private void bindProperties() {
        // Update on any directory changes
        txtTFImageDir.textProperty().addListener((observable, oldValue, newValue) -> updateNumSamples());
        txtTFAnnotationDir.textProperty().addListener((observable, oldValue, newValue) -> updateNumSamples());
        txtTFDataDir.textProperty().addListener((observable, oldValue, newValue) -> updateLabelMap());
        txtTFBaseModelDir.textProperty().addListener((observable, oldValue, newValue) -> updateTraining());

        BooleanBinding changes[] = {
                txtTFImageDir.textProperty().isNotEqualTo(Settings.tfImageDirProperty),
                txtTFAnnotationDir.textProperty().isNotEqualTo(Settings.tfAnnotationDirProperty),
                txtTFDataDir.textProperty().isNotEqualTo(Settings.tfDataDirProperty),
                new SimpleListProperty(labelMapPane.getItems()).isNotEqualTo(
                        FXCollections.observableList(TFTrainer.getLabelMapItems(txtTFDataDir.getText()))),
                txtTFBaseModelDir.textProperty().isNotEqualTo(Settings.tfBaseModelDirProperty),
                txtDockerImage.textProperty().isNotEqualTo(Settings.dockerImageProperty),
                txtContainerHostName.textProperty().isNotEqualTo(Settings.containerHostNameProperty),
                txtContainerName.textProperty().isNotEqualTo(Settings.containerNameProperty),
        };
        dirtyProperty.unbind();
        dirtyProperty.bind(EasyBind.combine(
                FXCollections.observableArrayList(changes), stream -> stream.reduce((a, b) -> a | b).orElse(false)));
    }

    private void updateNumSamples() {
        int num = 0;
        if (Paths.get(txtTFImageDir.getText()).toFile().exists()
                && Paths.get(txtTFAnnotationDir.getText()).toFile().exists()) {
            num = Paths.get(txtTFAnnotationDir.getText()).toFile().listFiles((dir, name) -> {
                name = name.toLowerCase();
                return name.endsWith(".xml");
            }).length;
        }
        labelNumSamples.setText(String.valueOf(num));
    }

    private void updateLabelMap() {
        labelMapPane.getItems().clear();
        labelMapPane.getItems().addAll(TFTrainer.getLabelMapItems(txtTFDataDir.getText()));
        bindProperties();
    }

    private void updateTraining() {
        // Training controls
        String baseModelDir = txtTFBaseModelDir.getText();

        config = new TFTrainer.PipelineConfig(baseModelDir);
        labelModelType.setText(config.getType() == null ? bundle.getString("msg.notFound") : config.getType());

        List<Button> buttons = new ArrayList();

        int checkpoint = TFTrainer.getTrainCkpt(baseModelDir);
        labelTrainCkpt.setText(checkpoint < 0 ? bundle.getString("msg.notFound") : String.valueOf(checkpoint));
        if (checkpoint > 0) {
            Button btn = new Button(bundle.getString("menu.exportGraph"));
            btn.setOnAction(event -> exportGraph(btn, checkpoint));
            buttons.add(btn);
        }

        boolean canTrain = TFTrainer.canTrain(txtTFDataDir.getText(), txtTFBaseModelDir.getText());
        if (canTrain) {
            if (TFTrainer.isTraining()) {
                Button btn = new Button(bundle.getString("menu.stopTrain"));
                btn.setOnAction(event -> train(false, false));
                buttons.add(btn);
            }
            else {
                Button btn1 = new Button(bundle.getString("menu.continueTrain"));
                btn1.setOnAction(event -> train(true, false));
                Button btn2 = new Button(bundle.getString("menu.restartTrain"));
                btn2.setOnAction(event -> train(true, true));
                buttons.addAll(Arrays.asList(btn1, btn2));
            }
        }
        else {
            Button btn = new Button(bundle.getString("menu.startTrain"));
            btn.setOnAction(event -> train(true, true));
            btn.disableProperty().bind(Bindings.or(txtTFDataDir.textProperty().isEmpty(), Bindings.or(txtTFImageDir.textProperty().isEmpty(), txtTFAnnotationDir.textProperty().isEmpty())));
            buttons.add(btn);
        }
        boxTrain.getChildren().clear();
        boxTrain.getChildren().addAll(buttons);
    }

    private void exportGraph(Button source, int checkpoint) {
        save();
        TFTrainer.exportGraph(checkpoint);
        Util.showInformation(bundle.getString("menu.alert"), MessageFormat.format(bundle.getString("msg.exportGraph"), checkpoint));
        source.setDisable(true);
    }

    private void train(boolean start, boolean restart) {
        save();
        TFTrainer.train(start, restart);
        Util.showInformation(bundle.getString("menu.alert"),
                bundle.getString(start ? (restart ? "msg.startTrain" : "msg.continueTrain") : "msg.stopTrain"));
        updateTraining();
    }
}
