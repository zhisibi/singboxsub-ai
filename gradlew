#!/bin/sh

# Standard Gradle wrapper execution script for POSIX environments

# Attempt to locate gradle on PATH or through standard location
if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
fi

# Fallback: Locate java and run wrapper jar if available
JAVACMD="java"
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
fi

DIRNAME=$(dirname "$0")
WRAPPER_JAR="$DIRNAME/gradle/wrapper/gradle-wrapper.jar"

if [ -f "$WRAPPER_JAR" ]; then
    exec "$JAVACMD" "-Dorg.gradle.appname=gradlew" -jar "$WRAPPER_JAR" "$@"
else
    echo "ERROR: gradle command not found on PATH." >&2
    exit 1
fi
