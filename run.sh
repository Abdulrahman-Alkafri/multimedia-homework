#!/bin/bash
cd "$(dirname "$0")"
echo "Building PixelLab..."
mvn -q compile && GTK_MODULES="" java -cp target/classes multimedia.Main
