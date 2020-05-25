#!/bin/bash

flink savepoint -m yarn-cluster -yid $1 $2
