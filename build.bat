@echo off
cd /d "%~dp0"
if not exist build mkdir build
javac -encoding UTF-8 -d build Desktop.java password-generation\PasswordGeneration.java file-carving\FileCarving.java metadata-score\MetadataScore.java
if errorlevel 1 exit /b 1
jar --create --file app.jar --main-class Desktop -C build .
