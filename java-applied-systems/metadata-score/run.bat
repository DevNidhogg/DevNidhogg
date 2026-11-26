@echo off
cd /d "%~dp0"
chcp 65001 >nul
where java >nul 2>&1
if errorlevel 1 (echo Install Java 21 and add it to PATH.& pause & exit /b 1)
if exist app.jar (java -jar app.jar) else (java MetadataScore.java)
pause
