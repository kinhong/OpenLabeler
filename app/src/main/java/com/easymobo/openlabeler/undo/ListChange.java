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

import com.easymobo.openlabeler.ui.ObjectTag;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;

import java.util.Objects;

public class ListChange implements ChangeBase<ObjectTag>
{
   private String name;
   private ObjectTag target;
   private int index;
   private boolean added;
   private ObservableList<ObjectTag> property;

   public ListChange(ObservableList<ObjectTag> property, Change<? extends ObjectTag> change) {
      this.property = property;
      if (change.next()) {
         if (change.wasRemoved()) {
            target = change.getRemoved().get(0);
            added = false;
         }
         else if (change.wasAdded()) {
            target = change.getAddedSubList().get(0);
            added = true;
         }
         index = change.getFrom();
      }
   }

   public ListChange(ObservableList<ObjectTag> property, int index, ObjectTag target, boolean added) {
      this.property = property;
      this.target = target;
      this.index = index;
      this.added = added;
   }

   @Override
   public String getName() {
      return target.getAction();
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
      return new ListChange(property, index, target, !added);
   }

   @Override
   public int hashCode() {
      return Objects.hash(property, index, target, added);
   }


   @Override
   public boolean equals(Object other) {
      if (other instanceof ListChange) {
         ListChange that = (ListChange) other;
         return Objects.equals(this.property, that.property)
                 && Objects.equals(this.index, that.index)
                 && Objects.equals(this.target, that.target)
                 && Objects.equals(this.added, that.added);
      }
      else {
         return false;
      }
   }
}
