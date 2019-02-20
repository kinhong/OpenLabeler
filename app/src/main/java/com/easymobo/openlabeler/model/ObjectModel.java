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

import javax.xml.bind.annotation.*;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlRootElement(name = "object")
@XmlType(propOrder = {"name", "pose", "truncated", "difficult", "boundBox"})
public class ObjectModel implements Cloneable
{
    private String name;
    private BoundBox boundBox;
    private String pose = "Unspecified";
    private Boolean truncated = false;
    private Boolean difficult =  false;

    public ObjectModel() {}

    public ObjectModel(String name, double xmin, double ymin, double xmax, double ymax) {
        this.name = name;
        this.boundBox = new BoundBox(xmin, ymin, xmax, ymax);
    }

    @XmlElement(name = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "bndbox")
    public BoundBox getBoundBox() {
        return boundBox;
    }

    public void setBoundBox(BoundBox boundBox) {
        this.boundBox = boundBox;
    }

    @XmlElement(name = "pose")
    public String getPose() {
        return pose;
    }

    public void setPose(String pose) {
        this.pose = pose;
    }

    @XmlElement(name = "truncated")
    @XmlJavaTypeAdapter(BooleanAdapter.class)
    public Boolean getTruncated() {
        return truncated;
    }

    public void setTruncated(Boolean truncated) {
        this.truncated = truncated;
    }

    @XmlElement(name = "difficult")
    @XmlJavaTypeAdapter(BooleanAdapter.class)
    public Boolean getDifficult() {
        return difficult;
    }

    public void setDifficult(Boolean difficult) {
        this.difficult = difficult;
    }

    @Override
    public Object clone() {
        ObjectModel model = new ObjectModel();
        model.setName(name);
        model.setBoundBox((BoundBox)boundBox.clone());
        return model;
    }
}
