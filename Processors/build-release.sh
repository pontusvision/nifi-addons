#!/bin/bash

echo "Building Release Zip ...."

# Make the directory and start moving the files into it
rm -rf ./Release
rm -rf ./Release.tar.gz
rm -rf ./Release.zip
mkdir Release

cp ./nifi-barcode/nifi-barcode-nar/target/*.nar ./Release/.
cp ./nifi-docker/nifi-docker-nar/target/*.nar ./Release/.
cp ./nifi-file/nifi-file-nar/target/*.nar ./Release/.
cp ./nifi-google-cloud/nifi-google-nar/target/*.nar ./Release/.
cp ./nifi-google-cloud/nifi-google-cloud-services/nifi-google-api-nar/target/*.nar ./Release/.
cp ./nifi-google-cloud/nifi-google-cloud-services/nifi-google-nar/target/*.nar ./Release/.
cp ./nifi-hdfs/nifi-hdfs-nar/target/*.nar ./Release/.
cp ./nifi-image/nifi-image-nar/target/*.nar ./Release/.
cp ./nifi-jmx/nifi-jmx-nar/target/*.nar ./Release/.
cp ./nifi-ocr/nifi-ocr-nar/target/*.nar ./Release/.
cp ./nifi-opencv/nifi-opencv-nar/target/*.nar ./Release/.
cp ./nifi-salesforce/nifi-salesforce-nar/target/*.nar ./Release/.
cp ./nifi-sphinx/nifi-sphinx-nar/target/*.nar ./Release/.
cp ./nifi-tensorflow/nifi-tensorflow-nar/target/*.nar ./Release/.
cp ./nifi-tesseract/nifi-tesseract-nar/target/*.nar ./Release/.

tar -czvf Release.tar.gz ./Release
zip Release.zip ./Release
