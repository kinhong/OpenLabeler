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
import com.easymobo.openlabeler.preference.NameColor;
import com.easymobo.openlabeler.preference.Settings;
import com.easymobo.openlabeler.util.AppUtils;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

public class ObjectTag extends TagBase
{
    private ObjectModel model;
    private String action;

    private ReadOnlyObjectWrapper<Image> thumbProperty = new ReadOnlyObjectWrapper();
    private ReadOnlyObjectWrapper<Color> strokeColorProperty = new ReadOnlyObjectWrapper();
    private ReadOnlyObjectWrapper<Color> fillColorProperty = new ReadOnlyObjectWrapper();

    public ObjectTag(ImageView imageView, Translate translate, Scale scale, Rotate rotate, ObjectModel model) {
        this.model = model;
        init(imageView.getImage(), translate, scale, rotate);

        name.setText(model.getName());
        name.textProperty().addListener((observable, oldValue, newValue) -> {
            model.setName(newValue);
            Settings.recentNamesProperty.addName(newValue);
        });

        // object thumbnail
        thumbProperty.bind(Bindings.createObjectBinding(() -> {
            Bounds bounds = boundsProperty().get();
            SnapshotParameters parameters = new SnapshotParameters();
            parameters.setFill(Color.TRANSPARENT);
            parameters.setViewport(new Rectangle2D(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight()));

            WritableImage wi = new WritableImage((int)bounds.getWidth(), (int)bounds.getHeight());
            return imageView.snapshot(parameters, wi);
         }, boundsProperty()));

        strokeColorProperty.bind(Bindings.createObjectBinding(() -> {
            NameColor item = Settings.recentNamesProperty.getByPrefix(getModel().getName());
            return item == null ? Settings.getObjectStrokeColor() : item.getColor();
        },  Settings.recentNamesProperty));

        fillColorProperty.bind(Bindings.createObjectBinding(() -> {
            NameColor item = Settings.recentNamesProperty.getByPrefix(getModel().getName());
            return item == null ? Settings.getObjectFillColor() : AppUtils.applyAlpha(item.getColor(), 0.3);
        },  Settings.recentNamesProperty));
    }

    @Override
    protected void onMouseClicked(MouseEvent me) {
        if (me.getClickCount() != 2 || !name.localToScreen(name.getBoundsInLocal()).contains(me.getScreenX(), me.getScreenY())) {
            return;
        }
        NameEditor editor = new NameEditor(nameProperty().get());
        String label = editor.showPopup(me.getScreenX(), me.getScreenY(), getScene().getWindow());
        nameProperty().set(label);
        Settings.recentNamesProperty.addName(label);
    }

    @Override
    public ObjectModel getModel() {
        return model;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    @Override
    public ReadOnlyObjectProperty<Color> strokeColorProperty() {
        return strokeColorProperty;
    }

    @Override
    public ReadOnlyObjectProperty<Color> fillColorProperty() {
        return fillColorProperty;
    }

    public ReadOnlyObjectProperty<Image> thumbProperty() {
        return thumbProperty.getReadOnlyProperty();
    }
}
