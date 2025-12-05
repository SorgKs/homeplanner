@echo off
@rem Script to install Android APK release on connected device

setlocal enabledelayedexpansion

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.

@rem Read java.home from local.properties if it exists
if exist "%DIRNAME%local.properties" (
    for /f "tokens=2 delims==" %%a in ('findstr /c:"java.home=" "%DIRNAME%local.properties" 2^>nul') do (
        set "JAVA_HOME_FROM_LOCAL=%%a"
        set "JAVA_HOME_FROM_LOCAL=!JAVA_HOME_FROM_LOCAL:"=!"
        for /f "tokens=* delims= " %%b in ("!JAVA_HOME_FROM_LOCAL!") do set "JAVA_HOME_FROM_LOCAL=%%b"
        if not "!JAVA_HOME_FROM_LOCAL!"=="" (
            set "JAVA_HOME=!JAVA_HOME_FROM_LOCAL!"
            echo Using JDK from local.properties: !JAVA_HOME!
        )
    )
)

echo.
echo Installing Android app on connected device...
echo Make sure your device is connected via USB with USB debugging enabled!
echo.

@rem Install APK using Gradle
call "%DIRNAME%gradlew.bat" :app:installRelease

if %ERRORLEVEL% EQU 0 (
    echo.
    echo Installation completed successfully!
    echo You can now launch the app on your device.
) else (
    echo.
    echo Installation failed. Check the error messages above.
)

pause
endlocal








