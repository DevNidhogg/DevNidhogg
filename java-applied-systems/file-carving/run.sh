#!/bin/sh
cd -- "$(dirname -- "$0")" || exit 1
if [ -f app.jar ]; then exec java -jar app.jar; fi
exec java FileCarving.java
