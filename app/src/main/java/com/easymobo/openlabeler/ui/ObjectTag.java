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
import com.easymobo.openlabeler.preference.Settings;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Bounds;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
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

    public ObjectTag(Image image, Translate translate, Scale scale, Rotate rotate, ObjectModel model) {
        super(image, translate, scale, rotate, model);
        this.model = model;

        name.setText(model.getName());
        name.textProperty().addListener((observable, oldValue, newValue) -> {
            model.setName(newValue);
            Settings.recentNames.add(newValue);
        });

        // object thumbnail
        thumbProperty.bind(Bindings.createObjectBinding(() -> {
            PixelReader reader = image.getPixelReader();
            Bounds b = boundsProperty().get();
            return new WritableImage(reader, (int) b.getMinX(), (int) b.getMinY(), (int) b.getWidth(), (int) b.getHeight());
        }, boundsProperty()));
    }

    @Override
    protected void onMouseClicked(MouseEvent me) {
        if (me.getClickCount() != 2 || !name.localToScreen(name.getBoundsInLocal()).contains(me.getScreenX(), me.getScreenY())) {
            return;
        }
        NameEditor editor = new NameEditor(nameProperty().get());
        String label = editor.showPopup(me.getScreenX(), me.getScreenY(), getScene().getWindow());
        nameProperty().set(label);
        Settings.recentNames.add(label);
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
    public ObjectProperty<Color> strokeColorProperty() {
        return Settings.objectStrokeColorProperty;
    }

    @Override
    public ReadOnlyObjectProperty<Color> fillColorProperty() {
        return Settings.objectFillColorProperty;
    }

    private ReadOnlyObjectWrapper<Image> thumbProperty = new ReadOnlyObjectWrapper();
    public ReadOnlyObjectProperty<Image> thumbProperty() {
        return thumbProperty.getReadOnlyProperty();
    }
}
