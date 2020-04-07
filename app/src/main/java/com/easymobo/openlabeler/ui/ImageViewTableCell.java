package com.easymobo.openlabeler.ui;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;

public class ImageViewTableCell<S, T> extends TableCell<S, T>
{
   private static final double VIEW_SIZE = 40;
   private static final double TOOLTIP_SIZE = 250;

   private ImageView imageView;
   private Tooltip tooltip;

   public ImageViewTableCell() {
      setAlignment(Pos.CENTER);
      setPrefSize(VIEW_SIZE, VIEW_SIZE);

      imageView = new ImageView();
      imageView.setFitHeight(VIEW_SIZE);
      imageView.setFitWidth(VIEW_SIZE);
      imageView.setPreserveRatio(true);
      setGraphic(imageView);
   }

   @Override
   protected void updateItem(T item, boolean empty) {
      super.updateItem(item, empty);

      var image = Image.class.cast(item);
      imageView.setImage(image);

      if (image != null) {
         var popupImageView = new ImageView(image);
         popupImageView.setFitHeight(TOOLTIP_SIZE);
         popupImageView.setFitWidth(TOOLTIP_SIZE);
         popupImageView.setPreserveRatio(true);
         Tooltip tooltip = new Tooltip("");
         tooltip.getStyleClass().add("imageTooltip");
         tooltip.setShowDelay(Duration.millis(250));
         tooltip.setGraphic(popupImageView);
         Tooltip.install(this, tooltip);
      }
   }
}
