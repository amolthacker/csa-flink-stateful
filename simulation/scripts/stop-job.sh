#!/bin/bash

flink stop -m yarn-cluster -yid $1 $2
