@echo off
@rem Script to install and launch Android app on emulator

setlocal enabledelayedexpansion

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.

set ADB_PATH=D:\Dev\HomePlanner\android\.android-sdk\platform-tools\adb.exe
set APK_PATH=%DIRNAME%app\build\outputs\apk\debug\homeplanner_v0_0_70.apk
set PACKAGE_NAME=com.homeplanner
set MAIN_ACTIVITY=com.homeplanner.MainActivity

echo Checking for connected devices...
"%ADB_PATH%" devices

echo.
echo Installing debug APK...
"%ADB_PATH%" install -r "%APK_PATH%"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo Installation successful! Launching app...
    "%ADB_PATH%" shell am start -n "%PACKAGE_NAME%/%MAIN_ACTIVITY%"
    if %ERRORLEVEL% EQU 0 (
        echo App launched successfully!
    ) else (
        echo Failed to launch app.
    )
) else (
    echo Installation failed.
)

pause
endlocal








