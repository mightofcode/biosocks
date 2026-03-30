#!/usr/bin/env sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR_NAME="biosocks-1.0.0.jar"
JAR_PATH="$SCRIPT_DIR/$JAR_NAME"

if [ ! -f "$JAR_PATH" ]; then
    JAR_PATH="$SCRIPT_DIR/target/$JAR_NAME"
fi

if [ ! -f "$JAR_PATH" ]; then
    echo "Cannot find $JAR_NAME in $SCRIPT_DIR or $SCRIPT_DIR/target" >&2
    exit 1
fi

exec java -jar "$JAR_PATH" client
