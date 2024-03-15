module com.easymobo.openlabeler.app {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires javafx.web;
    requires javafx.swing;
    requires org.apache.commons.io;
    requires java.logging;
    requires jakarta.xml.bind;
    requires org.apache.commons.lang3;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign;
    requires org.apache.commons.collections4;
    requires org.apache.commons.codec;
    requires easybind;
    requires opencv;
    requires com.fasterxml.jackson.databind;
    requires java.prefs;
    requires reactfx;
    requires org.fxmisc.undo;
    requires proto;
    requires com.github.dockerjava.core;
    requires com.github.dockerjava.api;
    requires com.github.dockerjava.transport;
    requires com.github.dockerjava.transport.httpclient5;
    requires org.tensorflow.core.api;
    requires org.tensorflow.ndarray;
    requires com.google.protobuf;
    requires org.tensorflow;

    exports com.easymobo.openlabeler;
}