@echo off
@rem Script to wait for device connection and install APK

setlocal enabledelayedexpansion

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.

set ADB_PATH=D:\Dev\HomePlanner\android\.android-sdk\platform-tools\adb.exe
set APK_PATH=%DIRNAME%app\build\outputs\apk\release\homeplanner_v0_0_0_71.apk

echo Waiting for Android device to be connected...
echo Please connect your device via USB with USB debugging enabled, or start an emulator.
echo.

:wait_loop
"%ADB_PATH%" devices | findstr /C:"device" >nul
if %ERRORLEVEL% EQU 0 (
    echo Device found! Installing APK...
    "%ADB_PATH%" install -r "%APK_PATH%"
    if %ERRORLEVEL% EQU 0 (
        echo.
        echo Installation completed successfully!
        echo You can now launch the app on your device.
    ) else (
        echo.
        echo Installation failed. Check the error messages above.
    )
    pause
    exit /b %ERRORLEVEL%
) else (
    timeout /t 2 /nobreak >nul
    goto wait_loop
)








