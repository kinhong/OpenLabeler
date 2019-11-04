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

import com.easymobo.openlabeler.model.HintModel;
import com.easymobo.openlabeler.preference.Settings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

import java.util.logging.Logger;

public class HintTag extends TagBase
{
    private static final Logger LOG = Logger.getLogger(HintTag.class.getCanonicalName());
    private HintModel model;

    public HintTag(ImageView imageView, Translate translate, Scale scale, Rotate rotate, HintModel model) {
        this.model = model;
        init(imageView.getImage(), translate, scale, rotate);

        name.setText(String.format("%s (%.2f)", model.getName(), model.getScore()));
        name.setMouseTransparent(true);
    }

    @Override
    public HintModel getModel() {
        return model;
    }

    @Override
    public ObjectProperty<Color> strokeColorProperty() {
        return Settings.hintStrokeColorProperty;
    }

    @Override
    public ReadOnlyObjectProperty<Color> fillColorProperty() {
        return Settings.hintFillColorProperty;
    }

    private BooleanProperty hintConfirmProperty = new SimpleBooleanProperty();
    public BooleanProperty hintConfirmProperty() {
        return hintConfirmProperty;
    }

    @Override
    protected void onMouseClicked(MouseEvent me) {
        if (me.getButton().equals(MouseButton.PRIMARY) && me.getClickCount() == 2) {
            hintConfirmProperty.set(true);
        }
    }
}
