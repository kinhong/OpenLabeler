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

package com.easymobo.openlabeler.model;

import com.easymobo.openlabeler.model.ModelUtil.BooleanAdapter;
import javafx.scene.image.Image;

import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "annotation")
@XmlType(propOrder = {"folder", "filename", "path", "source", "size", "segmented", "objects"})
public class Annotation
{
    private File file;
    private String filename, path, folder;
    private List<ObjectModel> objects = new ArrayList();
    private Source source = new Source();
    private Size size = new Size();

    public Annotation() {}

    public Annotation(File file, Image image) {
        setFile(file);
        this.size = new Size(image);
    }

    @XmlTransient
    public File getFile() {
        return this.file;
    }

    public void setFile(File file) {
        this.file = file;
        setFilename(file.getName());
        setPath(file.getAbsolutePath());
        setFolder(file.getParentFile().getName());
    }

    @XmlElement(name = "folder")
    public String getFolder() {
        return folder;
    }
    public void setFolder(String folder) {
        this.folder = folder;
    }

    @XmlElement(name = "filename")
    public String getFilename() {
        return filename;
    }
    public void setFilename(String name) {
        this.filename = name;
    }

    @XmlElement(name = "path")
    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }

    @XmlElement(name = "source")
    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    @XmlElement(name = "size")
    public Size getSize() {
        return size;
    }

    public void setSize(Size size) {
        this.size = size;
    }

    @XmlElement(name = "segmented")
    @XmlJavaTypeAdapter(BooleanAdapter.class)
    public Boolean getSegmented() {
        return false;
    }

    @XmlElements({ @XmlElement(name="object", type = ObjectModel.class) })
    public List<ObjectModel> getObjects() {
        return this.objects;
    }

    public void setObjects(List<ObjectModel> objects) {
        this.objects = objects;
    }

    @XmlRootElement(name = "source")
    @XmlType(propOrder = {"database"})
    public static class Source
    {
        private String database = "Unknown";

        @XmlElement(name = "database")
        public String getDatabase() {
            return this.database;
        }
        public void setDatabase(String database) {
            this.database =  database;
        }
    }

    @XmlRootElement(name = "size")
    @XmlType(propOrder = {"width", "height", "depth"})
    public static class Size
    {
        private Image image;
        private int width;
        private int height;

        public Size() {}
        public Size(Image image) {
            this.image = image;
        }

        @XmlTransient
        public Image getImage() {
            return image;
        }

        public void setImage(Image image) {
            this.image = image;
            setWidth((int)Math.round(image.getWidth()));
            setHeight((int)Math.round(image.getHeight()));
        }

        @XmlElement(name = "width")
        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        @XmlElement(name = "height")
        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        @XmlElement(name = "depth")
        public int getDepth() {
            return 3;
        }
    }
}
