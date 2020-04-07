package com.easymobo.openlabeler.tag;

import com.easymobo.openlabeler.model.BoundBox;
import com.easymobo.openlabeler.model.ObjectModel;
import javafx.beans.property.*;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Shape;
import javafx.scene.transform.Scale;
import javafx.scene.transform.TransformChangedEvent;
import javafx.scene.transform.Translate;
import org.fxmisc.easybind.EasyBind;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static com.easymobo.openlabeler.tag.TagBase.HANDLE_RADIUS;

public class PolygonItem extends Polygon implements ShapeItem
{
   private DoubleProperty minXProperty, minYProperty, maxXProperty, maxYProperty;
   private Dimension2D boundsDimension;

   public PolygonItem() {
   }

   public PolygonItem(ObjectModel model, Dimension2D imageDimension) {
      getPoints().addAll(model.getPolygon());
      boundsDimension = imageDimension;
   }

   @Override
   public void save(ObjectModel model) {
      var bounds = getBoundsInLocal();
      model.setBoundBox(new BoundBox(bounds.getMinX(), bounds.getMinY(), bounds.getMaxX(), bounds.getMaxY()));
      model.setPolygon(getPoints());
   }

   @Override
   public ReadOnlyDoubleProperty getMinXProperty() {
      if (minXProperty == null) {
         minXProperty = new SimpleDoubleProperty();
         minXProperty.bind(EasyBind.map(boundsInLocalProperty(), Bounds::getMinX));
      }
      return minXProperty;
   }

   @Override
   public ReadOnlyDoubleProperty getMinYProperty() {
      if (minYProperty == null) {
         minYProperty = new SimpleDoubleProperty();
         minYProperty.bind(EasyBind.map(boundsInLocalProperty(), Bounds::getMinY));
      }
      return minYProperty;
   }

   @Override
   public ReadOnlyDoubleProperty getMaxXProperty() {
      if (maxXProperty == null) {
         maxXProperty = new SimpleDoubleProperty();
         maxXProperty.bind(EasyBind.map(boundsInLocalProperty(), Bounds::getMaxX));
      }
      return maxXProperty;
   }

   @Override
   public ReadOnlyDoubleProperty getMaxYProperty() {
      if (maxYProperty == null) {
         maxYProperty = new SimpleDoubleProperty();
         maxYProperty.bind(EasyBind.map(boundsInLocalProperty(), Bounds::getMaxY));
      }
      return maxYProperty;
   }

   @Override
   public Shape toShape() {
      return this;
   }

   @Override
   public void copyFrom(Object src) {
      if (!(src instanceof PolygonItem)) {
         return;
      }
      getPoints().setAll(Polygon.class.cast(src).getPoints());
   }

   @Override
   public ShapeItem createCopy() {
      var item = new PolygonItem();
      item.copyFrom(this);
      return item;
   }

   @Override
   public List<? extends Shape> getHandles(Translate translate, Scale scale, ReadOnlyObjectProperty<Color> colorProperty) {
      List<Shape> handles = new ArrayList<>();

      IntStream.range(0, getPoints().size() / 2).forEach(idx -> {
         Handle handle = new Handle(idx, getPoints());
         handles.add(handle);

         // Maintain constant handle size at different zoom level
         scale.addEventHandler(TransformChangedEvent.TRANSFORM_CHANGED, event -> {
            handle.setRadius(HANDLE_RADIUS / scale.getX());
         });
         handle.fillProperty().bind(colorProperty);
         handle.setRadius(HANDLE_RADIUS / scale.getX());
         handle.getTransforms().addAll(translate, scale);
      });

      return handles;
   }

   @Override
   public ShapeItem moveTo(double x, double y) {
      double deltaX = x - getX();
      IntStream.range(0, getPoints().size() / 2).forEach(idx -> {
         getPoints().set(idx * 2, getPoints().get(idx * 2) + deltaX);
      });
      double deltaY = y - getY();
      IntStream.range(0, getPoints().size() / 2).forEach(idx -> {
         getPoints().set(idx * 2 + 1, getPoints().get(idx * 2 + 1) + deltaY);
      });
      return this;
   }

   @Override
   public boolean equals(Object obj) {
      if (obj == this) return true;
      if (obj instanceof PolygonItem) {
         PolygonItem other = (PolygonItem) obj;
         return getPoints().equals(other.getPoints());
      }
      return false;
   }

   @Override
   public int hashCode() {
      return Objects.hash(getPoints());
   }

   private class Handle extends Circle
   {
      private int index;
      private ObservableList<Double> points;
      private Point2D offset;

      public BooleanProperty selectedProperty = new SimpleBooleanProperty();

      public Handle(int idx, ObservableList<Double> pts) {
         super(0, 0, TagBase.HANDLE_RADIUS);
         index = idx;
         points = pts;
         setCenterX(points.get(idx * 2));
         setCenterY(points.get(idx * 2 + 1));

         var changeListListener = new ListChangeListener<Double>()
         {
            @Override
            public void onChanged(Change<? extends Double> c) {
               if (!c.next()) {
                  return;
               }
               if (c.wasReplaced()) {
                  setCenterX(points.get(idx * 2));
                  setCenterY(points.get(idx * 2 + 1));
               }
            }
         };

         parentProperty().addListener(((observable, oldValue, newValue) -> {
            if (newValue == null) {
               points.removeListener(changeListListener);
            }
            else {
               points.addListener(changeListListener);
            }
         }));

         setCursor(Cursor.CROSSHAIR);
         setOnMousePressed(this::onMousePressed);
         setOnMouseDragged(this::onMouseDragged);
         setOnMouseReleased(this::onMouseReleased);
      }

      public ReadOnlyBooleanProperty getSelectedProperty() {
         return selectedProperty;
      }

      private void onMousePressed(MouseEvent event) {
         offset = new Point2D(event.getX() - getCenterX(), event.getY() - getCenterY());
         selectedProperty.set(true);
      }

      private void onMouseDragged(MouseEvent event) {
         if (offset == null) {
            return;
         }
         // X location
         double x = event.getX() - offset.getX();
         if (x < 0) {
            x = 0;
         }
         if (x > boundsDimension.getWidth()) {
            x = boundsDimension.getWidth();
         }
         // Y location
         double y = event.getY() - offset.getY();
         if (y < 0) {
            y = 0;
         }
         if (y > boundsDimension.getHeight()) {
            y = boundsDimension.getHeight();
         }
         points.set(index * 2, x);
         points.set(index * 2 + 1, y);
      }

      private void onMouseReleased(MouseEvent event) {
         offset = null;
      }
   }
}
