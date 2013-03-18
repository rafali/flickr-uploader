#!/bin/sh
echo "Cleaning $1"
zip -d $1 com/googlecode/flickrjandroid/photos/Photo\$1.class
zip -d $1 com/googlecode/flickrjandroid/photos/Photo.class
zip -d $1 com/googlecode/flickrjandroid/REST.class
zip -d $1 com/googlecode/flickrjandroid/photos/PhotoUtils.class
zip -d $1 com/googlecode/flickrjandroid/photosets/PhotosetsInterface.class
zip -d $1 com/googlecode/flickrjandroid/uploader/Uploader.class