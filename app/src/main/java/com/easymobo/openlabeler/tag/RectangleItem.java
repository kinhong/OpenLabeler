package com.easymobo.openlabeler.tag;

import com.easymobo.openlabeler.model.ObjectModel;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.transform.Scale;
import javafx.scene.transform.TransformChangedEvent;
import javafx.scene.transform.Translate;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.easymobo.openlabeler.tag.TagBase.HANDLE_SIZE;
import static com.easymobo.openlabeler.tag.TagBase.MIN_SIZE;

public class RectangleItem extends Rectangle implements ShapeItem
{
   private enum Location
   {
      NW, NE, SE, SW
   }

   private static final Cursor cursorNW = Cursor.CROSSHAIR;//createImageCursor("resize_nw.png");
   private static final Cursor cursorNE = Cursor.CROSSHAIR;//createImageCursor("resize_ne.png");
   private static final Cursor cursorSE = Cursor.CROSSHAIR;//createImageCursor("resize_se.png");
   private static final Cursor cursorSW = Cursor.CROSSHAIR;//createImageCursor("resize_sw.png");

   private Dimension2D boundsDimension;

   public RectangleItem() {
   }

   public RectangleItem(ObjectModel model, Dimension2D imageDimension) {
      setX(model.getBoundBox().getXMin());
      setY(model.getBoundBox().getYMin());
      setWidth(model.getBoundBox().getXMax() - model.getBoundBox().getXMin());
      setHeight(model.getBoundBox().getYMax() - model.getBoundBox().getYMin());

      boundsDimension = imageDimension;
   }

   @Override
   public void copyFrom(Object src) {
      if (!(src instanceof RectangleItem)) {
         return;
      }
      setX(Rectangle.class.cast(src).getX());
      setY(Rectangle.class.cast(src).getY());
      setWidth(Rectangle.class.cast(src).getWidth());
      setHeight(Rectangle.class.cast(src).getHeight());
   }

   @Override
   public ShapeItem makeCopy() {
      var item = new RectangleItem();
      item.copyFrom(this);
      return item;
   }

   @Override
   public List<? extends Shape> getHandles(Translate translate, Scale scale, ReadOnlyObjectProperty<Color> colorProperty) {
      // 4 corner resize handles
      List<Rectangle> handles = Arrays.asList(new Rectangle[]{
            createHandle(Location.NW, cursorNW),
            createHandle(Location.NE, cursorNE),
            createHandle(Location.SE, cursorSE),
            createHandle(Location.SW, cursorSW)});

      // Maintain constant handle size at different zoom level
      handles.forEach(handle -> {
         scale.addEventHandler(TransformChangedEvent.TRANSFORM_CHANGED, event -> {
            handle.setWidth(HANDLE_SIZE / scale.getX());
            handle.setHeight(HANDLE_SIZE / scale.getY());
         });
         handle.fillProperty().bind(colorProperty);
         handle.setWidth(HANDLE_SIZE / scale.getX());
         handle.setHeight(HANDLE_SIZE / scale.getY());
         handle.getTransforms().addAll(translate, scale);
      });
      return handles;
   }

   @Override
   public ReadOnlyDoubleProperty getMinXProperty() {
      return xProperty();
   }

   @Override
   public ReadOnlyDoubleProperty getMinYProperty() {
      return yProperty();
   }

   @Override
   public ReadOnlyDoubleProperty getMaxXProperty() {
      return widthProperty();
   }

   @Override
   public ReadOnlyDoubleProperty getMaxYProperty() {
      return heightProperty();
   }

   @Override
   public void moveTo(double x, double y) {
      setX(x);
      setY(y);
   }

   @Override
   public void save(ObjectModel model) {
      model.getBoundBox().setXMin(getX());
      model.getBoundBox().setYMin(getY());
      model.getBoundBox().setXMax(getX() + getWidth());
      model.getBoundBox().setYMax(getY() + getHeight());
   }

   private Point2D offset;

   private Rectangle createHandle(Location location, Cursor cursor) {
      Rectangle handle = new Rectangle(HANDLE_SIZE, HANDLE_SIZE);
      switch (location) {
         case NW:
            handle.xProperty().bind(xProperty().subtract(handle.widthProperty().divide(2)));
            handle.yProperty().bind(yProperty().subtract(handle.heightProperty().divide(2)));
            break;
         case NE:
            handle.xProperty().bind(xProperty().add(widthProperty()).subtract(handle.widthProperty().divide(2)));
            handle.yProperty().bind(yProperty().subtract(handle.heightProperty().divide(2)));
            break;
         case SE:
            handle.xProperty().bind(xProperty().add(widthProperty()).subtract(handle.widthProperty().divide(2)));
            handle.yProperty().bind(yProperty().add(heightProperty()).subtract(handle.heightProperty().divide(2)));
            break;
         case SW:
            handle.xProperty().bind(xProperty().subtract(handle.widthProperty().divide(2)));
            handle.yProperty().bind(yProperty().add(heightProperty()).subtract(handle.heightProperty().divide(2)));
            break;
      }

      handle.setCursor(cursor);
      handle.setOnMousePressed(event -> {
         switch (location) {
            case NW:
               offset = new Point2D(event.getX() - getX(), event.getY() - getY());
               break;
            case NE:
               offset = new Point2D(event.getX() - (getX() + getWidth()), event.getY() - getY());
               break;
            case SE:
               offset = new Point2D(event.getX() - (getX() + getWidth()), event.getY() - (getY() + getHeight()));
               break;
            case SW:
               offset = new Point2D(event.getX() - getX(), event.getY() - (getY() + getHeight()));
               break;
         }
      });
      handle.setOnMouseDragged(event -> updateDraggedHandle(location, event));
      handle.setOnMouseReleased(event -> {
         offset = null;
      });

      return handle;
   }

   private void updateDraggedHandle(Location location, MouseEvent event) {
      if (offset == null) {
         return;
      }
      // X location
      double x1, x2, y1, y2;
      if (location == Location.NW || location == Location.SW) {
         x1 = event.getX() - offset.getX();
         x2 = getX() + getWidth();
         if (x1 < 0) {
            x1 = 0;
         }
         else if (x1 > x2 - MIN_SIZE) {
            x1 = x2 - MIN_SIZE;
         }
      }
      else {
         x1 = getX();
         x2 = event.getX() - offset.getX();
         if (x2 > boundsDimension.getWidth()) {
            x2 = boundsDimension.getWidth();
         }
         else if (x2 < x1 + MIN_SIZE) {
            x2 = x1 + MIN_SIZE;
         }
      }
      // Y location
      if (location == Location.NW || location == Location.NE) {
         y1 = event.getY() - offset.getY();
         y2 = getY() + getHeight();
         if (y1 < 0) {
            y1 = 0;
         }
         else if (y1 > y2 - MIN_SIZE) {
            y1 = y2 - MIN_SIZE;
         }
      }
      else {
         y1 = getY();
         y2 = event.getY() - offset.getY();
         if (y2 > boundsDimension.getHeight()) {
            y2 = boundsDimension.getHeight();
         }
         else if (y2 < y1 + MIN_SIZE) {
            y2 = y1 + MIN_SIZE;
         }
      }
      setX(x1);
      setY(y1);
      setWidth(x2 - x1);
      setHeight(y2 - y1);
   }

   @Override
   public boolean equals(Object obj) {
      if (obj == this) return true;
      if (obj instanceof RectangleItem) {
         RectangleItem other = (RectangleItem) obj;
         return getX() == other.getX()
                && getY() == other.getY()
                && getWidth() == other.getWidth()
                && getHeight() == other.getHeight();
      }
      return false;
   }

   @Override
   public int hashCode() {
      return Objects.hash(getX(), getY(), getWidth(), getHeight());
   }

   private static ImageCursor createImageCursor(String path) {
      Image image = new Image(path, true);
      return new ImageCursor(image, image.getWidth() / 2, image.getHeight() / 2);
   }
}
