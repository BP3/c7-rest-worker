#!/bin/bash

JARFILE=`find build/libs | grep jar | grep -v plain | head -1`

# Grab any user-supplied options
JAVA_OPTS="$*"

echo "Running: java $JDK_JAVA_OPTIONS $JAVA_OPTS -jar $JARFILE"

# The 'java' command looks up $JDK_JAVA_OPTIONS internally, so we don't need to specify it here
java $JAVA_OPTS -jar $JARFILE
