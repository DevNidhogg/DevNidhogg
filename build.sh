#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p build
javac -encoding UTF-8 -d build Desktop.java password-generation/PasswordGeneration.java file-carving/FileCarving.java metadata-score/MetadataScore.java
jar --create --file app.jar --main-class Desktop -C build .
