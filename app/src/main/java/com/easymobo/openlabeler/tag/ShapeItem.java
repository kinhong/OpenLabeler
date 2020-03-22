package com.easymobo.openlabeler.tag;

import com.easymobo.openlabeler.model.ObjectModel;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.scene.paint.Color;
import javafx.scene.shape.Shape;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

import java.util.List;

public interface ShapeItem extends Cloneable
{
   enum Type {
      RECTANGLE, POLYGON
   }

   ReadOnlyDoubleProperty getMinXProperty();
   ReadOnlyDoubleProperty getMinYProperty();
   ReadOnlyDoubleProperty getMaxXProperty();
   ReadOnlyDoubleProperty getMaxYProperty();

   default double getX() {
      return getMinXProperty().get();
   }

   default double getY() {
      return getMinYProperty().get();
   }

   default double getWidth() {
      return getMaxXProperty().get() - getMinXProperty().get();
   }

   default double getHeight() {
      return getMaxYProperty().get() - getMinYProperty().get();
   }

   void copyFrom(Object src);
   ShapeItem makeCopy();
   List<? extends Shape> getHandles(Translate translate, Scale scale, ReadOnlyObjectProperty<Color> colorProperty);
   void moveTo(double x, double y);
   void save(ObjectModel model);
}
