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

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.paint.Color;

import java.util.function.Function;
import java.util.prefs.Preferences;

public class PreferenceUtil
{
    public static class BooleanPrefProperty extends SimpleBooleanProperty
    {
        private final boolean defVal;

        public BooleanPrefProperty(Preferences pref, String baseKey, boolean defVal) {
            super(pref, baseKey, pref.getBoolean(baseKey, defVal));
            this.defVal = defVal;
            this.addListener((observable, oldVal, newVal) -> {
                ((Preferences)getBean()).putBoolean(getName(), newVal);
            });
        }

        @Override
        public void set(boolean use) {
            ((Preferences)getBean()).putBoolean(getName(), use);
            super.set(use);
        }
    }

    public static class IntegerPrefProperty extends SimpleIntegerProperty
    {
        private final int defVal;

        public IntegerPrefProperty(Preferences pref, String baseKey, int defVal) {
            super(pref, baseKey, pref.getInt(baseKey, defVal));
            this.defVal = defVal;
            this.addListener((observable, oldVal, newVal) -> {
                ((Preferences)getBean()).putInt(getName(), newVal.intValue());
            });
        }

        @Override
        public void set(int val) {
            ((Preferences)getBean()).putInt(getName(), val);
            super.set(val);
        }
    }

    public static class StringPrefProperty extends SimpleStringProperty
    {
        private final String defVal;

        public StringPrefProperty(Preferences pref, String baseKey, String defVal) {
            super(pref, baseKey, pref.get(baseKey, defVal));
            this.defVal = defVal;
            this.addListener((observable, oldVal, newVal) -> {
                ((Preferences)getBean()).put(getName(), newVal);
            });
        }

        @Override
        public void set(String val) {
            ((Preferences)getBean()).put(getName(), val);
            super.set(val);
        }
    }

    public static class ObjectPrefProperty<T> extends SimpleObjectProperty<T>
    {
        private final T defVal;
        private final Function<String, T> fromString;
        private final Function<T, String> toString;

        public ObjectPrefProperty(Preferences pref, String baseKey, T defVal, Function<String, T> fromString, Function<T, String> toString)  {
            super(pref, baseKey, fromString.apply(pref.get(baseKey, toString.apply(defVal))));
            this.defVal = defVal;
            this.fromString = fromString;
            this.toString = toString;
            this.addListener((observable, oldVal, newVal) -> {
                ((Preferences)getBean()).put(getName(), toString.apply(newVal));
            });
        }

        @Override
        public void set(T val) {
            ((Preferences)getBean()).put(getName(), toString.apply(val));
            super.set(val);
        }
    }

    public static class ColorPrefProperty extends ObjectPrefProperty<Color>
    {
        public ColorPrefProperty(Preferences pref, String baseKey, Color defVal) {
            super(pref, baseKey, defVal, PreferenceUtil::toColor, PreferenceUtil::fromColor);
        }
    }

    public static String fromColor(Color color) {
        return String.valueOf(
                (int) (color.getRed() * 0xFF) |
                ((int) (color.getGreen() * 0xFF)) << 010 |
                ((int) (color.getBlue() * 0xFF)) << 020 |
                ((int) (color.getOpacity() * 0xFF)) << 030);
    }

    public static Color toColor(String c) {
        int color = Integer.valueOf(c);
        return Color.rgb(
                color & 0xFF,
                (color >>> 010) & 0xFF,
                (color >>> 020) & 0xFF,
                (color >>> 030) / 255d);
    }
}
