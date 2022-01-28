# OpenLabeler
[![GitHub license](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](https://raw.githubusercontent.com/kinhong/openlabeler/master/LICENSE)
[![Downloads](https://img.shields.io/badge/download-all%20releases-brightgreen.svg)](https://github.com/kinhong/openlabeler/releases/)

## Introduction

**OpenLabeler** is an open source application for annotating objects. It can generate the PASCAL VOC format XML annotation file for artificial intelligence and deep learning training. A unique aspect of this application is its ability to use inference (with [TensorFlow](https://www.tensorflow.org)) to help improve accuracy and speed up the annotation process.

OpenLabeler is written in [OpenJDK](https://openjdk.java.net)/[OpenJFX](https://openjfx.io) (version 17.x).

![Application](assets/app.png)

A few highlights:

* Fast Labeling (no need for Open/Save File actions)
![General Preferences](assets/pref-general.png)
* Multi-level undo/redo
* Annotation "hints" (using TensorFlow inference) 
* Pre-built installation packages for macOS (tested on macOS Mojave), Linux (tested on Ubuntu 18.04 LTS), and Windows (tested on Windows 10 Pro)

## Inference

OpenLabeler can help improve the speed and accuracy of annotation by providing labeling "hints" from a saved model using TensorFlow.

For example, you have thousands of images to annotate. After labeling the first 300 or so images, you could train a model using these 300 samples, then configure OpenLabeler to use this intermediary model to give you labeling suggestions for the remaining images, thereby speeding up the annotation task.

![Inference Preferences](assets/pref-inference.png)

The **Label Map File** is the label map file in protobuf format (`.pbtxt`).

The **Saved Model Location** is the *folder* where the `.pb` file is located. If it is at `/opt/model/saved_model/saved_model.pb`, then the location should be specified as `/opt/model/saved_model`. Also, the `.pb` file must be named `saved_model.pb`.

OpenLabeler supports graphs with the `image_tensor` and `encoded_image_string_tensor` operations/input types.

## Training Support

*Note: This is currently an experimental feature.*

OpenLabeler can be used to start/stop a training process in TensorFlow running inside a [Docker](https://www.docker.com) container. Containers with [TensorFlow 2](https://www.tensorflow.org/install/docker) and [Object Detection API](https://github.com/tensorflow/models/tree/master/research/object_detection) dependencies have been pre-built for your convenience. To use this feature:

1. [Install Docker](https://docs.docker.com/install) on your host machine
2. *Windows only* Open Docker > Settings > General and check "Expose daemon on tcp://localhost:2375 without TLS"
3. *Windows only* Create a `<home>\.docker-java.properties` file. Add a `DOCKER_HOST=tcp://localhost:2375 entry`. See [reference](https://github.com/docker-java/docker-java)
4. Choose a pre-built, `kinhong/openlabeler:tf-2.3.1` or `kinhong/openlabeler:tf-2.3.1-gpu`, [docker image](https://cloud.docker.com/repository/docker/kinhong/openlabeler/tags) from [Docker Hub](https://hub.docker.com/) and pull it to your docker host
5. Download a base model from the [TensorFlow 2 Detection Model Zoo](https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/tf2_detection_zoo.md) for transfer learning
6. Configure the Training Preference settings (and add the label map entries)
![Train Preferences](assets/pref-train.png)
7. You can then start, stop, continue, restart training, or export the inference graph

## Shortcut Keys

OpenLabeler supports the following shortcut keys:

| Key Combination  | Action
| ------------- | -------------
| Ctrl (or ⌘) + o | Open media file
| Ctrl (or ⌘) + d | Open media directory
| Ctrl (or ⌘) + s | Save changes
| Ctrl (or ⌘) + x | Cut
| Ctrl (or ⌘) + c | Copy
| Ctrl (or ⌘) + v | Paste
| ⌫ (Backspace or Delete)  | Delete selected box
| Ctrl (or ⌘) + p | Go to previous media file
| Ctrl (or ⌘) + n | Go to next media file
| Ctrl (or ⌘) + g | Go to next unlabeled media file
| Ctrl (or ⌘) + h | Show inference hints
| Ctrl (or ⌘) + Shift + h | Hide inference hits
| Ctrl (or ⌘) + z | Undo
| Ctrl (or ⌘) + Shift + z | Redo
| Ctrl (or ⌘) + ↑→↓← (Arrow Keys) | Move selected bounding box  
| Any character(s) | Match/change label by prefix (on recent labels) of the selected box 


## Installation

If you have previously installed OpenLabeler, uninstall it first.

Download and execute the `.pkg`, `.deb` or `.msi` installation packages for macOS, Linux, and Windows respectively on the [releases](https://github.com/kinhong/OpenLabeler/releases) page.

## Recommended Directory Structure
```
+project
  +images
  +annotations
  +data
    -label_map file
    -train TFRecord file
    -eval TFRecord file
  +models
    +model
      +checkpoint
      +saved_model
      -pipeline config file
      -model.config (created by OpenLabeler)
      +temp (created by OpenLabeler)
        -ckpt-xyz...
        ...
      +fine_tuned_model (created by OpenLabeler)
```
 
## Build

This application can be built using [Apache Maven](https://maven.apache.org) with CLI.
First make sure the environment variable JAVA_HOME has been set accordingly

### macOS

1. Download and install [OpenJDK 17](http://jdk.java.net/17)
2. Download and install [Maven](https://maven.apache.org/install.html)
```
cd <openlabeler>
mvn clean package -Drevision=x.y.z
```
The macOS .pkg installer can be found under the app/target/package directory.

### Linux
```
sudo add-apt-repository ppa:openjdk-r/ppa \
sudo apt-get update -q \
sudo apt install -y openjdk-17-jdk

sudo apt-get install maven

sudo apt-get install fakeroot

cd <openlabeler>
mvn clean package -Drevision=x.y.z
```
The Linux .deb bundle can be found under the app/target/package directory.

### Windows

1. Download [OpenJDK 17](http://jdk.java.net/17/) for Windows and unzip to a directory with no spaces (e.g., `C:\java\jdk-17`)
2. Download [Maven](https://maven.apache.org/download.cgi) and unzip to a directory with no spaces (e.g., `C:\java\apache-maven`)

```DOS .bat
cd <openlabeler>
mvn clean package -Drevision=x.y.z
```

The Windows .msi file can be found under the app\target\package directory.

## License

[Apache License 2.0](LICENSE.md)
