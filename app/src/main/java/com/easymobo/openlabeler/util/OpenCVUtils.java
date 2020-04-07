package com.easymobo.openlabeler.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritablePixelFormat;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class OpenCVUtils
{
   private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

   static {
      try {
         nu.pattern.OpenCV.loadLocally();
         LOG.info("Loaded OpenCV: " + Core.getVersionString());
      }
      catch(Throwable ex) {
        LOG.log(Level.SEVERE, "Unable to load OpenCV", ex);
      }
   }

   /**
    * Return OpenCV MAT in CvType.CV_8UC4
    */
   public static Mat imageToMat(Image image) {
      int width = (int) image.getWidth();
      int height = (int) image.getHeight();
      byte[] buffer = new byte[width * height * 4];

      PixelReader reader = image.getPixelReader();
      WritablePixelFormat<ByteBuffer> format = WritablePixelFormat.getByteBgraInstance();
      reader.getPixels(0, 0, width, height, format, buffer, 0, width * 4);

      Mat mat = new Mat(height, width, CvType.CV_8UC4);
      mat.put(0, 0, buffer);
      return mat;
   }

   /**
    * Source OpenCV MAT is assumed to be in CvType.CV_8UC4
    * Return JavaFX Image in BGR format
    */
   public static Image matToImage(Mat matImg) {
      int width = matImg.width(), height = matImg.height();

      Mat converted = new Mat();
      Imgproc.cvtColor(matImg, converted, Imgproc.COLOR_RGBA2BGR);
      byte[] pixels = new byte[width * height * 3];
      converted.get(0, 0, pixels);

      BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
      image.getRaster().setDataElements(0, 0, width, height, pixels);
      return SwingFXUtils.toFXImage(image, null);
   }

   public static Image createMasked(Image image, List<Double> pts) {
      Mat src = imageToMat(image);

      Mat mask = Mat.zeros(src.size(), CvType.CV_8U);
      Point[] points = IntStream.range(0, pts.size()/2).mapToObj(i -> new Point(pts.get(i*2), pts.get(i*2+1)))
            .collect(Collectors.toList()).toArray(new Point[] {});
      MatOfPoint polygon = new MatOfPoint(points);
      Imgproc.fillConvexPoly(mask, polygon, Scalar.all(255));

      Mat dst = new Mat(src.size(), src.type(), Scalar.all(255));
      src.copyTo(dst, mask);
      return matToImage(dst);
   }
}
