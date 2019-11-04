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

import com.easymobo.openlabeler.model.ObjectModel;
import com.easymobo.openlabeler.util.Util;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.*;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.ImageCursor;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.TransformChangedEvent;
import javafx.scene.transform.Translate;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class TagBase extends Group
{
    @FXML
    protected Rectangle rect;
    @FXML
    protected Label name;

    private static final int HANDLE_SIZE = 8;
    static final int MIN_SIZE = HANDLE_SIZE * 3;

    private enum Location
    {
        NW, NE, SE, SW
    }

    private static final Cursor cursorNW = Cursor.CROSSHAIR;//createImageCursor("resize_nw.png");
    private static final Cursor cursorNE = Cursor.CROSSHAIR;//createImageCursor("resize_ne.png");
    private static final Cursor cursorSE = Cursor.CROSSHAIR;//createImageCursor("resize_se.png");
    private static final Cursor cursorSW = Cursor.CROSSHAIR;//createImageCursor("resize_sw.png");

    private static final Logger LOG = Logger.getLogger(TagBase.class.getCanonicalName());

    protected ResourceBundle bundle;
    protected Dimension2D imageDim;
    protected List<Rectangle> handles = new ArrayList();
    protected Translate translate;
    protected Scale scale;

    public TagBase() {
        bundle = ResourceBundle.getBundle("bundle");
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

    public void init(Image image, Translate translate, Scale scale, Rotate rotate) {
        this.imageDim = new Dimension2D(image.getWidth(), image.getHeight());
        this.translate = translate;
        this.scale = scale;

        // Prevent name from rotating so it is always upright
        this.getTransforms().add(rotate);
        name.getTransforms().add(new Rotate(-rotate.getAngle(), 0, 0));
        rotate.addEventFilter(TransformChangedEvent.TRANSFORM_CHANGED, event -> {
            Util.getTransform(name, Rotate.class).setAngle(-rotate.getAngle());
            name.translateXProperty().bind(getNameTranslateXProperty(rotate));
            name.translateYProperty().bind(getNameTranslateYProperty(rotate));
        });
        name.translateXProperty().bind(getNameTranslateXProperty(rotate));
        name.translateYProperty().bind(getNameTranslateYProperty(rotate));

        rect.setX(getModel().getBoundBox().getXMin());
        rect.setY(getModel().getBoundBox().getYMin());
        rect.setWidth(getModel().getBoundBox().getXMax() - getModel().getBoundBox().getXMin());
        rect.setHeight(getModel().getBoundBox().getYMax() - getModel().getBoundBox().getYMin());
        rect.getTransforms().addAll(translate, scale);
        rect.setFill(Color.TRANSPARENT);
        rect.setStrokeWidth(2 / scale.getX());
        rect.strokeProperty().bind(strokeColorProperty());
        scale.addEventHandler(TransformChangedEvent.TRANSFORM_CHANGED, event -> {
            rect.setStrokeWidth(2 / scale.getX()); // Retain box stroke width at all zoom levels
        });

        rect.setOnMouseClicked(this::onMouseClicked);
        rect.setOnMousePressed(this::onMousePressed);
        rect.setOnMouseDragged(this::onMouseDragged);
        rect.setOnMouseReleased(event -> onMouseReleased(event));

        // deselect before hidden
        visibleProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) {
                setSelected(false);
            }
        });
    }

    private DoubleBinding getNameTranslateXProperty(Rotate rotate) {
        DoubleBinding anchor = rect.xProperty().add(0);
        switch ((int)rotate.getAngle()) {
            case 180: case -180:
                anchor = rect.xProperty().add(rect.widthProperty()).subtract(name.heightProperty());
                break;
            case -90: case 270:
                anchor = rect.xProperty().add(rect.widthProperty());
                break;
        }
        return translate.xProperty().add(anchor.multiply(scale.xProperty()));
    }

    private DoubleBinding getNameTranslateYProperty(Rotate rotate) {
        DoubleBinding anchor = rect.yProperty().add(0);
        switch ((int)rotate.getAngle()) {
            case -270: case 90:
                anchor = rect.yProperty().add(rect.heightProperty());
                break;
            case 180: case -180:
                anchor = rect.yProperty().add(rect.heightProperty());
                break;
        }
        return translate.yProperty().add(anchor.multiply(scale.yProperty()));
    }

    public abstract ObjectModel getModel();
    public abstract ReadOnlyObjectProperty<Color> strokeColorProperty();
    public abstract ReadOnlyObjectProperty<Color> fillColorProperty();

    public StringProperty nameProperty() {
        return name.textProperty();
    }

    public ReadOnlyObjectProperty<Bounds> coordsProperty() {
        return boundsProperty();
    }

    private ObjectProperty<Bounds> boundsProperty;

    public ObjectProperty<Bounds> boundsProperty() {
        if (boundsProperty == null) {
            boundsProperty = new SimpleObjectProperty<>()
            {
                @Override
                public Bounds get() {
                    super.get();
                    return getRectBounds();
                }

                @Override
                public void set(Bounds value) {
                    rect.setX(value.getMinX());
                    rect.setY(value.getMinY());
                    rect.setWidth(value.getWidth());
                    rect.setHeight(value.getHeight());
                    getModel().getBoundBox().setXMin(value.getMinX());
                    getModel().getBoundBox().setYMin(value.getMinY());
                    getModel().getBoundBox().setXMax(value.getMaxX());
                    getModel().getBoundBox().setYMax(value.getMaxY());
                    super.set(value);
                }
            };
        }
        return boundsProperty;
    }

    public boolean isSelected() {
        return selectionProperty().get();
    }

    public void setSelected(boolean value) {
        selectionProperty().set(value);
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
                        createResizeHandles();
                        rect.fillProperty().bind(fillColorProperty());
                        toFront();
                    }
                    else if (!value && get()) {
                        for (Rectangle handle : handles) {
                            getChildren().remove(handle);
                        }
                        handles.clear();
                        rect.fillProperty().bind(new SimpleObjectProperty<>(Color.TRANSPARENT));
                    }
                    super.set(value);
                }
            };
        }

        return selectionProperty;
    }

    Point2D offset;

    protected void onMouseClicked(MouseEvent me) {
    }

    protected void onMousePressed(MouseEvent me) {
        setSelected(true);
        offset = new Point2D(me.getX() - rect.getX(), me.getY() - rect.getY());
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
        else if (x + rect.getWidth() > imageDim.getWidth()) {
            x = imageDim.getWidth() - rect.getWidth();
        }
        if (y < 0) {
            y = 0;
        }
        else if (y + rect.getHeight() > imageDim.getHeight()) {
            y = imageDim.getHeight() - rect.getHeight();
        }
        rect.setX(x);
        rect.setY(y);
    }

    public void move(HorizontalDirection horizontal, VerticalDirection vertical, double deltaX, double deltaY) {
        Rotate rotate = Util.getTransform(this, Rotate.class);
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
        setLocation(rect.getX() + x, rect.getY() + y);
        boundsProperty().set(getRectBounds());
    }

    protected void onMouseReleased(MouseEvent me) {
        setCursor(Cursor.DEFAULT);
        offset = null;
        boundsProperty().set(getRectBounds());
    }

    private void createResizeHandles() {
        // 4 corner resize handles
        Rectangle resizeNW = createHandle(Location.NW);
        Rectangle resizeNE = createHandle(Location.NE);
        Rectangle resizeSE = createHandle(Location.SE);
        Rectangle resizeSW = createHandle(Location.SW);

        setUpDragging(resizeNW, Location.NW, cursorNW);
        setUpDragging(resizeNE, Location.NE, cursorNE);
        setUpDragging(resizeSE, Location.SE, cursorSE);
        setUpDragging(resizeSW, Location.SW, cursorSW);
    }

    private Rectangle createHandle(Location location) {
        Rectangle handle = new Rectangle(HANDLE_SIZE, HANDLE_SIZE);
        handle.fillProperty().bind(strokeColorProperty());
        switch (location) {
            case NW:
                handle.xProperty().bind(rect.xProperty().subtract(handle.widthProperty().divide(2)));
                handle.yProperty().bind(rect.yProperty().subtract(handle.heightProperty().divide(2)));
                break;
            case NE:
                handle.xProperty().bind(rect.xProperty().add(rect.widthProperty()).subtract(handle.widthProperty().divide(2)));
                handle.yProperty().bind(rect.yProperty().subtract(handle.heightProperty().divide(2)));
                break;
            case SE:
                handle.xProperty().bind(rect.xProperty().add(rect.widthProperty()).subtract(handle.widthProperty().divide(2)));
                handle.yProperty().bind(rect.yProperty().add(rect.heightProperty()).subtract(handle.heightProperty().divide(2)));
                break;
            case SW:
                handle.xProperty().bind(rect.xProperty().subtract(handle.widthProperty().divide(2)));
                handle.yProperty().bind(rect.yProperty().add(rect.heightProperty()).subtract(handle.heightProperty().divide(2)));
                break;
        }

        // Maintain constant handle size at different zoom level
        scale.addEventHandler(TransformChangedEvent.TRANSFORM_CHANGED, event -> {
            handle.setWidth(HANDLE_SIZE / scale.getX());
            handle.setHeight(HANDLE_SIZE / scale.getY());
        });

        handle.setWidth(HANDLE_SIZE / scale.getX());
        handle.setHeight(HANDLE_SIZE / scale.getY());
        handle.getTransforms().addAll(translate, scale);
        handles.add(handle);
        getChildren().add(handle);
        return handle;
    }

    private void setUpDragging(Rectangle handle, Location location, Cursor cursor) {
        handle.setCursor(cursor);
        handle.setOnMousePressed(event -> {
            switch (location) {
                case NW:
                    offset = new Point2D(event.getX() - rect.getX(), event.getY() - rect.getY());
                    break;
                case NE:
                    offset = new Point2D(event.getX() - (rect.getX() + rect.getWidth()), event.getY() - rect.getY());
                    break;
                case SE:
                    offset = new Point2D(event.getX() - (rect.getX() + rect.getWidth()), event.getY() - (rect.getY() + rect.getHeight()));
                    break;
                case SW:
                    offset = new Point2D(event.getX() - rect.getX(), event.getY() - (rect.getY() + rect.getHeight()));
                    break;
            }
        });
        handle.setOnMouseDragged(event -> updateDraggedHandle(location, event));
        handle.setOnMouseReleased(event -> {
            offset = null;
            boundsProperty().set(getRectBounds());
        });
    }

    private void updateDraggedHandle(Location location, MouseEvent event) {
        if (offset == null) {
            return;
        }
        // X location
        double x1, x2, y1, y2;
        if (location == Location.NW || location == Location.SW) {
            x1 = event.getX() - offset.getX();
            x2 = rect.getX() + rect.getWidth();
            if (x1 < 0) {
                x1 = 0;
            }
            else if (x1 > x2 - MIN_SIZE) {
                x1 = x2 - MIN_SIZE;
            }
        }
        else {
            x1 = rect.getX();
            x2 = event.getX() - offset.getX();
            if (x2 > imageDim.getWidth()) {
                x2 = imageDim.getWidth();
            }
            else if (x2 < x1 + MIN_SIZE) {
                x2 = x1 + MIN_SIZE;
            }
        }
        // Y location
        if (location == Location.NW || location == Location.NE) {
            y1 = event.getY() - offset.getY();
            y2 = rect.getY() + rect.getHeight();
            if (y1 < 0) {
                y1 = 0;
            }
            else if (y1 > y2 - MIN_SIZE) {
                y1 = y2 - MIN_SIZE;
            }
        }
        else {
            y1 = rect.getY();
            y2 = event.getY() - offset.getY();
            if (y2 > imageDim.getHeight()) {
                y2 = imageDim.getHeight();
            }
            else if (y2 < y1 + MIN_SIZE) {
                y2 = y1 + MIN_SIZE;
            }
        }
        rect.setX(x1);
        rect.setY(y1);
        rect.setWidth(x2 - x1);
        rect.setHeight(y2 - y1);
    }

    private static ImageCursor createImageCursor(String path) {
        Image image = new Image(path, true);
        return new ImageCursor(image, image.getWidth() / 2, image.getHeight() / 2);
    }

    private Bounds getRectBounds() {
        return new BoundingBox(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
    }
}
