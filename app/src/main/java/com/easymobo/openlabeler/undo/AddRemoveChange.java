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

import com.easymobo.openlabeler.tag.ObjectTag;
import javafx.collections.ObservableList;

import java.util.Objects;

public class AddRemoveChange implements ChangeBase<ObjectTag>
{
    private String name;
    private int index;
    private ObjectTag target;
    private ObservableList<ObjectTag> property;
    private boolean added;

    public AddRemoveChange(String name, int index, ObjectTag target, ObservableList<ObjectTag> property, boolean added) {
        this.name = name;
        this.index = index;
        this.target = target;
        this.added = added;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void redo() {
        if (added) {
            property.add(index, target);
        }
        else {
            property.remove(target);
        }
    }

    @Override
    public ChangeBase<ObjectTag> invert() {
        return new AddRemoveChange(name, index, target, property, !added);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, index, target, property, added);
    }


    @Override
    public boolean equals(Object other) {
        if (other instanceof AddRemoveChange) {
            AddRemoveChange that = (AddRemoveChange) other;
            return Objects.equals(this.name, that.name)
                    && Objects.equals(this.index, that.index)
                    && Objects.equals(this.target, that.target)
                    && Objects.equals(this.property, that.property)
                    && Objects.equals(this.added, that.added);
        }
        else {
            return false;
        }
    }
}
