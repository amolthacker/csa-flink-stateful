#!/bin/bash

flink run -m yarn-cluster -d -p 2 -ys 2 -ytm 1500 -ynm TransactionProcessor  csa-flink-stateful-1.0.0-SNAPSHOT.jar job.properties
