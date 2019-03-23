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

import com.easymobo.openlabeler.model.Annotation;
import com.easymobo.openlabeler.model.BoundBox;
import com.easymobo.openlabeler.model.HintModel;
import com.easymobo.openlabeler.model.ObjectModel;
import com.easymobo.openlabeler.preference.Settings;
import com.easymobo.openlabeler.tensorflow.ObjectDetector;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.*;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.ImageView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.TransformChangedEvent;
import javafx.scene.transform.Translate;

import java.text.MessageFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TagGroup extends Group implements AutoCloseable
{
    @FXML
    private StackPane paddingPane;
    @FXML
    private ImageView imageView;
    @FXML
    private Canvas canvas;

    private static final Logger LOG = Logger.getLogger(TagGroup.class.getCanonicalName());
    private static final int PADDING = 10;

    private ResourceBundle bundle;
    private Translate translate;
    private Scale scale;
    private Rotate rotate;
    private ContextMenu contextMenu;
    private ObjectDetector objectDetector;
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // Root model
    private ObjectProperty<Annotation> modelProperty;

    private ListProperty<ObjectTag> objectsProperty = new SimpleListProperty(FXCollections.observableArrayList());
    public ListProperty<ObjectTag> objectsProperty() {
        return objectsProperty;
    }

    private ObjectProperty<ObjectTag> selectedObjectProperty = new SimpleObjectProperty<>();
    public ObjectProperty<ObjectTag> selectedObjectProperty() {
        return selectedObjectProperty;
    }

    private ListProperty<HintTag> hintsProperty = new SimpleListProperty(FXCollections.observableArrayList());
    public ListProperty<HintTag> hintsProperty() {
        return hintsProperty;
    }

    private SimpleStringProperty tagCoordsProperty = new SimpleStringProperty();
    public StringProperty tagCoordsProperty() {
        return tagCoordsProperty;
    }

    private SimpleStringProperty statusProperty = new SimpleStringProperty();
    public StringProperty statusProperty() {
        return statusProperty;
    }

    public TagGroup() {
        bundle = ResourceBundle.getBundle("bundle");
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/TagGroup.fxml"), bundle);
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to load FXML", ex);
        }

        paddingPane.setOnMousePressed(event -> onMousePressed(event));
        paddingPane.setOnMouseDragged(event -> onMouseDragged(event));
        paddingPane.setOnMouseReleased(event -> onMouseReleased(event));

        // context menu
        addEventFilter(ContextMenuEvent.ANY, event -> onContextMenuEvent(event));

        // add/remove ObjectTag
        objectsProperty().addListener((ListChangeListener<ObjectTag>) c -> {
            if (!c.next()) {
                return;
            }
            Annotation model = getModel();
            if (c.wasAdded()) {
                int offset = getChildren().indexOf(paddingPane) + 1;
                c.getAddedSubList().forEach(objectTag -> {
                    getChildren().add(offset + c.getFrom(), objectTag);
                    if (model != null && !model.getObjects().contains(objectTag.getModel())) {
                        model.getObjects().add(objectTag.getModel());
                    }
                });
            }
            else if (c.wasRemoved()) {
                c.getRemoved().forEach(objectTag -> {
                    getChildren().remove(objectTag);
                    if (model != null && model.getObjects().contains(objectTag.getModel())) {
                        model.getObjects().remove(objectTag.getModel());
                    }
                });
            }
        });

        // show/clear HintTag
        hintsProperty().addListener((ListChangeListener<HintTag>) c -> {
            if (!c.next()) {
                return;
            }
            if (c.wasAdded()) {
                c.getAddedSubList().forEach(hintTag -> {
                    hintTag.setVisible(false);
                    getChildren().add(hintTag);
                });
            }
            else if (c.wasRemoved()) {
                c.getRemoved().forEach(hintTag -> {
                    hintTag.setVisible(false);
                    getChildren().remove(hintTag);
                });
            }
        });

        objectDetector = new ObjectDetector(bundle);
        new Thread(() -> objectDetector.init(), "Object Detector Initializer").start();
        objectDetector.statusProperty().addListener((observable, oldValue, newValue) -> statusProperty.set(newValue));

        // keyboard
        setOnKeyPressed(event -> onKeyPressed(event));
    }

    public Annotation getModel() {
        return modelProperty().get();
    }
    public void setModel(Annotation annotation) {
        modelProperty().set(annotation);
    }
    public ObjectProperty<Annotation> modelProperty() {
        if (modelProperty == null) {
            modelProperty = new SimpleObjectProperty<>() {
                @Override
                public void set(Annotation model) {
                    super.set(model);
                    initModel(model);
                }
            };
        }
        return modelProperty;
    }

    private void initModel(Annotation model) {
        scale = new Scale(1, 1);
        translate = new Translate(PADDING, PADDING);
        rotate = new Rotate();
        paddingPane.getTransforms().clear();
        paddingPane.getTransforms().addAll(scale, rotate);

        scale.addEventHandler(TransformChangedEvent.TRANSFORM_CHANGED, event -> {
            // Maintain constant padding at different zoom level
            paddingPane.setPadding(new Insets(Math.max(PADDING/scale.getX(), PADDING/scale.getY())));

            canvas.setWidth(imageView.getBoundsInLocal().getWidth());
            canvas.setHeight(imageView.getBoundsInLocal().getHeight());
        });

        scale.setX(1);
        scale.setY(1);
        rotate.setAngle(0);
        selectedObjectProperty.setValue(null);
        tagCoordsProperty.set("");
        objectsProperty.clear();
        hintsProperty.clear();
        imageView.setImage(model == null ? null : model.getSize().getImage());
        if (model != null && model.getObjects().size() > 0) {
            model.getObjects().forEach(obj -> createObjectTag(obj));
            statusProperty.set(MessageFormat.format(bundle.getString("msg.objectsCount"), model.getObjects().size()));
        }
        else {
            statusProperty.set(bundle.getString("msg.noObjects"));
        }
        findHints();
    }

    public Scale getScale() {
        return scale;
    }

    public void rotate(int angle) {
        rotate.setAngle((rotate.getAngle()+angle) % 360);
    }

    public ImageView getImageView() {
        return imageView;
    }

    // Temporary mouse drag states
    private BoundingBox dragBox;
    private Point2D anchor;

    // padding pane mouse listener
    private void onMousePressed(MouseEvent me) {
        requestFocus();
        deselectObjects();

        Point2D pt = imageView.parentToLocal(me.getX(), me.getY());
        anchor = pt;
        dragBox = new BoundingBox(pt.getX(), pt.getY(), 0, 0);
        me.consume();
    }

    // padding pane mouse listener
    private void onMouseDragged(MouseEvent me) {
        if (dragBox == null) {
            return;
        }
        updateDragBox(imageView.parentToLocal(me.getX(), me.getY()));
        me.consume();
    }

    // padding pane mouse listener
    private void onMouseReleased(MouseEvent me) {
        if (dragBox == null) {
            return;
        }
        updateDragBox(imageView.parentToLocal(me.getX(), me.getY()));

        // Clip selection to image view bounds
        Bounds bounds = imageView.getBoundsInLocal();
        double x = dragBox.getMinX() < 0 ? 0 : dragBox.getMinX();
        double y = dragBox.getMinY() < 0 ? 0 : dragBox.getMinY();
        double w = dragBox.getWidth() > bounds.getWidth() ? bounds.getWidth() : dragBox.getWidth();
        double h = dragBox.getHeight() > bounds.getHeight() ? bounds.getHeight() : dragBox.getHeight();

        if (w > ObjectTag.MIN_SIZE && h > ObjectTag.MIN_SIZE) {
            String lastLabel = NameEditor.getLastLabel(bundle);
            ObjectTag objectTag = addObjectTag(lastLabel, x, y, x + w, y + h);
            if (!Settings.getAutoSetName() || Settings.recentNames.size() <= 0) {
                NameEditor editor = new NameEditor(lastLabel);
                lastLabel = editor.showPopup(me.getScreenX(), me.getScreenY(), getScene().getWindow());
                objectTag.nameProperty().set(lastLabel);
                Settings.recentNames.add(lastLabel);
            }
        }
        else {
            tagCoordsProperty.set("");
            if (!anchor.equals(new Point2D(me.getX(), me.getY()))) {
                statusProperty.set(bundle.getString("msg.objectTooSmall"));
            }
        }

        canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        dragBox = null;
        anchor = null;
    }

    private void updateDragBox(Point2D me) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Settings.getObjectFillColor());
        final double maxX = imageView.getImage().getWidth();
        final double maxY = imageView.getImage().getHeight();
        double x = me.getX() < maxX ? (me.getX() < 0 ? 0 : me.getX()) : maxX;
        double y = me.getY() < maxY ? (me.getY() < 0 ? 0 : me.getY()) : maxY;;
        double w = x - anchor.getX();
        double h = y - anchor.getY();

        if (w < 0) {
            w = anchor.getX() - x;
        }
        else {
            x = anchor.getX();
        }

        if (h < 0) {
            h = anchor.getY() - y;
        }
        else {
            y = anchor.getY();
        }

        gc.clearRect(dragBox.getMinX(), dragBox.getMinY(), dragBox.getWidth(), dragBox.getHeight());
        gc.fillRect(x, y, w, h);
        dragBox = new BoundingBox(x, y, w, h);
    }

    private void onContextMenuEvent(ContextMenuEvent event) {
        ObjectTag selected = selectedObjectProperty.get();
        if (selected == null) {
            return;
        }
        contextMenu = new ContextMenu();
        for (int i = 0; i < 10 && i < Settings.recentNames.size(); i++) {
            String name = Settings.recentNames.get(i);
            if (name.isEmpty() || name.isBlank()) {
                continue;
            }
            MenuItem mi = new MenuItem(name);
            mi.setOnAction(value -> {
                selected.nameProperty().set(name);
                Settings.recentNames.add(name);
            });
            contextMenu.getItems().add(mi);
        }
        if (!contextMenu.getItems().isEmpty()) {
            contextMenu.getItems().add(new SeparatorMenuItem());
        }
        MenuItem editName = new MenuItem(bundle.getString("menu.editName"));
        editName.setOnAction(value -> {
            NameEditor editor = new NameEditor(selected.nameProperty().get());
            String label = editor.showPopup(event.getScreenX(), event.getScreenY(), getScene().getWindow());
            selected.nameProperty().set(label);
            Settings.recentNames.add(label);
        });
        MenuItem delete = new MenuItem(bundle.getString("menu.delete"));
        delete.setOnAction(value -> deleteSelected(bundle.getString("menu.delete")));
        contextMenu.getItems().addAll(editName, delete);
        contextMenu.setAutoHide(true);
        contextMenu.show(imageView, event.getScreenX(), event.getScreenY());
        event.consume();
    }

    private StringBuffer prefix = new StringBuffer();
    public void onKeyPressed(KeyEvent event) {
        if (event.isConsumed()) {
            return;
        }
        KeyCode code = event.getCode();
        if (code == KeyCode.BACK_SPACE) {
            deleteSelected(bundle.getString("menu.delete"));
        }
        else if (selectedObjectProperty.get() != null && code.isArrowKey() && event.isShortcutDown()) {
            if (selectedObjectProperty.get() != null) {
                selectedObjectProperty.get().move(
                        code == KeyCode.LEFT ? HorizontalDirection.LEFT : (code == KeyCode.RIGHT ? HorizontalDirection.RIGHT : null),
                        code == KeyCode.UP ? VerticalDirection.UP : (code == KeyCode.DOWN ? VerticalDirection.DOWN : null)
                );
            }
        }
        else if (selectedObjectProperty.get() != null && code.isLetterKey()) {
            // Try to assign label name by user-entered prefix
            scheduler.schedule(() -> prefix.delete(0, prefix.length()), 500, TimeUnit.MILLISECONDS);
            String name = Settings.recentNames.getByPrefix(prefix.append(code.getChar()).toString());
            if (name != null) {
                selectedObjectProperty.get().nameProperty().set(name);
            }
        }
    }

    public void deselectObjects() {
        // Dismiss currently opened context menu;
        if (contextMenu != null) {
            contextMenu.hide();
            contextMenu = null;
        }

        // Deselect all objects
        objectsProperty.stream().forEach(ov -> ov.setSelected(false));
        hintsProperty.stream().forEach(hv -> hv.setSelected(false));
    }

    public void deleteSelected(String action) {
        for (Iterator<ObjectTag> it = objectsProperty.iterator(); it.hasNext(); ) {
            ObjectTag objectTag = it.next();
            if (objectTag.isSelected()) {
                objectTag.setSelected(false);
                objectTag.setAction(action);
                it.remove();
            }
        }
    }

    public void showHints() {
        hintsProperty().forEach(hintTag -> hintTag.setVisible(true));
    }

    public void clearHints() {
        hintsProperty().forEach(hintTag -> hintTag.setVisible(false));
    }

    public ObjectTag addObjectTag(ObjectModel om, String action) {
        ObjectTag objectTag = createObjectTag(om);
        objectTag.setAction(action);
        objectTag.setSelected(true);
        return objectTag;
    }

    private ObjectTag createObjectTag(ObjectModel om) {
        ObjectTag objectTag = new ObjectTag(imageView.getImage(), translate, scale, rotate, om);
        objectTag.selectionProperty().addListener(observable -> tagSelectionChanged(objectTag));
        objectsProperty.add(objectTag);
        return objectTag;
    }

    private ObjectTag addObjectTag(String label, double minX, double minY, double maxX, double maxY) {
        ObjectModel om = new ObjectModel(label, minX, minY, maxX, maxY);
        return addObjectTag(om, bundle.getString("menu.create"));
    }

    private HintTag createHintTag(HintModel hm) {
        HintTag hintTag = new HintTag(imageView.getImage(), translate, scale, rotate, hm);
        hintTag.selectionProperty().addListener(observable -> tagSelectionChanged(hintTag));
        hintTag.hintConfirmProperty().addListener(observable -> onHintAccepted(hintTag));
        hintsProperty.add(hintTag);
        return hintTag;
    }

    private void tagSelectionChanged(TagBase source) {
        // Ensure single tag selection
        if (source.isSelected()) {
            getChildren().stream()
                    .filter(c -> c instanceof TagBase)
                    .map (c -> (TagBase) c)
                    .filter(ov -> ov != source).forEach(ov -> ov.setSelected(false));
            if (source instanceof ObjectTag) {
                selectedObjectProperty.set((ObjectTag)source);
            }
            tagCoordsProperty.set(getCoordinates(source.coordsProperty().get()));
            source.coordsProperty().addListener((observable, oldValue, newValue) -> tagCoordsProperty.set(getCoordinates(newValue)));
        }
        else {
            if (objectsProperty.stream().filter(ov -> ov.isSelected()).count() > 0) {
                return;
            }
            selectedObjectProperty.set(null);
            tagCoordsProperty.set("");
        }
    }

    private void onHintAccepted(HintTag hintTag) {
        // Turn a hint tag to an object tag
        String label = hintTag.getModel().getName();
        BoundBox box = hintTag.getModel().getBoundBox();
        ObjectModel om = new ObjectModel(label, box.getXMin(), box.getYMin(), box.getXMax(), box.getYMax());
        ObjectTag objectTag = createObjectTag(om);
        objectTag.setSelected(true);
        hintTag.setVisible(false);
    }

    private void findHints() {
        if (!Settings.isUseInference()) {
            return;
        }
        new Thread(() -> {
            try {
                final Annotation model = getModel();
                if (model != null) {
                    List<HintModel> hints = objectDetector.detect(model.getFile());
                    Platform.runLater(() -> {
                        if (getModel() != model) {
                            return;
                        }
                        hints.forEach(hint -> createHintTag(hint));
                        if (model.getObjects().size() > 0) {
                            clearHints();
                        }
                        else {
                            statusProperty.set(MessageFormat.format(bundle.getString("msg.detectedObjects"), hints.size()));
                            showHints();
                        }
                    });
                }
            }
            catch (Exception ex) {
                LOG.log(Level.WARNING, "Fail to detect", ex);
            }
        }, "Object Detector").start();
    }

    private String getCoordinates(Bounds rect) {
        if (rect == null) {
            return "";
        }
        // Top left starts at (1, 1)
        return MessageFormat.format(bundle.getString("msg.coords"), (int)rect.getMinX()+1, (int)rect.getMinY()+1, (int)rect.getWidth(), (int)rect.getHeight());
    }

    @Override
    public void close() {
        Optional.ofNullable(objectDetector).ifPresent(obj -> obj.close());
        scheduler.shutdown();
    }
}
