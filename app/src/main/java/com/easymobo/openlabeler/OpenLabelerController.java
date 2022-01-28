/*
 * Copyright (c) 2022. Kin-Hong Wong. All Rights Reserved.
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

package com.easymobo.openlabeler;

import com.easymobo.openlabeler.model.Annotation;
import com.easymobo.openlabeler.model.ObjectModel;
import com.easymobo.openlabeler.preference.PreferencePane;
import com.easymobo.openlabeler.preference.Settings;
import com.easymobo.openlabeler.support.SupportInfoPane;
import com.easymobo.openlabeler.tag.ObjectTag;
import com.easymobo.openlabeler.tag.ShapeItem;
import com.easymobo.openlabeler.tag.TagBoard;
import com.easymobo.openlabeler.tensorflow.TFTrainer;
import com.easymobo.openlabeler.tool.ExportCOCOPane;
import com.easymobo.openlabeler.tool.ExportCreateMLPane;
import com.easymobo.openlabeler.ui.MediaPane;
import com.easymobo.openlabeler.ui.MediaTableView.MediaFile;
import com.easymobo.openlabeler.ui.ObjectTableView;
import com.easymobo.openlabeler.undo.ChangeBase;
import com.easymobo.openlabeler.undo.ListChange;
import com.easymobo.openlabeler.undo.NameChange;
import com.easymobo.openlabeler.undo.ShapeChange;
import com.easymobo.openlabeler.util.AppUtils;
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
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.easymobo.openlabeler.tag.ShapeItem.Type.POLYGON;
import static com.easymobo.openlabeler.tag.ShapeItem.Type.RECTANGLE;
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
    private TagBoard tagBoard;
    @FXML
    private MenuBar menuBar;
    @FXML
    private Menu menuOpenRecent;
    @FXML
    private MenuItem miClose, miSave, msPreference, miPreference, msExit, miExit,
          miUndo, miRedo, miCut, miCopy, miPaste, miDelete,
          miPrevMediaFile, miNextMediaFile, miGoToUnlabeledMediaFile,
          miZoomIn, miZoomOut, miZoomFit, miRotateLeft, miRotateRight, miShowHint, miClearHint,
          miInspectLabels, miExportCOCO,
          msAbout, miAbout;
    @FXML
    private RadioMenuItem miShapeRectangle, miShapePolygon;
    @FXML
    private ToolBar toolBar;
    @FXML
    private Button btnUndo, btnRedo;
    @FXML
    private MediaPane mediaPane;
    @FXML
    private ObjectTableView objectTable;

    private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());
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
            tagBoard.statusProperty().set(bundle.getString("msg.openMedia"));
            jaxbContext = JAXBContext.newInstance(Annotation.class);
        }
        catch (JAXBException ex) {
            LOG.log(Level.SEVERE, "Unable to create JAXBContext", ex);
        }

        // ScrollPane steals focus, so it is always the focus owner
        scrollPane.setOnKeyPressed(event -> tagBoard.onKeyPressed(event));

        // Paste menu item
        miPaste.getParentMenu().setOnShowing(event -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            miPaste.disableProperty().set(!clipboard.getContentTypes().contains(DATA_FORMAT_JAXB));
        });

        // Remove mnemonics from toolbar button tooltips
        fixTooltipMnemonics(toolBar);

        trainer = new TFTrainer();
        trainer.init();

        bindProperties();
    }

    public void handleWindowEvent(WindowEvent event) {
        if (event.getEventType() == WindowEvent.WINDOW_SHOWN) {
            // Initial focus
            tagBoard.requestFocus();

            // Open last media file/folder
            if (Settings.isOpenLastMedia() && Settings.recentFilesProperty.size() > 0) {
                File fileOrDir = new File(Settings.recentFilesProperty.get(0));
                openFileOrDir(fileOrDir);
            }
        }
    }

    @FXML
    private void onFileOpenFile(ActionEvent actionEvent) {
        ExtensionFilter imageFilter = new ExtensionFilter("Image Files (*.jpg, *.png, *.gif)", "*.JPG", "*.jpg",
                "*.JPEG", "*.jpeg", "*.PNG", "*.png", "*.GIF", ".gif");
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(bundle.getString("menu.openMediaFile").replaceAll("_", ""));
        fileChooser.getExtensionFilters().add(imageFilter);
        File file = fileChooser.showOpenDialog(tagBoard.getScene().getWindow());
        openFileOrDir(file);
    }

    @FXML
    private void onFileOpenDir(ActionEvent actionEvent) {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle(bundle.getString("menu.openMediaDir").replaceAll("_", ""));
        File dir = dirChooser.showDialog(tagBoard.getScene().getWindow());
        openFileOrDir(dir);
    }

    private void openFileOrDir(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (tagBoard.getModel() != null && tagBoard.getModel().getFile().equals(file) || !canClose()) {
            return;
        }

        mediaPane.openFileOrDir(file);
    }

    @FXML
    private void onFileMenu(Event event) {
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

    @FXML
    private void onClose(ActionEvent actionEvent) {
        if (canClose()) {
            tagBoard.setModel(null);
            undoManager.forgetHistory();
            mediaPane.clear();
        }
    }

    @FXML
    private void onExit(ActionEvent actionEvent) {
        if (canClose()) {
            close();
            Platform.exit();
        }
    }

    @FXML
    private void onSave(ActionEvent actionEvent) {
        save(true);
    }

    @FXML
    private void onUndo(ActionEvent event) {
        undoManager.undo();
    }

    @FXML
    private void onRedo(ActionEvent event) {
        undoManager.redo();
    }

    @FXML
    private void onShapeMenu() {
        miShapeRectangle.setSelected(Settings.getEditShape() == RECTANGLE);
        miShapePolygon.setSelected(Settings.getEditShape() == POLYGON);
    }

    @FXML
    private void onShapeRectangle(ActionEvent event) {
        Settings.setEditShape(RECTANGLE);
    }

    @FXML
    private void onShapePolygon(ActionEvent event) {
        Settings.setEditShape(ShapeItem.Type.POLYGON);
    }

    @FXML
    private void onCut(ActionEvent event) {
        ObjectTag tag = tagBoard.selectedObjectProperty().get();
        toClipboard(tag.getModel());
        tagBoard.deleteSelected(bundle.getString("menu.cut"));
    }

    @FXML
    private void onCopy(ActionEvent event) {
        ObjectTag tag = tagBoard.selectedObjectProperty().get();
        toClipboard((ObjectModel)tag.getModel().clone());
    }

    @FXML
    private void onPaste(ActionEvent event) {
        ObjectModel model = fromClipboard();
        if (model != null) {
            tagBoard.addObjectTag((ObjectModel)model.clone(), bundle.getString("menu.paste"));
        }
    }

    @FXML
    private void onDelete(ActionEvent event) {
        tagBoard.deleteSelected(bundle.getString("menu.delete"));
    }

    @FXML
    private void onPrevMedia(ActionEvent actionEvent) {
        mediaPane.onPrevMediaFile(actionEvent);
    }

    @FXML
    private void onNextMediaFile(ActionEvent actionEvent) {
        mediaPane.onNextMediaFile(actionEvent);
    }

    @FXML
    private void onGoToUnlabeledMediaFile(ActionEvent actionEvent) {
        mediaPane.onGoToUnlabeledMediaFile(actionEvent);
    }

    @FXML
    private void onZoomIn(ActionEvent actionEvent) {
        tagBoard.getScale().setX(tagBoard.getScale().getX() * 1.1);
        tagBoard.getScale().setY(tagBoard.getScale().getY() * 1.1);
    }

    @FXML
    private void onZoomOut(ActionEvent actionEvent) {
        tagBoard.getScale().setX(tagBoard.getScale().getX() * 0.9);
        tagBoard.getScale().setY(tagBoard.getScale().getY() * 0.9);
    }

    @FXML
    private void onZoomFit(ActionEvent actionEvent) {
        zoomFit();
    }

    /**
     * Sets the scale transform to roughly fit the scroll pane view port
     */
    private void zoomFit() {
        if (tagBoard.getImageView().getImage() == null) {
            return;
        }
        double imgWidth = tagBoard.getImageView().getImage().getWidth();
        double imgHeight = tagBoard.getImageView().getImage().getHeight();
        Bounds bounds = scrollPane.getLayoutBounds();

        // Take into account scrollbar space so that scroll bars will not be shown after fitting
        Set<Node> nodes = scrollPane.lookupAll(".scroll-bar");
        Optional<ScrollBar> sbar = nodes.stream().filter(n -> n instanceof ScrollBar).map(n -> (ScrollBar) n).findFirst();
        double sbSpace = Math.max(sbar.isPresent() ? sbar.get().getWidth() : 30, 30);
        double factor = Math.min(
                Math.round((bounds.getWidth() - sbSpace) * 10.0 / imgWidth) / 10.0,
                Math.round((bounds.getHeight() - sbSpace) * 10.0 / imgHeight) / 10.0);
        tagBoard.getScale().setX(factor);
        tagBoard.getScale().setY(factor);
    }

    @FXML
    private  void onPreference(ActionEvent actionEvent) {
        new PreferencePane().showAndWait();
    }

    @FXML
    private void onRotateLeft(ActionEvent event) {
        rotate(-90);
    }

    @FXML
    private void onRotateRight(ActionEvent event) {
        rotate(90);
    }

    @FXML
    private void rotate(int angle) {
        tagBoard.rotate(angle);
    }

    @FXML
    private void onShowHint(ActionEvent event) {
        tagBoard.showHints();
    }

    @FXML
    private void onClearHint(ActionEvent event) {
        tagBoard.clearHints();
    }

    @FXML
    private void onInspectLabels(ActionEvent event) {

    }

    @FXML
    private void onExportCOCO(ActionEvent event) {
        new ExportCOCOPane().showAndWait(tagBoard.getModel());
    }

    @FXML
    private void onExportCreateML(ActionEvent event) {
        new ExportCreateMLPane().showAndWait(tagBoard.getModel());
    }

    @FXML
    private void onAbout(ActionEvent actionEvent) {
        Stage aboutDialog = OpenLabeler.createAboutStage(bundle);
        aboutDialog.initOwner(tagBoard.getScene().getWindow());
        aboutDialog.showAndWait();
    }

    @FXML
    private void onSupportInfo(ActionEvent actionEvent) {
        new SupportInfoPane().showAndWait();
    }

    private void save(boolean force) {
        if (!force && !Settings.isSaveEveryChange()) {
            return;
        }
        try {
            Annotation model = tagBoard.getModel();
            if (model == null) {
                return;
            }

            Marshaller marshaller = jaxbContext.createMarshaller();

            // output pretty printed
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);

            File xmlFile = AppUtils.getAnnotationFile(model.getFile());
            if (xmlFile.getParentFile() != null) {
                xmlFile.getParentFile().mkdirs();
            }
            xmlFile.createNewFile();
            marshaller.marshal(model, xmlFile);

            mediaPane.updateFileStats();
            tagBoard.statusProperty().set(bundle.getString("msg.saved"));

            if (!Settings.isSaveEveryChange()) {
                undoManager.forgetHistory();
            }
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to save", ex);
        }
    }

    public boolean canClose() {
        if (miSave.isDisable()) {
            return true;
        }
        ButtonType saveAndClose = new ButtonType(bundle.getString("label.saveAndClose"), ButtonData.OTHER);
        Alert alert = AppUtils.createAlert(CONFIRMATION, bundle.getString("label.alert"), bundle.getString("msg.confirmClose"));
        alert.getButtonTypes().clear();
        alert.getButtonTypes().addAll(saveAndClose, ButtonType.YES, ButtonType.NO);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.get() == saveAndClose) {
            save(true);
        }

        return result.get() != ButtonType.NO;
    }

    private void bindProperties() {
        // Files
        boolean loading[] = { false };
        mediaPane.getSelectionModel().selectedItemProperty().addListener((observable, oldFile, newFile) -> {
            if (newFile == null) {
                updateAppTitle(newFile);
                return;
            }
            if (oldFile != null && oldFile.equals(tagBoard.getModel().getFile()) && !canClose()) {
                // Switch back to the previous media selection
                Platform.runLater(() -> mediaPane.getSelectionModel().select(new MediaFile(oldFile)));
                return;
            }
            if (tagBoard.getModel() != null && tagBoard.getModel().getFile().equals(newFile)) {
                return;
            }

            File xmlFile = AppUtils.getAnnotationFile(newFile);
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
                tagBoard.setModel(annotation);
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

        BooleanBinding hasNext = mediaPane.sizeProperty().greaterThan(1).and(mediaPane.getSelectionModel().selectedIndexProperty().lessThan(mediaPane.sizeProperty().subtract(1)));
        miNextMediaFile.disableProperty().bind(hasNext.not());

        // File -> Close
        miClose.disableProperty().bind(tagBoard.modelProperty().isNull());

        // File -> Save
        miSave.disableProperty().bind(Settings.saveEveryChangeProperty.or(miUndo.disableProperty()));

        // Edit -> Undo/Redo
        undoManager = UndoManagerFactory.unlimitedHistorySingleChangeUM(
                merge(changes), // stream of changes to observe
                c -> c.invert(), // function to invert a change
                c -> c.redo(), // function to undo a change
                (c1, c2) -> c1.mergeWith(c2)); // function to merge two changes

        changes.add(changesOf(tagBoard.objectsProperty().get()).map(c -> new ListChange(tagBoard.objectsProperty().get(), c)));

        tagBoard.objectsProperty().addListener((Change<? extends ObjectTag> change) -> {
            if (!change.next()) {
                return;
            }
            if (change.wasAdded()) {
                change.getAddedSubList().forEach(target -> {
                    EventStream<ChangeBase<?>> es = changesOf(target.nameProperty()).map(c -> new NameChange(bundle.getString("menu.editName"), target.nameProperty(), c));
                    target.getProperties().put("EventStreamName", es);
                    target.nameProperty().addListener((observable, oldValue, newValue) -> save(false));
                    changes.add(es);

                    es = changesOf(target.shapeItemProperty()).map(c -> new ShapeChange(bundle.getString("menu.changeShape"), target.shapeItemProperty(), c));
                    target.getProperties().put("EventStreamBounds", es);
                    target.shapeItemProperty().addListener((observable, oldValue, newValue) -> save(false));
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

        miRedo.disableProperty().bind(undoManager.redoAvailableProperty().map(x -> !x));

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
        miCut.disableProperty().bind(tagBoard.selectedObjectProperty().isNull());

        // Edit -> Copy
        miCopy.disableProperty().bind(tagBoard.selectedObjectProperty().isNull());

        // Edit -> Delete
        miDelete.disableProperty().bind(tagBoard.selectedObjectProperty().isNull());

        miGoToUnlabeledMediaFile.disableProperty().bind(mediaPane.nextUnlabeledMediaProperty().isNull());

        // Link object table and object group
        objectTable.setItems(tagBoard.objectsProperty());
        tagBoard.selectedObjectProperty().addListener((observable, oldValue, newValue) -> {
            objectTable.getSelectionModel().select(newValue);
        });
        objectTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                newValue.setSelected(true);
            }
        });

        // View -> Zoom
        miZoomIn.disableProperty().bind(tagBoard.modelProperty().isNull());
        miZoomOut.disableProperty().bind(tagBoard.modelProperty().isNull());
        miZoomFit.disableProperty().bind(tagBoard.modelProperty().isNull());

        // View -> Rotate
        miRotateLeft.disableProperty().bind(tagBoard.modelProperty().isNull());
        miRotateRight.disableProperty().bind(tagBoard.modelProperty().isNull());

        // View -> Show/Clear Hints
        ObservableValue<Long> visibleHints = EasyBind.combine(
                EasyBind.map(tagBoard.hintsProperty().get(), t -> t.visibleProperty()),
                stream -> stream.filter(visible -> visible).count());
        LongBinding vhBinding = Bindings.createLongBinding(() -> ((MonadicBinding<Long>)visibleHints).get(), visibleHints);
        BooleanBinding canShowHint = tagBoard.hintsProperty().sizeProperty().greaterThan(0).and(vhBinding.lessThan(tagBoard.hintsProperty().sizeProperty()));
        miShowHint.disableProperty().bind(canShowHint.not());

        BooleanBinding canClearHint = vhBinding.greaterThan(0);
        miClearHint.disableProperty().bind(canClearHint.not());

        // Tools -> Export COCO
        miExportCOCO.disableProperty().bind(tagBoard.modelProperty().isNull());

        // Status bar
        tagBoard.statusProperty().addListener((observable, oldValue, newValue) -> status.setText(newValue));
        trainer.checkpointProperty().addListener((observable, oldValue, newValue) -> {
            status.setText(MessageFormat.format(bundle.getString("msg.ckptCreated"), newValue));
        });
        coords.textProperty().bind(tagBoard.tagCoordsProperty());
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
        ((Stage) tagBoard.getScene().getWindow()).setTitle(title);
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
        tagBoard.close();
        mediaPane.close();
    }
}
