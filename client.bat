@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "JAR_NAME=biosocks-1.0.0.jar"
set "JAR_PATH=%SCRIPT_DIR%%JAR_NAME%"

if not exist "%JAR_PATH%" (
    set "JAR_PATH=%SCRIPT_DIR%target\%JAR_NAME%"
)

if not exist "%JAR_PATH%" (
    echo Cannot find %JAR_NAME% in %SCRIPT_DIR% or %SCRIPT_DIR%target\
    exit /b 1
)

java -jar "%JAR_PATH%" client
exit /b %ERRORLEVEL%
