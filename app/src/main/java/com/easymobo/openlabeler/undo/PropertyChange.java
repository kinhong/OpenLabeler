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

package com.easymobo.openlabeler.undo;

import javafx.beans.property.Property;

import java.util.Objects;

public abstract class PropertyChange<T> implements ChangeBase<T>
{
    protected String name;
    protected final Property<T> property;
    protected final T oldValue;
    protected final T newValue;

    public PropertyChange(String name, Property<T> property, T oldValue, T newValue) {
        this.name = name;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.property = property;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, property, oldValue, newValue);
    }
}
