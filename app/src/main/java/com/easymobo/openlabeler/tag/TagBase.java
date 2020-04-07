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

package com.easymobo.openlabeler.tag;

import com.easymobo.openlabeler.model.ObjectModel;
import com.easymobo.openlabeler.preference.Settings;
import com.easymobo.openlabeler.util.AppUtils;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.*;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Shape;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.TransformChangedEvent;
import javafx.scene.transform.Translate;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class TagBase extends Group
{
    @FXML
    protected Label name;

    private ObjectModel model;
    protected ShapeItem shapeItem;

    static final double HANDLE_SIZE = 8;
    static final double HANDLE_RADIUS = 4;
    static final double MIN_SIZE = HANDLE_SIZE * 3;

    private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

    protected ResourceBundle bundle = ResourceBundle.getBundle("bundle");
    protected Dimension2D imageDim;
    protected List<Shape> handles = new ArrayList();
    protected Translate translate;
    protected Scale scale;

    public TagBase() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/TagBase.fxml"));
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to load FXML", ex);
        }
    }

    public void init(Image image, Translate translate, Scale scale, Rotate rotate, ObjectModel model) {
        this.imageDim = new Dimension2D(image.getWidth(), image.getHeight());
        this.translate = translate;
        this.scale = scale;
        this.model = model;

        shapeItem = createShapeItem();
        var shape = shapeItem.toShape();
        shape.getTransforms().addAll(translate, scale);
        shape.setFill(Color.TRANSPARENT);

        shape.setStrokeWidth(2 / scale.getX());
        shape.strokeProperty().bind(strokeColorProperty());
        scale.addEventHandler(TransformChangedEvent.TRANSFORM_CHANGED, event -> {
            shape.setStrokeWidth(2 / scale.getX()); // Retain box stroke width at all zoom levels
        });

        shape.setOnMouseClicked(this::onMouseClicked);
        shape.setOnMousePressed(this::onMousePressed);
        shape.setOnMouseDragged(this::onMouseDragged);
        addEventFilter(MouseEvent.MOUSE_RELEASED, this::onMouseReleased);

        shapeItemProperty().set(shapeItem.createCopy());
        getChildren().add(0, shape);

        // Prevent name from rotating so it is always upright
        this.getTransforms().add(rotate);
        name.getTransforms().add(new Rotate(-rotate.getAngle(), 0, 0));
        rotate.addEventFilter(TransformChangedEvent.TRANSFORM_CHANGED, event -> {
            AppUtils.getTransform(name, Rotate.class).setAngle(-rotate.getAngle());
            name.translateXProperty().bind(getNameTranslateXProperty(rotate));
            name.translateYProperty().bind(getNameTranslateYProperty(rotate));
        });
        name.translateXProperty().bind(getNameTranslateXProperty(rotate));
        name.translateYProperty().bind(getNameTranslateYProperty(rotate));

        // deselect before hidden
        visibleProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) {
                setSelected(false);
            }
        });
    }

    private DoubleBinding getNameTranslateXProperty(Rotate rotate) {
        DoubleBinding anchor = shapeItem.getMinXProperty().add(0);
        switch ((int)rotate.getAngle()) {
            case 180: case -180:
                anchor = shapeItem.getMaxXProperty().subtract(name.heightProperty());
                break;
            case -90: case 270:
                anchor = shapeItem.getMaxXProperty().add(0);
                break;
        }
        return translate.xProperty().add(anchor.multiply(scale.xProperty()));
    }

    private DoubleBinding getNameTranslateYProperty(Rotate rotate) {
        DoubleBinding anchor = shapeItem.getMinYProperty().add(0);
        switch ((int)rotate.getAngle()) {
            case -270: case 90:
            case 180: case -180:
                anchor = shapeItem.getMaxYProperty().add(0);
                break;
        }
        return translate.yProperty().add(anchor.multiply(scale.yProperty()));
    }

    public ObjectModel getModel() {
        return model;
    }

    public abstract ReadOnlyObjectProperty<Color> strokeColorProperty();
    public abstract ReadOnlyObjectProperty<Color> fillColorProperty();

    public StringProperty nameProperty() {
        return name.textProperty();
    }

    private ObjectProperty<ShapeItem> shapeProperty;

    public ObjectProperty<ShapeItem> shapeItemProperty() {
        if (shapeProperty == null) {
            shapeProperty = new SimpleObjectProperty<>()
            {
                @Override
                public void set(ShapeItem value) {
                    shapeItem.copyFrom(value);
                    shapeItem.save(getModel());
                    super.set(value);
                }
            };
        }
        return shapeProperty;
    }

    public boolean isSelected() {
        return selectionProperty().get();
    }

    public void setSelected(boolean value) {
        selectionProperty().set(value);
        if (value) {
            setAnimateOutline(Settings.isAnimateOutline());
        }
        else {
            setAnimateOutline(false);
        }
    }

    private BooleanProperty selectionProperty;

    public BooleanProperty selectionProperty() {
        if (selectionProperty == null) {
            selectionProperty = new SimpleBooleanProperty() {
                @Override
                public boolean get() {
                    super.get();
                    return !handles.isEmpty();
                }

                @Override
                public void set(boolean value) {
                    if (value && !get()) {
                        handles.addAll(shapeItem.getHandles(translate, scale, strokeColorProperty()));
                        getChildren().addAll(handles);
                        shapeItem.toShape().fillProperty().bind(fillColorProperty());
                        toFront();
                    }
                    else if (!value && get()) {
                        for (Shape handle : handles) {
                            getChildren().remove(handle);
                        }
                        handles.clear();
                        shapeItem.toShape().fillProperty().bind(new SimpleObjectProperty<>(Color.TRANSPARENT));
                    }
                    super.set(value);
                }
            };
        }

        return selectionProperty;
    }

    private Point2D offset;

    protected void onMouseClicked(MouseEvent me) {
    }

    protected void onMousePressed(MouseEvent me) {
        setSelected(true);
        offset = new Point2D(me.getX() - shapeItem.getX(), me.getY() - shapeItem.getY());
    }

    protected void onMouseDragged(MouseEvent me) {
        setCursor(Cursor.CLOSED_HAND);
        setLocation(me.getX() - offset.getX(), me.getY() - offset.getY());
    }

    protected void setLocation(double x, double y) {
        // Check for out-of-bounds conditions
        if (x < 0) {
            x = 0;
        }
        else if (x + shapeItem.getWidth() > imageDim.getWidth()) {
            x = imageDim.getWidth() - shapeItem.getWidth();
        }
        if (y < 0) {
            y = 0;
        }
        else if (y + shapeItem.getHeight() > imageDim.getHeight()) {
            y = imageDim.getHeight() - shapeItem.getHeight();
        }
        shapeItem.moveTo(x, y);
    }

    public void move(HorizontalDirection horizontal, VerticalDirection vertical, double deltaX, double deltaY) {
        Rotate rotate = AppUtils.getTransform(this, Rotate.class);
        double x = horizontal == null ? 0 : (horizontal == HorizontalDirection.LEFT ? -deltaX : deltaX);
        double y = vertical == null ? 0 : (vertical == VerticalDirection.UP ? -deltaY : deltaY);
        final double x1 = horizontal == null ? 0 : (horizontal == HorizontalDirection.LEFT ? deltaX : -deltaX);
        final double y1 = vertical == null ? 0 : (vertical == VerticalDirection.UP ? deltaY : -deltaY);
        switch ((int)rotate.getAngle()) {
            case 90:
                x = y;
                y = x1;
                break;
            case 180:
                x = x1;
                y = y1;
                break;
            case 270:
                x = y1;
                y = x;
                break;
        }
        setLocation(shapeItem.getX() + x, shapeItem.getY() + y);
        shapeProperty.set(shapeItem.createCopy());
    }

    protected void onMouseReleased(MouseEvent me) {
        setCursor(Cursor.DEFAULT);
        offset = null;
        shapeProperty.set(shapeItem.createCopy());
    }

    private ShapeItem createShapeItem() {
        if (getModel().getPolygon() != null) {
            return new PolygonItem(getModel(), imageDim);
        }
        return new RectangleItem(getModel(), imageDim);
    }

    public Bounds getBounds() {
        return shapeItem.getBounds();
    }

    public void setAnimateOutline(boolean animate) {
        if (animate) {
            AppUtils.addOutlineAnimation(shapeItem.toShape());
        }
        else {
            AppUtils.removeOutlineAnimation(shapeItem.toShape());
        }
    }
}
