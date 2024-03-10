/*
 * Copyright (c) 2024. Kin-Hong Wong. All Rights Reserved.
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
 */

package com.easymobo.openlabeler;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.application.Preloader.StateChangeNotification;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import org.apache.commons.io.IOUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static javafx.application.Preloader.StateChangeNotification.Type.BEFORE_START;

public class OpenLabeler extends Application
{
    private static final String APP_HOME = ".openlabeler";
    public static final String APP_ICON = "icon.png";
    public static final String APP_CSS = "/css/OpenLabeler.css";

    private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

    @Override
    public void init() throws Exception {
        initLogging();
    }

    @Override
    public void start(final Stage stage) throws Exception {
        Task task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    // Attempt to get bundle using user's default locale.
                    ResourceBundle.getBundle("bundle");
                }
                catch (MissingResourceException mse) {
                    LOG.log(Level.WARNING, String.format("Can't get bundle for %s. Using en_US locale.", Locale.getDefault()), mse);Locale.setDefault(Locale.US);
                }
                try {
                    ResourceBundle bundle = ResourceBundle.getBundle("bundle");
                    LOG.info(bundle.getString("app.name") + " " + bundle.getString("app.version"));

                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/OpenLabeler.fxml"), bundle);
                    Parent root = loader.load();

                    stage.setOnCloseRequest(e -> {
                        OpenLabelerController controller = loader.getController();
                        if (!controller.canClose()) {
                            e.consume();
                            return;
                        }
                        controller.close();
                        Platform.exit();
                    });

                    stage.addEventHandler(WindowEvent.ANY, (event) -> {
                        OpenLabelerController controller = loader.getController();
                        controller.handleWindowEvent(event);
                    });

                    Platform.runLater(() -> {
                        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
                        Scene scene = new Scene(root, screenBounds.getWidth() * 0.8, screenBounds.getHeight() * 0.8); // 80% of monitor size
                        stage.setTitle(bundle.getString("app.name"));
                        stage.getIcons().add(new Image(getClass().getClassLoader().getResourceAsStream("icon.png")));
                        stage.setScene(scene);

                        stage.show();
                        stage.requestFocus();
                    });

                    notifyPreloader(new StateChangeNotification(BEFORE_START));
                }
                catch (Throwable ex) {
                    LOG.log(Level.SEVERE, "Unable to start application", ex);
                }

                return null;
            }
        };
        new Thread(task).start();
    }

    public static void main(String[] args) {
        System.setProperty("javafx.preloader", "com.easymobo.openlabeler.SplashScreenLoader");
        launch(OpenLabeler.class, args);
    }

    public static Stage createAboutStage(ResourceBundle bundle) {
        try {
            FXMLLoader loader = new FXMLLoader(OpenLabeler.class.getResource("/fxml/AboutPane.fxml"), bundle);
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle(bundle.getString("app.name"));
            stage.focusedProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue) {
                    stage.close();
                }
            });
            stage.initStyle(StageStyle.UNDECORATED);
            stage.setResizable(false);
            stage.setScene(new Scene(root));
            return stage;
        }
        catch (Exception ex) {
            LOG.log(Level.WARNING, "Failed to load ICNS file", ex);
        }

        return null;
    }

    private void initLogging() {
        try {
            Path logConfig = Paths.get(System.getProperty("user.home"), APP_HOME, "logging.properties");
            Path logDest = Paths.get(System.getProperty("user.home"), APP_HOME, "logs");
            if (!logConfig.toFile().exists()) {
                // Copy default log config file to APP_HOME
                logConfig.toFile().getParentFile().mkdirs();
                InputStream src = getClass().getClassLoader().getResourceAsStream("logging.properties");
                FileOutputStream dest = new FileOutputStream(logConfig.toFile());
                dest.write(IOUtils.toByteArray(src));
                src.close();
                dest.close();
            }
            logDest.toFile().mkdirs();

            FileInputStream is = new FileInputStream(logConfig.toFile());
            LogManager.getLogManager().readConfiguration(is);
            is.close();
        }
        catch (Exception ex) {
            System.err.println("Unable to initialize logger");
            ex.printStackTrace();
        }
    }
}
