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

package com.easymobo.openlabeler;

import com.easymobo.openlabeler.model.Annotation;
import com.easymobo.openlabeler.model.ObjectModel;
import com.easymobo.openlabeler.preference.PreferencePane;
import com.easymobo.openlabeler.preference.Settings;
import com.easymobo.openlabeler.support.SupportInfoPane;
import com.easymobo.openlabeler.tensorflow.TFTrainer;
import com.easymobo.openlabeler.ui.MediaPane;
import com.easymobo.openlabeler.ui.MediaTableView.MediaFile;
import com.easymobo.openlabeler.ui.ObjectTableView;
import com.easymobo.openlabeler.ui.ObjectTag;
import com.easymobo.openlabeler.ui.TagGroup;
import com.easymobo.openlabeler.undo.BoundsChange;
import com.easymobo.openlabeler.undo.ChangeBase;
import com.easymobo.openlabeler.undo.ListChange;
import com.easymobo.openlabeler.undo.NameChange;
import com.easymobo.openlabeler.util.Util;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.LongBinding;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableSet;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.apache.commons.lang3.SystemUtils;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.monadic.MonadicBinding;
import org.fxmisc.undo.UndoManager;
import org.fxmisc.undo.UndoManagerFactory;
import org.reactfx.EventStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static javafx.scene.control.Alert.AlertType.CONFIRMATION;
import static org.reactfx.EventStreams.changesOf;
import static org.reactfx.EventStreams.merge;

public class OpenLabelerController implements Initializable, AutoCloseable
{
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private Label status, coords;
    @FXML
    private TagGroup tagGroup;
    @FXML
    private MenuBar menuBar;
    @FXML
    private Menu menuOpenRecent;
    @FXML
    private MenuItem miClose, miSave, msPreference, miPreference, msExit, miExit, miUndo, miRedo, miCut, miCopy, miPaste, miDelete, miZoomIn, miZoomOut, miZoomFit, miPrevMediaFile, miNextMediaFile, miGoToUnlabeledMediaFile, miRotateLeft, miRotateRight, miShowHint, miClearHint, msAbout, miAbout, miInspectLabels;
    @FXML
    private ToolBar toolBar;
    @FXML
    private Button btnPrevMedia, btnNextMedia, btnSave, btnUndo, btnRedo, btnDelete, btnZoomIn, btnZoomOut, btnZoomFit, btnRotateLeft, btnRotateRight, btnShowHint, btnClearHint;
    @FXML
    private MediaPane mediaPane;
    @FXML
    private ObjectTableView objectTable;

    private static final Logger LOG = Logger.getLogger(OpenLabelerController.class.getCanonicalName());
    private static final DataFormat DATA_FORMAT_JAXB = new DataFormat("application/openlabeler-jaxb");

    private ResourceBundle bundle;

    // For PASCAL VOC xml persistence
    private JAXBContext jaxbContext;

    // undo/redo
    private ObservableSet<EventStream<ChangeBase<?>>> changes = FXCollections.observableSet();
    private UndoManager<ChangeBase<?>> undoManager;

    // TensorFlow training
    TFTrainer trainer;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Platform dependent menu adjustments
        menuBar.setUseSystemMenuBar(true);
        if (SystemUtils.IS_OS_MAC) {
            msPreference.setVisible(false);
            miPreference.setVisible(false);
            msExit.setVisible(false);
            miExit.setVisible(false);
            msAbout.setVisible(false);
            miAbout.setVisible(false);
        }

        try {
            bundle = resources;
            tagGroup.statusProperty().set(bundle.getString("msg.openMedia"));
            jaxbContext = JAXBContext.newInstance(Annotation.class);
        }
        catch (JAXBException ex) {
            LOG.log(Level.SEVERE, "Unable to create JAXBContext", ex);
        }

        // ScrollPane steals focus, so it is always the focus owner
        scrollPane.setOnKeyPressed(event -> tagGroup.onKeyPressed(event));

        // Paste menu item
        miPaste.getParentMenu().setOnShowing(event -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            miPaste.disableProperty().set(!clipboard.getContentTypes().contains(DATA_FORMAT_JAXB));
        });

        // Remove mnemonics from toolbar button tooltips
        fixTooltipMnemonics(toolBar);

        trainer = new TFTrainer(bundle);
        trainer.init();

        bindProperties();
    }

    public void handleWindowEvent(WindowEvent event) {
        if (event.getEventType() == WindowEvent.WINDOW_SHOWN) {
            // Initial focus
            tagGroup.requestFocus();

            // Open last media file/folder
            if (Settings.isOpenLastMedia() && Settings.recentFilesProperty.size() > 0) {
                File fileOrDir = new File(Settings.recentFilesProperty.get(0));
                openFileOrDir(fileOrDir);
            }
        }
    }

    public void onFileOpenFile(ActionEvent actionEvent) {
        ExtensionFilter imageFilter = new ExtensionFilter("Image Files (*.jpg, *.png, *.gif)", "*.JPG", "*.jpg",
                "*.JPEG", "*.jpeg", "*.PNG", "*.png", "*.GIF", ".gif");
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(bundle.getString("menu.openMediaFile").replaceAll("_", ""));
        fileChooser.getExtensionFilters().add(imageFilter);
        File file = fileChooser.showOpenDialog(tagGroup.getScene().getWindow());
        openFileOrDir(file);
    }

    public void onFileOpenDir(ActionEvent actionEvent) {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle(bundle.getString("menu.openMediaDir").replaceAll("_", ""));
        File dir = dirChooser.showDialog(tagGroup.getScene().getWindow());
        openFileOrDir(dir);
    }

    private void openFileOrDir(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (tagGroup.getModel() != null && tagGroup.getModel().getFile().equals(file) || !canClose()) {
            return;
        }

        mediaPane.openFileOrDir(file);
    }

    public void onFileMenu(Event event) {
        menuOpenRecent.getItems().clear();
        for (Iterator<String> it = Settings.recentFilesProperty.iterator(); it.hasNext(); ) {
            File fileOrDir = new File(it.next());
            MenuItem item = new MenuItem(fileOrDir.getAbsolutePath());
            item.setOnAction(value -> openFileOrDir(fileOrDir));
            menuOpenRecent.getItems().add(item);
        }
        // Clear menu item
        if (menuOpenRecent.getItems().size() > 0) {
            menuOpenRecent.getItems().add(new SeparatorMenuItem());
            MenuItem clear = new MenuItem(bundle.getString("menu.clear"));
            clear.setOnAction(value -> Settings.recentFilesProperty.clear());
            menuOpenRecent.getItems().add(clear);
        }
        menuOpenRecent.setDisable(menuOpenRecent.getItems().size() <= 0);
    }

    public void onClose(ActionEvent actionEvent) {
        if (canClose()) {
            tagGroup.setModel(null);
            undoManager.forgetHistory();
            mediaPane.clear();
        }
    }

    public void onExit(ActionEvent actionEvent) {
        if (canClose()) {
            close();
            Platform.exit();
        }
    }

    public void onSave(ActionEvent actionEvent) {
        save(true);
    }

    public void onUndo(ActionEvent event) {
        undoManager.undo();
    }

    public void onRedo(ActionEvent event) {
        undoManager.redo();
    }

    public void onCut(ActionEvent event) {
        ObjectTag tag = tagGroup.selectedObjectProperty().get();
        toClipboard(tag.getModel());
        tagGroup.deleteSelected(bundle.getString("menu.cut"));
    }

    public void onCopy(ActionEvent event) {
        ObjectTag tag = tagGroup.selectedObjectProperty().get();
        toClipboard((ObjectModel)tag.getModel().clone());
    }

    public void onPaste(ActionEvent event) {
        ObjectModel model = fromClipboard();
        if (model != null) {
            tagGroup.addObjectTag((ObjectModel)model.clone(), bundle.getString("menu.paste"));
        }
    }

    public void onDelete(ActionEvent event) {
        tagGroup.deleteSelected(bundle.getString("menu.delete"));
    }

    public void onPrevMedia(ActionEvent actionEvent) {
        mediaPane.onPrevMediaFile(actionEvent);
    }

    public void onNextMediaFile(ActionEvent actionEvent) {
        mediaPane.onNextMediaFile(actionEvent);
    }

    public void onGoToUnlabeledMediaFile(ActionEvent actionEvent) {
        mediaPane.onGoToUnlabeledMediaFile(actionEvent);
    }

    public void onZoomIn(ActionEvent actionEvent) {
        tagGroup.getScale().setX(tagGroup.getScale().getX() * 1.1);
        tagGroup.getScale().setY(tagGroup.getScale().getY() * 1.1);
    }

    public void onZoomOut(ActionEvent actionEvent) {
        tagGroup.getScale().setX(tagGroup.getScale().getX() * 0.9);
        tagGroup.getScale().setY(tagGroup.getScale().getY() * 0.9);
    }

    public void onZoomFit(ActionEvent actionEvent) {
        zoomFit();
    }

    /**
     * Sets the scale transform to roughly fit the scroll pane view port
     */
    public void zoomFit() {
        if (tagGroup.getImageView().getImage() == null) {
            return;
        }
        double imgWidth = tagGroup.getImageView().getImage().getWidth();
        double imgHeight = tagGroup.getImageView().getImage().getHeight();
        Bounds bounds = scrollPane.getLayoutBounds();

        // Take into account scrollbar space so that scroll bars will not be shown after fitting
        Set<Node> nodes = scrollPane.lookupAll(".scroll-bar");
        Optional<ScrollBar> sbar = nodes.stream().filter(n -> n instanceof ScrollBar).map(n -> (ScrollBar) n).findFirst();
        double sbSpace = Math.max(sbar.isPresent() ? sbar.get().getWidth() : 30, 30);
        double factor = Math.min(
                Math.round((bounds.getWidth() - sbSpace) * 10.0 / imgWidth) / 10.0,
                Math.round((bounds.getHeight() - sbSpace) * 10.0 / imgHeight) / 10.0);
        tagGroup.getScale().setX(factor);
        tagGroup.getScale().setY(factor);
    }

    public void onPreference(ActionEvent actionEvent) {
        new PreferencePane(bundle).showAndWait();
    }

    public void onRotateLeft(ActionEvent event) {
        rotate(-90);
    }

    public void onRotateRight(ActionEvent event) {
        rotate(90);
    }

    private void rotate(int angle) {
        tagGroup.rotate(angle);
    }

    public void onShowHint(ActionEvent event) {
        tagGroup.showHints();
    }

    public void onClearHint(ActionEvent event) {
        tagGroup.clearHints();
    }

    public void onInspectLabels(ActionEvent event) {

    }

    public void onAbout(ActionEvent actionEvent) {
        Stage aboutDialog = OpenLabeler.createAboutStage(bundle);
        aboutDialog.initOwner(tagGroup.getScene().getWindow());
        aboutDialog.showAndWait();
    }

    public void onSupportInfo(ActionEvent actionEvent) {
        new SupportInfoPane(bundle).showAndWait();
    }

    private void save(boolean force) {
        if (!force && !Settings.isSaveEveryChange()) {
            return;
        }
        try {
            Annotation model = tagGroup.getModel();
            if (model == null) {
                return;
            }

            Marshaller marshaller = jaxbContext.createMarshaller();

            // output pretty printed
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);

            File xmlFile = Util.getAnnotationFile(model.getFile());
            if (xmlFile.getParentFile() != null) {
                xmlFile.getParentFile().mkdirs();
            }
            xmlFile.createNewFile();
            marshaller.marshal(model, xmlFile);

            mediaPane.updateFileStats();
            tagGroup.statusProperty().set(bundle.getString("msg.saved"));

            if (!Settings.isSaveEveryChange()) {
                undoManager.forgetHistory();
            }
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to save", ex);
        }
    }

    public boolean canClose() {
        if (btnSave.isDisabled()) {
            return true;
        }
        ButtonType saveAndClose = new ButtonType(bundle.getString("menu.saveAndClose"), ButtonData.OTHER);
        Alert alert = Util.createAlert(CONFIRMATION, bundle.getString("menu.alert"), bundle.getString("msg.confirmClose"));
        alert.getButtonTypes().clear();
        alert.getButtonTypes().addAll(saveAndClose, ButtonType.OK, ButtonType.CANCEL);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.get() == saveAndClose) {
            save(true);
        }

        return result.get() != ButtonType.CANCEL;
    }

    private void bindProperties() {
        // Files
        boolean loading[] = { false };
        mediaPane.getSelectionModel().selectedItemProperty().addListener((observable, oldFile, newFile) -> {
            if (newFile == null) {
                updateAppTitle(newFile);
                return;
            }
            if (oldFile != null && oldFile.equals(tagGroup.getModel().getFile()) && !canClose()) {
                // Switch back to the previous media selection
                Platform.runLater(() -> mediaPane.getSelectionModel().select(new MediaFile(oldFile)));
                return;
            }
            if (tagGroup.getModel() != null && tagGroup.getModel().getFile().equals(newFile)) {
                return;
            }

            File xmlFile = Util.getAnnotationFile(newFile);
            Annotation annotation = null;
            try {
                Image image = new Image(newFile.toURI().toURL().toExternalForm());
                if (xmlFile.exists()) {
                    Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
                    annotation = (Annotation) unmarshaller.unmarshal(xmlFile);
                    annotation.setFile(newFile);
                    annotation.getSize().setImage(image);
                }
                if (annotation == null) {
                    annotation = new Annotation(newFile, image);
                }

                loading[0] =  true;
                tagGroup.setModel(annotation);
                zoomFit();

                undoManager.forgetHistory();

                updateAppTitle(newFile);
            }
            catch (Exception ex) {
                LOG.log(Level.SEVERE, "Unable to load", ex);
            }
            finally {
                loading[0] = false;
            }
        });

        BooleanBinding hasPrev = mediaPane.sizeProperty().greaterThan(1).and(mediaPane.getSelectionModel().selectedIndexProperty().greaterThan(0));
        miPrevMediaFile.disableProperty().bind(hasPrev.not());
        btnPrevMedia.disableProperty().bind(miPrevMediaFile.disableProperty());

        BooleanBinding hasNext = mediaPane.sizeProperty().greaterThan(1).and(mediaPane.getSelectionModel().selectedIndexProperty().lessThan(mediaPane.sizeProperty().subtract(1)));
        miNextMediaFile.disableProperty().bind(hasNext.not());
        btnNextMedia.disableProperty().bind(miNextMediaFile.disableProperty());

        // File -> Close
        miClose.disableProperty().bind(tagGroup.modelProperty().isNull());

        // File -> Save
        miSave.disableProperty().bind(Settings.saveEveryChangeProperty.or(miUndo.disableProperty()));
        btnSave.disableProperty().bind(miSave.disableProperty());

        // Edit -> Undo/Redo
        undoManager = UndoManagerFactory.unlimitedHistorySingleChangeUM(
                merge(changes), // stream of changes to observe
                c -> c.invert(), // function to invert a change
                c -> c.redo(), // function to undo a change
                (c1, c2) -> c1.mergeWith(c2)); // function to merge two changes

        changes.add(changesOf(tagGroup.objectsProperty().get()).map(c -> new ListChange(tagGroup.objectsProperty().get(), c)));

        tagGroup.objectsProperty().addListener((Change<? extends ObjectTag> change) -> {
            if (!change.next()) {
                return;
            }
            if (change.wasAdded()) {
                change.getAddedSubList().forEach(target -> {
                    EventStream<ChangeBase<?>> es = changesOf(target.nameProperty()).map(c -> new NameChange(bundle.getString("menu.editName"), target.nameProperty(), c));
                    target.getProperties().put("EventStreamName", es);
                    target.nameProperty().addListener((observable, oldValue, newValue) -> save(false));
                    changes.add(es);

                    es = changesOf(target.boundsProperty()).map(c -> new BoundsChange(bundle.getString("menu.changeBndBox"), target.boundsProperty(), c));
                    target.getProperties().put("EventStreamBounds", es);
                    target.boundsProperty().addListener((observable, oldValue, newValue) -> save(false));
                    changes.add(es);
                });
            }
            else if (change.wasRemoved()) {
                change.getRemoved().forEach(target -> {
                    changes.remove(target.getProperties().get("EventStreamName"));
                    changes.remove(target.getProperties().get("EventStreamBounds"));
                });
            }
            if (!loading[0]) {
                save(false);
            }
        });

        miUndo.disableProperty().bind(undoManager.undoAvailableProperty().map(x -> !x));
        btnUndo.disableProperty().bind(miUndo.disableProperty());

        miRedo.disableProperty().bind(undoManager.redoAvailableProperty().map(x -> !x));
        btnRedo.disableProperty().bind(miRedo.disableProperty());

        // update Undo/Redo menu item and tooltip text
        undoManager.nextUndoProperty().addListener((observable, oldValue, newValue) -> {
            String name = newValue == null ? "" : newValue.getName();
            String msg = MessageFormat.format(bundle.getString("menu.undoAction"), name);
            miUndo.setText(msg);
            btnUndo.getTooltip().setText(msg);
        });
        undoManager.nextRedoProperty().addListener((observable, oldValue, newValue) -> {
            String name = newValue == null ? "" : newValue.getName();
            String msg = MessageFormat.format(bundle.getString("menu.redoAction"), name);
            miRedo.setText(msg);
            btnRedo.getTooltip().setText(msg);
        });

        // Edit -> Cut
        miCut.disableProperty().bind(tagGroup.selectedObjectProperty().isNull());

        // Edit -> Copy
        miCopy.disableProperty().bind(tagGroup.selectedObjectProperty().isNull());

        // Edit -> Delete
        miDelete.disableProperty().bind(tagGroup.selectedObjectProperty().isNull());
        btnDelete.disableProperty().bind(miDelete.disableProperty());

        miGoToUnlabeledMediaFile.disableProperty().bind(mediaPane.nextUnlabeledMediaProperty().isNull());

        // Link object table and object group
        objectTable.setItems(tagGroup.objectsProperty());
        tagGroup.selectedObjectProperty().addListener((observable, oldValue, newValue) -> {
            objectTable.getSelectionModel().select(newValue);
        });
        objectTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                newValue.setSelected(true);
            }
        });

        // View -> Zoom
        miZoomIn.disableProperty().bind(tagGroup.modelProperty().isNull());
        btnZoomIn.disableProperty().bind(miZoomIn.disableProperty());
        miZoomOut.disableProperty().bind(tagGroup.modelProperty().isNull());
        btnZoomOut.disableProperty().bind(miZoomOut.disableProperty());
        miZoomFit.disableProperty().bind(tagGroup.modelProperty().isNull());
        btnZoomFit.disableProperty().bind(miZoomFit.disableProperty());

        // View -> Rotate
        miRotateLeft.disableProperty().bind(tagGroup.modelProperty().isNull());
        btnRotateLeft.disableProperty().bind(miRotateLeft.disableProperty());
        miRotateRight.disableProperty().bind(tagGroup.modelProperty().isNull());
        btnRotateRight.disableProperty().bind(miRotateRight.disableProperty());

        // View -> Show/Clear Hints
        ObservableValue<Long> visibleHints = EasyBind.combine(
                EasyBind.map(tagGroup.hintsProperty().get(), t -> t.visibleProperty()),
                stream -> stream.filter(visible -> visible).count());
        LongBinding vhBinding = Bindings.createLongBinding(() -> ((MonadicBinding<Long>)visibleHints).get(), visibleHints);
        BooleanBinding canShowHint = tagGroup.hintsProperty().sizeProperty().greaterThan(0).and(vhBinding.lessThan(tagGroup.hintsProperty().sizeProperty()));
        miShowHint.disableProperty().bind(canShowHint.not());
        btnShowHint.disableProperty().bind(canShowHint.not());

        BooleanBinding canClearHint = vhBinding.greaterThan(0);
        miClearHint.disableProperty().bind(canClearHint.not());
        btnClearHint.disableProperty().bind(canClearHint.not());

        // Status bar
        tagGroup.statusProperty().addListener((observable, oldValue, newValue) -> status.setText(newValue));
        trainer.checkpointProperty().addListener((observable, oldValue, newValue) -> {
            status.setText(MessageFormat.format(bundle.getString("msg.ckptCreated"), newValue));
        });
        coords.textProperty().bind(tagGroup.tagCoordsProperty());
    }

    private void toClipboard(ObjectModel model) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        Map<DataFormat, Object> content = new HashMap();
        try {
            Marshaller marshaller = jaxbContext.createMarshaller();
            StringWriter writer = new StringWriter();
            // output pretty printed
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);

            marshaller.marshal(model, writer);
            content.put(DATA_FORMAT_JAXB, writer.toString());
            content.put(DataFormat.PLAIN_TEXT, writer.toString());
            clipboard.setContent(content);
        }
        catch (Exception ex) {
            LOG.log(Level.WARNING, "Unable to put content to clipboard", ex);
        }
    }

    private ObjectModel fromClipboard() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.getContentTypes().contains(DATA_FORMAT_JAXB)) {
            try {
                String content = (String)clipboard.getContent(DATA_FORMAT_JAXB);
                Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
                return (ObjectModel) unmarshaller.unmarshal(new StringReader(content));
            }
            catch (Exception ex) {
                LOG.log(Level.SEVERE, "Unable to get content from clipboard", ex);
            }
        }
        return null;
    }

    private void updateAppTitle(File file) {
        String title = bundle.getString("app.name");
        if (file != null && file.exists()) {
            title += " - " + file.getAbsolutePath();
        }
        ((Stage)tagGroup.getScene().getWindow()).setTitle(title);
    }

    private void fixTooltipMnemonics(ToolBar toolBar) {
        toolBar.getItems().stream().filter(Button.class::isInstance).forEach(node -> {
            Tooltip tooltip = ((Button) node).getTooltip();
            if (tooltip != null) {
                tooltip.setText(tooltip.getText().replaceAll("_", ""));
            }
        });
    }

    /**
     * Clean up
     */
    public void close() {
        trainer.close();
        tagGroup.close();
        mediaPane.close();
    }
}
