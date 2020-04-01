package com.easymobo.openlabeler.ui;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;


/**
 * See <a href="https://stackoverflow.com/a/17289707">How to add border to panel of javafx?</a>
 * Places content in a bordered pane with a title.
 */
public class BorderedTitledPane extends StackPane {
   private StringProperty title = new SimpleStringProperty();
   private ObjectProperty<Node> graphic = new SimpleObjectProperty<>();
   private ObjectProperty<Node> content = new SimpleObjectProperty<>();
   private ObjectProperty<Pos>  titleAlignment = new SimpleObjectProperty<>();
   // todo other than TOP_LEFT other alignments aren't really supported correctly, due to translation fudge for indentation of the title label in css => best to implement layoutChildren and handle layout there.
   // todo work out how to make content the default node for fxml so you don't need to write a <content></content> tag.

   public BorderedTitledPane() {
      this("", null);
   }

   public BorderedTitledPane(String titleString, Node contentNode) {
      final Label titleLabel = new Label();
      titleLabel.textProperty().bind(Bindings.concat(title, " "));
      titleLabel.getStyleClass().add("bordered-titled-title");
      titleLabel.graphicProperty().bind(graphic);

      titleAlignment.addListener(new InvalidationListener() {
         @Override
         public void invalidated(Observable observable) {
            StackPane.setAlignment(titleLabel, titleAlignment.get());
         }
      });

      final StackPane contentPane = new StackPane();

      getStyleClass().add("bordered-titled-border");
      getChildren().addAll(titleLabel, contentPane);

      content.addListener(new InvalidationListener() {
         @Override
         public void invalidated(Observable observable) {
            if (content.get() == null) {
               contentPane.getChildren().clear();
            } else {
               if (!content.get().getStyleClass().contains("bordered-titled-content")) {
                  content.get().getStyleClass().add("bordered-titled-content");  // todo would be nice to remove this style class when it is no longer required.
               }
               contentPane.getChildren().setAll(content.get());
            }
         }
      });

      titleAlignment.set(Pos.TOP_LEFT);
      this.title.set(titleString);
      this.content.set(contentNode);
   }

   public String getTitle() {
      return title.get();
   }

   public StringProperty getTitleStringProperty() {
      return title;
   }

   public void setTitle(String title) {
      this.title.set(title);
   }

   public Pos getTitleAlignment() {
      return titleAlignment.get();
   }

   public ObjectProperty<Pos> titleAlignmentProperty() {
      return titleAlignment;
   }

   public void setTitleAlignment(Pos titleAlignment) {
      this.titleAlignment.set(titleAlignment);
   }

   public Node getContent() {
      return content.get();
   }

   public ObjectProperty<Node> contentProperty() {
      return content;
   }

   public void setContent(Node content) {
      this.content.set(content);
   }

   public Node getGraphic() {
      return graphic.get();
   }

   public ObjectProperty<Node> graphicProperty() {
      return graphic;
   }

   public void setGraphic(Node graphic) {
      this.graphic.set(graphic);
   }
}