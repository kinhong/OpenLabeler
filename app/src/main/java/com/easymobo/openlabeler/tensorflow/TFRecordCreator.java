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

package com.easymobo.openlabeler.tensorflow;

import com.easymobo.openlabeler.model.Annotation;
import com.easymobo.openlabeler.preference.LabelMapItem;
import com.google.protobuf.ByteString;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.tensorflow.example.*;
import org.tensorflow.hadoop.util.TFRecordWriter;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class TFRecordCreator
{
    private static final Logger LOG = Logger.getLogger(TFRecordCreator.class.getCanonicalName());

    private final Path imagePath, annotationPath, dataPath;

    // For PASCAL VOC xml persistence
    private JAXBContext jaxbContext;

    public TFRecordCreator(Path imagePath, Path annotationPath, Path dataPath) {
        this.imagePath = imagePath;
        this.annotationPath = annotationPath;
        this.dataPath = dataPath;
        try {
            jaxbContext = JAXBContext.newInstance(Annotation.class);
        }
        catch (JAXBException ex) {
            LOG.log(Level.SEVERE, "Unable to create JAXBContext", ex);
        }
    }

    public void createData(List<LabelMapItem> items) {
        if (!imagePath.toFile().exists()) {
            LOG.severe("Image path does not exist");
            return;
        }
        if (!annotationPath.toFile().exists()) {
            LOG.severe("Annotation path does not exist");
            return;
        }
        if (!dataPath.toFile().exists()) {
            dataPath.toFile().mkdirs();
        }

        // Randomly divide examples into 70% train and 30% eval
        List<File> files = Arrays.asList(annotationPath.toFile().listFiles((dir, name) -> {
            name = name.toLowerCase();
            return name.endsWith(".xml");
        }));
        Collections.shuffle(files);
        int separator = (int)(files.size() * 0.7);
        List<File> train = files.subList(0, separator);
        List<File> eval = files.subList(separator, files.size());

        // Generate the train and eval records
        Map<String, Integer> labelMap = items.stream().collect(
                Collectors.toMap(LabelMapItem::getName, LabelMapItem::getId));
        createTFRecord(train, labelMap, Paths.get(dataPath.toString(), "train.record"));
        createTFRecord(eval, labelMap, Paths.get(dataPath.toString(), "eval.record"));
        LOG.info("Created train/eval records in " + dataPath);
    }

    private void createTFRecord(List<File> annotations, Map<String, Integer>labelMap, Path outputPath) {
        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
            TFRecordWriter writer = new TFRecordWriter(new DataOutputStream(fos));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (File file : annotations) {
                Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
                Annotation annotation = (Annotation) unmarshaller.unmarshal(file);
                File imageFile = Paths.get(imagePath.toString(), annotation.getFilename()).toFile();
                if (!imageFile.exists()) {
                    LOG.warning("Image "+imageFile+ " does not exist");
                    continue;
                }
                if (annotation.getObjects().size() <= 0) {
                    LOG.warning("No objects in " + file);
                    continue;
                }
                FileInputStream fio = new FileInputStream(imageFile);
                byte[] imageBytes = IOUtils.toByteArray(fio);
                String key = Hex.encodeHexString(digest.digest(imageBytes));

                int width = annotation.getSize().getWidth();
                int height = annotation.getSize().getHeight();
                if (width <= 0.0 || height <= 0.0 ) {
                    LOG.warning("Error in annotation size " + file);
                    continue;
                }
                FloatList.Builder xmin = FloatList.newBuilder();
                FloatList.Builder xmax = FloatList.newBuilder();
                FloatList.Builder ymin = FloatList.newBuilder();
                FloatList.Builder ymax = FloatList.newBuilder();
                BytesList.Builder text = BytesList.newBuilder();
                Int64List.Builder label = Int64List.newBuilder();
                Int64List.Builder difficult = Int64List.newBuilder();
                Int64List.Builder truncated = Int64List.newBuilder();
                BytesList.Builder poses = BytesList.newBuilder();
                annotation.getObjects().forEach(obj -> {
                    Integer id = labelMap.get(obj.getName());
                    if (id == null) {
                        LOG.warning("Could not find " + obj.getName() + " in " + file + " in label map");
                        return;
                    }
                    xmin.addValue((float)(obj.getBoundBox().getXMin() / width));
                    ymin.addValue((float)(obj.getBoundBox().getYMin() / height));
                    xmax.addValue((float)(obj.getBoundBox().getXMax() / width));
                    ymax.addValue((float)(obj.getBoundBox().getYMax() / height));
                    text.addValue(ByteString.copyFromUtf8(obj.getName()));
                    label.addValue(id);
                    difficult.addValue(0);
                    truncated.addValue(0);
                    poses.addValue(ByteString.copyFromUtf8("Unspecified"));
                });

                Features.Builder builder = Features.newBuilder();
                builder.putFeature("image/height", getInt64ListFeature(annotation.getSize().getHeight()));
                builder.putFeature("image/width", getInt64ListFeature(annotation.getSize().getWidth()));
                builder.putFeature("image/filename", getBytesListFeature(annotation.getFilename()));
                builder.putFeature("image/source_id", getBytesListFeature(annotation.getFilename()));
                builder.putFeature("image/key/sha256", getBytesListFeature(key));
                builder.putFeature("image/encoded", getBytesListFeature(imageBytes));
                builder.putFeature("image/format", getBytesListFeature("jpeg"));
                builder.putFeature("image/object/bbox/xmin", Feature.newBuilder().setFloatList(xmin).build());
                builder.putFeature("image/object/bbox/xmax", Feature.newBuilder().setFloatList(xmax).build());
                builder.putFeature("image/object/bbox/ymin", Feature.newBuilder().setFloatList(ymin).build());
                builder.putFeature("image/object/bbox/ymax", Feature.newBuilder().setFloatList(ymax).build());
                builder.putFeature("image/object/class/text", Feature.newBuilder().setBytesList(text).build());
                builder.putFeature("image/object/class/label", Feature.newBuilder().setInt64List(label).build());
                builder.putFeature("image/object/difficult", Feature.newBuilder().setInt64List(difficult).build());
                builder.putFeature("image/object/truncated", Feature.newBuilder().setInt64List(truncated).build());
                builder.putFeature("image/object/view", Feature.newBuilder().setBytesList(poses).build());

                writer.write(Example.newBuilder().setFeatures(builder.build()).build().toByteArray());
                fio.close();
            }
        }
        catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unable to create TFRecord", ex);
        }
    }

    private Feature getInt64ListFeature(int value) {
        return Feature.newBuilder().setInt64List(Int64List.newBuilder().addValue(value)).build();
    }

    private Feature getBytesListFeature(String value) {
        return Feature.newBuilder().setBytesList(BytesList.newBuilder().addValue(ByteString.copyFromUtf8(value))).build();
    }

    private Feature getBytesListFeature(byte[] value) {
        return Feature.newBuilder().setBytesList(BytesList.newBuilder().addValue(ByteString.copyFrom(value))).build();
    }
}
