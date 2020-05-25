#!/bin/bash

flink run -m yarn-cluster -d -p 1 -ys 1 -ytm 1000 -ynm TrnxProcessor -c com.cloudera.streaming.examples.flink.KafkaHDFSITrnxJob csa-flink-stateful-1.0.0-SNAPSHOT.jar job.properties

