package com.easymobo.openlabeler.preference;

import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.paint.Color;
import javafx.util.Callback;

import java.util.regex.Pattern;

public class NameColor
{
    private static final String SEPARATOR = "||";
    private StringProperty name;
    private ObjectProperty<Color> color;

    public static Callback<NameColor, Observable[]> extractor() {
        return (NameColor item) -> new Observable[] { item.nameProperty(), item.colorProperty() };
    }

    public NameColor() {
        this("");
    }

    public NameColor(String rep) {
        String parts[] = rep.split(Pattern.quote(SEPARATOR));
        if (parts.length >= 2) {
            init(parts[0], PreferenceUtil.toColor(parts[1]));
        }
        else if (parts.length == 1) {
            init(parts[0], Settings.getObjectStrokeColor());
        }
        else {
            init("", Settings.getObjectStrokeColor());
        }
    }

    private void init(String name, Color color) {
        this.name = new SimpleStringProperty(name);
        this.color = new SimpleObjectProperty(color);
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

    public ObjectProperty<Color> colorProperty() {
        return color;
    }
    public Color getColor() {
        return color.get();
    }
    public void setColor(Color v) {
        color.set(v);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof NameColor
                && getName().equals(((NameColor)obj).getName())
                && getColor().equals(((NameColor)obj).getColor());
    }
    @Override
    public String toString() {
        return name.get() + SEPARATOR + PreferenceUtil.fromColor(color.get());
    }
}
