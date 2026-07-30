#!/bin/sh
set -eu

exec java ${JAVA_OPTIONS} -jar "${FUSEKI_JAR}" "$@"

