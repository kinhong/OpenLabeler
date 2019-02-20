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

package com.easymobo.openlabeler.preference;

import javafx.beans.Observable;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.util.Callback;
import object_detection.protos.StringIntLabelMapOuterClass.StringIntLabelMapItem;

public class LabelMapItem
{
    private IntegerProperty id;
    private StringProperty name;
    private StringProperty displayName;

    public static Callback<LabelMapItem, Observable[]> extractor() {
        return (LabelMapItem item) -> new Observable[] { item.idProperty(), item.nameProperty(), item.displayNameProperty() };
    }

    public LabelMapItem(StringIntLabelMapItem item) {
        id = new SimpleIntegerProperty(item.getId());
        name = new SimpleStringProperty(item.getName());
        displayName = new SimpleStringProperty(item.getDisplayName());
    }

    public IntegerProperty idProperty() {
        return id;
    }
    public int getId() {
        return id.get();
    }
    public void setId(int v) {
        id.set(v);
    }

    public StringProperty nameProperty() {
        return name;
    }
    public String getName() {
        return name.get();
    }
    public void setName(String v) {
        name.set(v);
    }

    public StringProperty displayNameProperty() {
        return displayName;
    }
    public String getDisplayName() {
        return displayName.get();
    }
    public void setDisplayName(String v) {
        displayName.set(v);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LabelMapItem
                && getId() == ((LabelMapItem)obj).getId()
                && getName().equals(((LabelMapItem)obj).getName())
                && getDisplayName().equals(((LabelMapItem)obj).getDisplayName());
    }
}
