@echo off
set MAVEN_WRAPPER_JAR=.mvn\wrapper\maven-wrapper.jar
set MAVEN_WRAPPER_PROPERTIES=.mvn\wrapper\maven-wrapper.properties

if defined JAVA_HOME (
  set JAVACMD="%JAVA_HOME%\bin\java"
) else (
  set JAVACMD=java
)

if not exist %MAVEN_WRAPPER_JAR% (
  for /f "tokens=2 delims==" %%i in ('findstr "wrapperUrl" %MAVEN_WRAPPER_PROPERTIES%') do set WRAPPER_URL=%%i
  powershell -Command "Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%MAVEN_WRAPPER_JAR%'"
)

%JAVACMD% -classpath %MAVEN_WRAPPER_JAR% org.apache.maven.wrapper.MavenWrapperMain %*
