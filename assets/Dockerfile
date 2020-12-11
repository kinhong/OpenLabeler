FROM tensorflow/tensorflow:2.3.1
LABEL maintainer="kinhong.wong@easymobo.com"

RUN apt-get update && \
  apt-get install -y --no-install-recommends \
  wget \
  unzip \
  subversion \
  libgl1-mesa-glx \
  && rm -rf /var/lib/apt/lists/*

RUN /usr/bin/python3 -m pip install --upgrade pip && \ 
    pip3 install --upgrade setuptools wheel && \
    pip3 install Cython contextlib2 pillow lxml matplotlib && \
    pip3 install tf_slim lvis scipy tf-models-official && \
    pip3 install pycocotools

WORKDIR /root

RUN svn export https://github.com/tensorflow/models/trunk/research/object_detection

RUN wget -O protobuf.zip https://github.com/google/protobuf/releases/download/v3.14.0/protoc-3.14.0-linux-x86_64.zip && \
    unzip protobuf.zip && \
    ./bin/protoc object_detection/protos/*.proto --python_out=.

ENV PYTHONPATH "${PYTONPATH}:/root"
