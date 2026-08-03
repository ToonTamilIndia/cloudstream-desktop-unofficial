@echo off
set "JAVA_HOME="
cd /d "%~dp0"
echo ===================================================
echo   Building standalone JAR + wrapping with Launch4j
echo ===================================================
echo.

IF NOT EXIST "android-reference\settings.gradle.kts" (
    echo [INFO] android-reference submodule is empty. Attempting to fetch automatically...
    git submodule update --init --recursive
    IF NOT EXIST "android-reference\settings.gradle.kts" (
        echo [FATAL ERROR] android-reference folder is STILL empty!
        echo Please clone with:  git clone --recursive https://github.com/YourUsername/cloudstream-windows.git
        pause
        exit /b 1
    )
)

echo [1/3] Building fat JAR (all dependencies merged)...
call gradlew :desktop-app:fatJar
if %errorlevel% neq 0 (
    echo [ERROR] fatJar build failed!
    pause
    exit /b %errorlevel%
)

set "FAT_JAR=desktop-app\build\libs\CloudStream-Desktop-all.jar"
if not exist "%FAT_JAR%" (
    echo [ERROR] Expected fat JAR not found at %FAT_JAR%
    pause
    exit /b 1
)

echo [2/3] Copying bundled JRE (optional, gives you a fully standalone exe)...
set "JRE_DIR=%~dp0dist\jre"
if not exist "%JRE_DIR%" (
    if exist "desktop-app\build\compose\binaries\main\app\CloudStream-Desktop\runtime" (
        echo   Copying JRE from Compose distribution...
        xcopy /E /I /Q "desktop-app\build\compose\binaries\main\app\CloudStream-Desktop\runtime" "%JRE_DIR%" >nul
    ) else (
        echo   No bundled runtime found - skipping. The exe will need Java 21 installed.
    )
)

echo [3/3] Wrapping with Launch4j...
if not exist "launch4j\launch4j.exe" (
    echo.
    echo [FATAL] launch4j\launch4j.exe not found.
    echo Download Launch4j from https://github.com/l4j/launch4j/releases and extract it
    echo into the "launch4j" folder next to this script, then re-run this script.
    pause
    exit /b 1
)

"launch4j\launch4j.exe" launch4j.xml
if %errorlevel% neq 0 (
    echo [ERROR] Launch4j failed. See errors above.
    pause
    exit /b %errorlevel%
)

echo.
echo [SUCCESS] CloudStream-Desktop.exe created. Copy it together with the "dist\jre"
echo folder to any Windows machine - no Java installation required.
pause
