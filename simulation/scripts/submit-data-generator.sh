#!/bin/bash

flink run -m yarn-cluster -d -p 2 -ys 2 -ynm DataGenerator -c com.cloudera.streaming.examples.flink.KafkaDataGeneratorJob csa-flink-stateful-1.0.0-SNAPSHOT.jar job.properties

