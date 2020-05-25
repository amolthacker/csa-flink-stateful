#!/bin/bash

kafka-console-consumer --bootstrap-server $(hostname -f):9092 --topic query.output.log.1

