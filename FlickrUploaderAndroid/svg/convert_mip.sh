#!/bin/bash

#Requires inkscape to be installed.

for f in "$@";
do
	echo "Processing $f"
	/Applications/Inkscape.app/Contents/Resources/bin/inkscape --file=$f --export-png=../res/drawable-xhdpi/${f/.svg}.png --export-dpi=120
	/Applications/Inkscape.app/Contents/Resources/bin/inkscape --file=$f --export-png=../res/drawable-hdpi/${f/.svg}.png --export-dpi=90
	/Applications/Inkscape.app/Contents/Resources/bin/inkscape --file=$f --export-png=../res/drawable-mdpi/${f/.svg}.png --export-dpi=60
	/Applications/Inkscape.app/Contents/Resources/bin/inkscape --file=$f --export-png=../res/drawable-ldpi/${f/.svg}.png --export-dpi=45
done