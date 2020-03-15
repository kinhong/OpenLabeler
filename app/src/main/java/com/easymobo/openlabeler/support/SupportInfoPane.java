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

package com.easymobo.openlabeler.support;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import org.apache.commons.lang3.StringUtils;

import java.lang.invoke.MethodHandles;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SupportInfoPane extends DialogPane
{
    @FXML
    private TextArea text;
    @FXML
    private ButtonType copyToClipboard;

    private static final Logger LOG = Logger.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());

    private ResourceBundle bundle = ResourceBundle.getBundle("bundle");

    public SupportInfoPane() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/SupportInfoPane.fxml"));
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to load FXML", ex);
        }

        ButtonType copyToClipboard = new ButtonType(bundle.getString("menu.copyToClipboard"), ButtonBar.ButtonData.APPLY);
        getButtonTypes().addAll(copyToClipboard, ButtonType.CLOSE);

        final Button btn = (Button) lookupButton(copyToClipboard);
        btn.addEventFilter(ActionEvent.ACTION, event -> {
            final ClipboardContent content = new ClipboardContent();
            content.put(DataFormat.PLAIN_TEXT, text.getText());
            Clipboard.getSystemClipboard().setContent(content);
            event.consume();
        });
    }

    public void showAndWait() {
        Dialog dialog = new Dialog();
        dialog.setDialogPane(this);
        dialog.setTitle(bundle.getString("menu.supportInfo"));
        dialog.setResizable(true);

        // Collect support information asynchronously
        new Thread(() -> gatherSupportInfo(), "Support Information").run();
        dialog.showAndWait();
    }

    private void gatherSupportInfo() {
        String sep = " / ";
        StringBuilder sb = new StringBuilder();
        sb.append(System.getProperty("os.name", "?") + " (" +
                System.getProperty("os.version", "?") + ", " + System.getProperty("os.arch", "?") + ")" +
                sep + System.getProperty("file.encoding", "?") +
                sep + System.getProperty("user.language", "?") +
                sep + System.getProperty("user.country", "?") +
                sep + System.getProperty("java.vendor", "?") +
                " " + getJavaVersion());
        Runtime rt = Runtime.getRuntime();
        NumberFormat fmt = new DecimalFormat("#,###");
        sb.append(System.lineSeparator());
        sb.append("Memory: Max=").append(fmt.format(rt.maxMemory()));
        sb.append(";  Total=").append(fmt.format(rt.totalMemory()));
        sb.append(";  Free=").append(fmt.format(rt.freeMemory()));
        sb.append(";  CPUs=").append(rt.availableProcessors());

        sb.append(System.lineSeparator());
        sb.append(System.lineSeparator());

        Properties props = System.getProperties();
        List<String> keys = new ArrayList(props.keySet());
        Collections.sort(keys);
        keys.forEach(key -> sb.append(key).append(":").append(props.getProperty(key)).append(System.lineSeparator()));

        Platform.runLater(() -> {
            text.setText(sb.toString());
            text.setScrollTop(0.0);
        });
    }

    public static String getJavaVersion() {
        String version = System.getProperty("java.runtime.version");
        if (StringUtils.isBlank(version)) {
            version = System.getProperty("java.version", "?");
        }
        return version;
    }
}
