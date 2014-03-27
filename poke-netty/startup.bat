@echo off
cd %POKE_HOME%
set classpath=.
set classpath=%CLASSPATH%;./classes;./lib/*

set JAVA_TUNE=-Xms500m -Xmx1000m
set MAIN_CLASS=poke.server.Server
set ARGS=runtime/%1/%2

java %JAVA_TUNE% %MAIN_CLASS% %ARGS%
pause