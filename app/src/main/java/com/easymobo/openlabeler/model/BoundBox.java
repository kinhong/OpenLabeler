/*
 * Copyright (c) 2020. Kin-Hong Wong. All Rights Reserved.
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

import com.easymobo.openlabeler.model.ModelUtil.OneBasedPointAdapter;
import com.easymobo.openlabeler.model.ModelUtil.PointAdapter;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlRootElement(name = "bndbox")
@XmlType(propOrder = {"XMin", "YMin", "XMax", "YMax"})
public class BoundBox implements Cloneable
{
    private Double xmin, ymin, xmax, ymax;

    public BoundBox() {}

    public BoundBox(Double xmin, Double ymin, Double xmax, Double ymax) {
        this.xmin = xmin;
        this.ymin = ymin;
        this.xmax = xmax;
        this.ymax = ymax;
    }

    @XmlElement(name = "xmin")
    @XmlJavaTypeAdapter(OneBasedPointAdapter.class)
    public Double getXMin() {
        return xmin;
    }

    public void setXMin(Double xmin) {
        this.xmin = xmin;
    }

    @XmlElement(name = "ymin")
    @XmlJavaTypeAdapter(OneBasedPointAdapter.class)
    public Double getYMin() {
        return ymin;
    }

    public void setYMin(Double ymin) {
        this.ymin = ymin;
    }

    @XmlElement(name = "xmax")
    @XmlJavaTypeAdapter(PointAdapter.class)
    public Double getXMax() {
        return xmax;
    }

    public void setXMax(Double xmax) {
        this.xmax = xmax;
    }

    @XmlElement(name = "ymax")
    @XmlJavaTypeAdapter(PointAdapter.class)
    public Double getYMax() {
        return ymax;
    }

    public void setYMax(Double ymax) {
        this.ymax = ymax;
    }

    public double area() {
        return (xmax - xmin) * (ymax - ymin);
    }

    public double getX() {
        return xmin;
    }
    public double getY() {
        return ymin;
    }
    public double getWidth() {
        return xmax - xmin;
    }
    public double getHeight() {
        return ymax - ymin;
    }

    @Override
    public Object clone() {
        return new BoundBox(xmin, ymin, xmax, ymax);
    }
}
