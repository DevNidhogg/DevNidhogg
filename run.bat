@echo off
cd /d "%~dp0"
chcp 65001 >nul
where java >nul 2>&1
if errorlevel 1 (echo Install Java 21 and add it to PATH.& pause & exit /b 1)
if not exist app.jar call build.bat
if not exist app.jar (echo Build failed.& pause & exit /b 1)
java -jar app.jar %*
if errorlevel 1 pause
