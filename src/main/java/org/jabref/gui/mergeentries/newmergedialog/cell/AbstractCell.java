package org.jabref.gui.mergeentries.newmergedialog.cell;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

/**
 *
 */
public abstract class AbstractCell extends HBox {
    private final StringProperty text = new SimpleStringProperty();
    private final ObjectProperty<BackgroundTone> backgroundTone = new SimpleObjectProperty<>();

    public AbstractCell(String text, BackgroundTone backgroundTone) {
        backgroundToneProperty().addListener(invalidated -> setBackground(Background.fill(getBackgroundTone().color())));
        setPadding(new Insets(8));

        setText(text);
        setBackgroundTone(backgroundTone);
    }

    public AbstractCell(String text) {
        this(text, BackgroundTone.DARK);
    }

    public String getText() {
        return textProperty().get();
    }

    public StringProperty textProperty() {
        return text;
    }

    public void setText(String text) {
        textProperty().set(text);
    }

    public void setBackgroundTone(BackgroundTone backgroundTone) {
        backgroundToneProperty().set(backgroundTone);
    }

    public BackgroundTone getBackgroundTone() {
        return backgroundToneProperty().get();
    }

    public ObjectProperty<BackgroundTone> backgroundToneProperty() {
        return backgroundTone;
    }

    public enum BackgroundTone {
        LIGHT(Color.web("#F7F7F7")), DARK(Color.web("#FAFAFA"));
        private final Color color;

        BackgroundTone(Color color) {
            this.color = color;
        }

        public Color color() {
            return color;
        }
    }
}