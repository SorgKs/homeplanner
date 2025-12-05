@echo off
@rem Script to start Android emulator using configuration from local.properties

setlocal enabledelayedexpansion

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.

set AVD_NAME=%1
if "%AVD_NAME%"=="" set AVD_NAME=HomePlanner34

set SDK_DIR=
if exist "%DIRNAME%local.properties" (
    for /f "tokens=1,* delims==" %%a in ('type "%DIRNAME%local.properties" ^| findstr /b /r "sdk.dir= avd.name="') do (
        if /i "%%a"=="sdk.dir" (
            set "SDK_DIR=%%b"
        )
        if /i "%%a"=="avd.name" (
            if "%1"=="" set "AVD_NAME=%%b"
        )
    )
)

if "%SDK_DIR%"=="" (
    echo ERROR: sdk.dir not found in local.properties.
    echo Please set sdk.dir in android/local.properties.
    pause
    exit /b 1
)

set "SDK_DIR=%SDK_DIR:"=%"
set "SDK_DIR=%SDK_DIR:/=\%"
set EMULATOR_PATH=%SDK_DIR%\emulator\emulator.exe

if not exist "%EMULATOR_PATH%" (
    echo ERROR: Emulator binary not found at %EMULATOR_PATH%
    pause
    exit /b 1
)

echo Starting Android emulator: %AVD_NAME%
echo Emulator binary: %EMULATOR_PATH%
echo.

start "" "%EMULATOR_PATH%" -avd %AVD_NAME% -netdelay none -netspeed full

echo Emulator launching...
echo Use "adb devices" to verify once booted.
echo.

endlocal
