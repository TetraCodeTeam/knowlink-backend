#!/bin/sh
# Maven Wrapper script - auto-downloads Maven if needed
MAVEN_WRAPPER_JAR=".mvn/wrapper/maven-wrapper.jar"
MAVEN_WRAPPER_PROPERTIES=".mvn/wrapper/maven-wrapper.properties"

# Detect JAVA_HOME
if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="java"
fi

# Download wrapper jar if missing
if [ ! -f "$MAVEN_WRAPPER_JAR" ]; then
  WRAPPER_URL=$(grep 'wrapperUrl' "$MAVEN_WRAPPER_PROPERTIES" | cut -d= -f2-)
  curl -fsSL -o "$MAVEN_WRAPPER_JAR" "$WRAPPER_URL" 2>/dev/null \
    || wget -q -O "$MAVEN_WRAPPER_JAR" "$WRAPPER_URL" 2>/dev/null
fi

exec "$JAVACMD" -classpath "$MAVEN_WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain "$@"
