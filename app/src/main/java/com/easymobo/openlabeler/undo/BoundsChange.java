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
import javafx.geometry.Bounds;
import org.reactfx.Change;

import java.util.Objects;

public class BoundsChange extends PropertyChange<Bounds>
{
   public BoundsChange(String name, Property<Bounds> property, Bounds oldValue, Bounds newValue) {
      super(name, property, oldValue, newValue);
   }

   public BoundsChange(String name, Property<Bounds> property, Change<Bounds> c) {
      super(name, property, c.getOldValue(), c.getNewValue());
   }

   @Override
   public void redo() {
      property.setValue(newValue);
   }

   @Override
   public BoundsChange invert() {
      return new BoundsChange(name, property, newValue, oldValue);
   }

   @Override
   public boolean equals(Object other) {
      if (other instanceof BoundsChange) {
         BoundsChange that = (BoundsChange) other;
         return Objects.equals(this.name, that.name)
                 && Objects.equals(this.property, that.property)
                 && Objects.equals(this.oldValue, that.oldValue)
                 && Objects.equals(this.newValue, that.newValue);
      }
      return false;
   }
}
