#!/bin/bash

flink cancel -m yarn-cluster -yid $1 $2
