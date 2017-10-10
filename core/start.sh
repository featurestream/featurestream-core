#!/bin/bash

DIR=$(dirname $0)

filename=../client/resources/KDDTrain_1Percent.csv
target=41
targettype=CATEGORIC 
learnertype=hoeffding_classifier
master=local[4]
has_header=0

java -server -ea -cp .:$DIR/target/featurestream-core-1.0-SNAPSHOT.jar featurestream.classifier.rf.spark.RFDriver $filename $target $targettype $learnertype $master $has_header

