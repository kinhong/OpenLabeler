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

package com.easymobo.openlabeler.model;

import com.easymobo.openlabeler.model.ModelUtil.BooleanAdapter;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import java.util.List;
import java.util.stream.Collectors;

@XmlRootElement(name = "object")
@XmlType(propOrder = {"name", "pose", "truncated", "difficult", "boundBox", "polygon"})
public class ObjectModel implements Cloneable
{
    private String name;
    private BoundBox boundBox;
    private List<Double> polygon;
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

    @XmlElement(name = "polygon")
    @XmlJavaTypeAdapter(ModelUtil.OneBasedPointListAdapter.class)
    public List<Double> getPolygon() {
        return polygon;
    }

    public void setPolygon(List<Double> polygon) {
        this.polygon = polygon;
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

    public double area() {
        if (polygon != null) {
            double sum = 0;
            for (int i = 0; i < polygon.size() / 2 ; i++) {
                if (i == 0) {
                    sum += polygon.get(i * 2) * (polygon.get((i + 1) * 2 + 1) - polygon.get((polygon.size() - 1)));
                }
                else if (i == (polygon.size() / 2) - 1) {
                    sum += polygon.get(i * 2) * (polygon.get(1) - polygon.get((i - 1) * 2 + 1));
                }
                else {
                    sum += polygon.get(i * 2) * (polygon.get((i + 1) * 2 + 1) - polygon.get((i - 1) * 2 + 1));
                }
            }
            return 0.5 * Math.abs(sum);
        }
        // Just the area of bounding box
        return boundBox.area();
    }

    @Override
    public Object clone() {
        ObjectModel model = new ObjectModel();
        model.setName(name);
        model.setBoundBox((BoundBox)boundBox.clone());
        if (polygon != null) {
            model.setPolygon(polygon.stream().collect(Collectors.toList()));
        }
        return model;
    }
}
