#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ ! -f app.jar ]; then sh build.sh; fi
exec java -jar app.jar "$@"
