@echo off
rem Builds the latest version of the mod and opens it in its own copy of Minecraft (NeoForge).
rem Worlds and settings are kept in neoforge\runs\client between launches.
title Scarlet Witch
cd /d "%~dp0"
set "JAVA_HOME=%USERPROFILE%\.jdks\jdk-25.0.4.1+1"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Building the latest Scarlet Witch and starting Minecraft...
echo (The first launch after changes takes a minute.)
echo.
call gradlew.bat :neoforge:runClient --console=plain
if errorlevel 1 (
    echo.
    echo Something went wrong. Scroll up to see the error, or send it to the assistant.
    pause
)
