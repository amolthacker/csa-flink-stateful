#!/bin/bash

kafka-console-producer --broker-list $(hostname -f):9092 --topic query.input.log.1
