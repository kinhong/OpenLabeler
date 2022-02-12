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

package com.easymobo.openlabeler.tag;

import com.easymobo.openlabeler.model.Annotation;
import com.easymobo.openlabeler.model.BoundBox;
import com.easymobo.openlabeler.model.HintModel;
import com.easymobo.openlabeler.model.ObjectModel;
import com.easymobo.openlabeler.preference.NameColor;
import com.easymobo.openlabeler.preference.Settings;
import com.easymobo.openlabeler.tensorflow.ObjectDetector;
import com.easymobo.openlabeler.ui.NameEditor;
import com.easymobo.openlabeler.util.AppUtils;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.VerticalDirection;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.TransformChangedEvent;
import javafx.scene.transform.Translate;

import java.lang.invoke.MethodHandles;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.easymobo.openlabeler.tag.ShapeItem.Type.POLYGON;
import static com.easymobo.openlabeler.tag.ShapeItem.Type.RECTANGLE;
import static java.lang.Math.max;
import static java.lang.Math.min;

public class TagBoard extends Group implements AutoCloseable
{
   @FXML
   private StackPane board;
   @FXML
   private ImageView imageView;
   @FXML
   private Canvas canvas;

   private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());
   private static final int PADDING = 10;

   private ResourceBundle bundle = ResourceBundle.getBundle("bundle");
   ;
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

   public TagBoard() {
      FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/TagBoard.fxml"), bundle);
      loader.setRoot(this);
      loader.setController(this);

      try {
         loader.load();
      }
      catch (Exception ex) {
         LOG.log(Level.SEVERE, "Unable to load FXML", ex);
      }

      board.setOnMousePressed(event -> onMousePressed(event));
      board.setOnMouseMoved(event -> onMouseMoved(event));
      board.setOnMouseClicked(event -> onMouseClicked(event));
      board.setOnMouseDragged(event -> onMouseDragged(event));
      board.setOnMouseReleased(event -> onMouseReleased(event));

      // context menu
      addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> onContextMenuEvent(event));

      // add/remove ObjectTag
      objectsProperty().addListener((ListChangeListener<ObjectTag>) change -> {
         if (!change.next()) {
            return;
         }
         Annotation model = getModel();
         if (change.wasAdded()) {
            int offset = getChildren().indexOf(board) + 1;
            change.getAddedSubList().forEach(objectTag -> {
               getChildren().add(offset + change.getFrom(), objectTag);
               if (model != null && !model.getObjects().contains(objectTag.getModel())) {
                  model.getObjects().add(objectTag.getModel());
               }
            });
         }
         else if (change.wasRemoved()) {
            change.getRemoved().forEach(objectTag -> {
               getChildren().remove(objectTag);
               if (model != null && model.getObjects().contains(objectTag.getModel())) {
                  model.getObjects().remove(objectTag.getModel());
               }
            });
         }
      });

      // Selected tag animation outline
      Settings.animateOutlineProperty.addListener((observableValue, oldValue, newValue) -> {
         if (selectedObjectProperty.get() != null) {
            selectedObjectProperty.get().setAnimateOutline(Settings.isAnimateOutline());
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

      // inference
      Settings.useInferenceProperty.addListener((observable, oldValue, newValue) -> findHints());

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
         modelProperty = new SimpleObjectProperty<>()
         {
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
      board.getTransforms().setAll(scale, rotate);

      scale.addEventHandler(TransformChangedEvent.TRANSFORM_CHANGED, event -> {
         // Maintain constant padding at different zoom level
         board.setPadding(new Insets(max(PADDING / scale.getX(), PADDING / scale.getY())));

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
      imageView.setCache(true);
      canvas.setWidth(imageView.getBoundsInLocal().getWidth());
      canvas.setHeight(imageView.getBoundsInLocal().getHeight());
      if (model != null && model.getObjects().size() > 0) {
         model.getObjects().forEach(obj -> createObjectTag(obj));
         statusProperty.set(MessageFormat.format(bundle.getString("msg.objectsCount"), model.getObjects().size()));
      }
      else {
         statusProperty.set(bundle.getString("msg.noObjects"));
      }
      Platform.runLater(() -> findHints());
   }

   public Scale getScale() {
      return scale;
   }

   public void rotate(int angle) {
      rotate.setAngle((rotate.getAngle() + angle) % 360);
   }

   public ImageView getImageView() {
      return imageView;
   }

   // Mouse points during shape create
   private List<Point2D> points = new ArrayList<>();
   private Point2D lastPoint = null;
   private Path path;

   private void onMousePressed(MouseEvent me) {
      if (imageView.getImage() == null) {
         return;
      }
      requestFocus();
      deselectObjects();

      Point2D pt = imageView.parentToLocal(me.getX(), me.getY());
      if (Settings.getEditShape() == RECTANGLE) {
         beginShape(pt);
      }
      me.consume();
   }

   private void onMouseMoved(MouseEvent me) {
      if (imageView.getImage() == null) {
         return;
      }

      if (Settings.getEditShape() == POLYGON && path != null) {
         updatePath(imageView.parentToLocal(me.getX(), me.getY()));
      }
      me.consume();
   }

   private void onMouseDragged(MouseEvent me) {
      if (imageView.getImage() == null) {
         return;
      }

      if (Settings.getEditShape() == RECTANGLE && path != null) {
         updateDragBox(imageView.parentToLocal(me.getX(), me.getY()));
      }
      me.consume();
   }

   private void onMouseClicked(MouseEvent me) {
      if (imageView.getImage() == null) {
         return;
      }

      if (!me.getButton().equals(MouseButton.PRIMARY)) {
         me.consume();
         return;
      }
      if (Settings.getEditShape() == POLYGON) {
         Point2D pt = imageView.parentToLocal(me.getX(), me.getY());
         if (path == null) {
            // Start a polygon shape with double-click or SHORTCUT + mouse click
            if (!me.isShortcutDown() && me.getClickCount() != 2) {
               me.consume();
               return;
            }
            beginShape(pt);
         }
         else {
            // End a polygon shape with double-click (and space key)
            if (me.getClickCount() == 2) {
               endShape(pt);
               return;
            }
            path.getElements().add(new LineTo(pt.getX(), pt.getY()));
            points.add(pt);
            updatePath(pt);
         }
      }
      me.consume();
   }

   private void onMouseReleased(MouseEvent me) {
      if (imageView.getImage() == null) {
         return;
      }

      if (Settings.getEditShape() == RECTANGLE && path != null) {
         updateDragBox(imageView.parentToLocal(me.getX(), me.getY()));
         path.getElements().add(new LineTo(lastPoint.getX(), lastPoint.getY()));
         endShape(imageView.parentToLocal(me.getX(), me.getY()));
      }
      me.consume();
   }

   private void updatePath(Point2D mousePt) {
      final double maxX = imageView.getImage().getWidth();
      final double maxY = imageView.getImage().getHeight();
      double x = mousePt.getX() < maxX ? (mousePt.getX() < 0 ? 0 : mousePt.getX()) : maxX;
      double y = mousePt.getY() < maxY ? (mousePt.getY() < 0 ? 0 : mousePt.getY()) : maxY;

      // Clear last drawing
      GraphicsContext gc = canvas.getGraphicsContext2D();
      clearLastDrawing(gc, points, lastPoint);

      // Draw outline and fill temporary polygon
      List<Point2D> pts = new ArrayList<>();
      pts.addAll(points);
      pts.add(new Point2D(x, y));
      double[] xPts = AppUtils.getXPoints(pts);
      double[] yPts = AppUtils.getYPoints(pts);

      gc.setStroke(Settings.getObjectStrokeColor());
      gc.setLineWidth(min(4d, 4d / scale.getX()));
      gc.setLineCap(StrokeLineCap.ROUND);
      gc.strokePolyline(xPts, yPts, pts.size());

      gc.setFill(Settings.getObjectFillColor());
      gc.fillPolygon(xPts, yPts, pts.size());

      lastPoint = new Point2D(x, y);
   }

   private void updateDragBox(Point2D mousePt) {
      final double maxX = imageView.getImage().getWidth();
      final double maxY = imageView.getImage().getHeight();
      double x = mousePt.getX() < maxX ? (mousePt.getX() < 0 ? 0 : mousePt.getX()) : maxX;
      double y = mousePt.getY() < maxY ? (mousePt.getY() < 0 ? 0 : mousePt.getY()) : maxY;
      var anchor = points.get(0);

      // Clear last drawing
      GraphicsContext gc = canvas.getGraphicsContext2D();
      clearLastDrawing(gc, points, lastPoint);

      gc.setStroke(Settings.getObjectStrokeColor());
      gc.setLineWidth(min(4d, 4d / scale.getX()));
      gc.setLineCap(StrokeLineCap.ROUND);
      gc.strokeLine(anchor.getX(), anchor.getY(), x, y);

      gc.setFill(Settings.getObjectFillColor());
      var bounds = AppUtils.getBounds(anchor, new Point2D(x, y));
      gc.fillRect(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
      lastPoint = new Point2D(x, y);
   }

   private void clearLastDrawing(GraphicsContext gc, List<Point2D> points, Point2D lastPt) {
      List<Point2D> clearPts = new ArrayList<>();
      clearPts.addAll(points);
      if (lastPt != null) {
         clearPts.add(lastPt);
      }
      var bounds = AppUtils.getBounds(clearPts.toArray(new Point2D[] {}));
      bounds = AppUtils.insetBounds(bounds, -10d);
      gc.clearRect(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
   }

   private void modifyShape(KeyEvent event) {
      if (Settings.getEditShape() == POLYGON && path != null && lastPoint != null) {
         if (event.isShortcutDown()) {
            path.getElements().add(new LineTo(lastPoint.getX(), lastPoint.getY()));
            points.add(lastPoint);
            updatePath(lastPoint);
         }
         else if (event.getCode() == KeyCode.SPACE) {
            endShape(lastPoint);
         }
      }
   }

   private void beginShape(Point2D mousePt) {
      path = new Path();
      path.getElements().add(new MoveTo(mousePt.getX(), mousePt.getY()));
      points.add(mousePt);
      objectsProperty.forEach(tag -> tag.setMouseTransparent(true));
      hintsProperty.forEach(tag -> tag.setMouseTransparent(true));
   }

   private void endShape(Point2D mousePt) {
      if (mousePt != null && path != null) {
         ObjectTag objectTag = null;
         String lastLabel = NameEditor.getLastLabel(bundle);

         var shapeBounds = path.getBoundsInLocal();
         if (shapeBounds.getWidth() > ObjectTag.MIN_SIZE && shapeBounds.getHeight() > ObjectTag.MIN_SIZE) {
            objectTag = addObjectTag(lastLabel, Settings.getEditShape(), path);
         }

         if (objectTag != null) {
            if (!Settings.isAutoSetName() || Settings.recentNamesProperty.size() <= 0) {
               NameEditor editor = new NameEditor(lastLabel);
               var screenBounds = imageView.localToScreen(objectTag.getBounds());
               lastLabel = editor.showPopup(screenBounds.getMaxX(), screenBounds.getMaxY(), getScene().getWindow());
               objectTag.nameProperty().set(lastLabel);
               Settings.recentNamesProperty.addName(lastLabel);
            }
         }
         else {
            tagCoordsProperty.set("");
            if (points.get(0).equals(mousePt)) {
               statusProperty.set(bundle.getString("msg.objectTooSmall"));
            }
         }
      }

      objectsProperty.forEach(tag -> tag.setMouseTransparent(false));
      hintsProperty.forEach(tag -> tag.setMouseTransparent(false));

      canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
      points.clear();
      path = null;
   }

   private void onContextMenuEvent(ContextMenuEvent event) {
      ObjectTag selected = selectedObjectProperty.get();
      if (selected == null) {
         return;
      }
      contextMenu = new ContextMenu();
      for (int i = 0; i < 10 && i < Settings.recentNamesProperty.size(); i++) {
         NameColor nameColor = Settings.recentNamesProperty.get(i);
         String name = nameColor.getName();
         if (name.isEmpty() || name.isBlank()) {
            continue;
         }
         MenuItem mi = new MenuItem(name);
         mi.setOnAction(value -> {
            selected.nameProperty().set(name);
            Settings.recentNamesProperty.addName(name);
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
         Settings.recentNamesProperty.addName(label);
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
      if (code == KeyCode.BACK_SPACE || code == KeyCode.DELETE) {
         deleteSelected(bundle.getString("menu.delete"));
      }
      else if (code == KeyCode.ESCAPE) {
         endShape(null);
      }
      else if (selectedObjectProperty.get() != null && code.isArrowKey() && event.isShortcutDown()) {
         selectedObjectProperty.get().move(
               code == KeyCode.LEFT ? HorizontalDirection.LEFT : (code == KeyCode.RIGHT ? HorizontalDirection.RIGHT : null),
               code == KeyCode.UP ? VerticalDirection.UP : (code == KeyCode.DOWN ? VerticalDirection.DOWN : null),
               1 / scale.getX(), 1 / scale.getY()
         );
      }
      else if (selectedObjectProperty.get() != null && code.isLetterKey()) {
         // Try to assign label name by user-entered prefix
         scheduler.schedule(() -> prefix.delete(0, prefix.length()), 500, TimeUnit.MILLISECONDS);
         NameColor nameColor = Settings.recentNamesProperty.getByPrefix(prefix.append(code.getChar()).toString());
         if (nameColor != null) {
            selectedObjectProperty.get().nameProperty().set(nameColor.getName());
         }
      }
      else {
         modifyShape(event);
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
      ObjectTag objectTag = new ObjectTag(imageView, translate, scale, rotate, om);
      objectTag.selectionProperty().addListener(observable -> tagSelectionChanged(objectTag));
      objectTag.setAction(action);
      objectTag.setSelected(true);
      objectsProperty.add(objectTag);
      return objectTag;
   }

   private ObjectTag createObjectTag(ObjectModel om) {
      ObjectTag objectTag = new ObjectTag(imageView, translate, scale, rotate, om);
      objectTag.selectionProperty().addListener(observable -> tagSelectionChanged(objectTag));
      objectsProperty.add(objectTag);
      return objectTag;
   }

   private ObjectTag addObjectTag(String label, ShapeItem.Type type, Path path) {
      var bounds = path.getBoundsInLocal();
      ObjectModel om = new ObjectModel(label, bounds.getMinX(), bounds.getMinY(), bounds.getMaxX(), bounds.getMaxY());
      if (type == POLYGON) {
         om.setPolygon(AppUtils.getPolygonPoints(path));
      }
      return addObjectTag(om, bundle.getString("menu.create"));
   }

   private HintTag createHintTag(HintModel hm) {
      HintTag hintTag = new HintTag(imageView, translate, scale, rotate, hm);
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
               .map(c -> (TagBase) c)
               .filter(ov -> ov != source).forEach(ov -> ov.setSelected(false));
         if (source instanceof ObjectTag) {
            source.setAnimateOutline(Settings.isAnimateOutline());
            selectedObjectProperty.set((ObjectTag) source);
         }
         tagCoordsProperty.set(getCoordinates(source.shapeItemProperty().get()));
         source.shapeItemProperty().addListener((observable, oldValue, newValue) -> tagCoordsProperty.set(getCoordinates(newValue)));
      }
      else {
         if (objectsProperty.stream().filter(ov -> ov.isSelected()).count() > 0) {
            return;
         }
         source.setAnimateOutline(false);
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
            // Object detector and hints
            if (objectDetector == null) {
               objectDetector = new ObjectDetector();
               objectDetector.init();
               objectDetector.statusProperty().addListener((observable, oldValue, newValue) -> statusProperty.set(newValue));
            }

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

   private String getCoordinates(ShapeItem shape) {
      if (shape == null) {
         return "";
      }
      // Top left starts at (1, 1)
      return MessageFormat.format(bundle.getString("msg.coords"),
            (int) shape.getX() + 1, (int) shape.getY() + 1, (int) shape.getWidth(), (int) shape.getHeight());
   }

   @Override
   public void close() {
      Optional.ofNullable(objectDetector).ifPresent(obj -> obj.close());
      scheduler.shutdown();
   }
}
