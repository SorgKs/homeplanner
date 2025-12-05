@echo off
@rem Script to install and run Android app on emulator
@rem Automatically finds latest APK and installs it

setlocal enabledelayedexpansion

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.

@rem Setup log file
set LOG_FILE=%DIRNAME%install_output.log
powershell -Command "$timestamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'; \"`n========================================`nInstall and Run - Started at $timestamp`n========================================`n\" | Out-File -FilePath '%LOG_FILE%' -Append -Encoding UTF8"

echo. | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
echo ======================================== | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
echo Installing and running on emulator | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
echo ======================================== | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
echo. | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
echo Log will be saved to: %LOG_FILE% | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
echo. | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"

@rem Read paths from local.properties
if not exist "%DIRNAME%local.properties" (
    echo ERROR: local.properties not found at %DIRNAME%local.properties | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
    pause
    exit /b 1
)

@rem Read sdk.dir from local.properties
for /f "tokens=2 delims==" %%a in ('findstr /c:"sdk.dir=" "%DIRNAME%local.properties" 2^>nul') do (
    set "SDK_DIR=%%a"
    set "SDK_DIR=!SDK_DIR:"=!"
    for /f "tokens=* delims= " %%b in ("!SDK_DIR!") do set "SDK_DIR=%%b"
)

if "!SDK_DIR!"=="" (
    echo ERROR: sdk.dir not defined in local.properties | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
    pause
    exit /b 1
)

set ADB_PATH=!SDK_DIR!\platform-tools\adb.exe
set ADB_PATH=!ADB_PATH:/=\!

if not exist "!ADB_PATH!" (
    echo ERROR: adb.exe not found at !ADB_PATH! | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
    pause
    exit /b 1
)

@rem Check for connected devices
echo Checking for connected devices... | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
powershell -Command "& '!ADB_PATH!' devices 2>&1 | Tee-Object -FilePath '%LOG_FILE%' -Append"
echo. | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"

@rem Find latest APK (prefer release, fallback to debug)
set APK_FILE=

@rem Try release first
set RELEASE_DIR=%DIRNAME%app\build\outputs\apk\release
if exist "!RELEASE_DIR!" (
    for /f "delims=" %%f in ('dir /b /o-d "!RELEASE_DIR!\homeplanner_*.apk" 2^>nul') do (
        set "APK_FILE=!RELEASE_DIR!\%%f"
        echo Found release APK: %%f | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
        goto :found_apk
    )
)

@rem Fallback to debug
set DEBUG_DIR=%DIRNAME%app\build\outputs\apk\debug
if exist "!DEBUG_DIR!" (
    for /f "delims=" %%f in ('dir /b /o-d "!DEBUG_DIR!\homeplanner_*.apk" 2^>nul') do (
        set "APK_FILE=!DEBUG_DIR!\%%f"
        echo Found debug APK: %%f | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
        goto :found_apk
    )
)

echo. | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
echo ERROR: No APK file found! | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
echo Please build the app first using build.bat or gradlew assembleDebug | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
echo. | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
pause
exit /b 1

:found_apk
echo APK path: !APK_FILE! | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
echo. | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"

@rem Install APK
echo Installing APK on device... | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
powershell -Command "$result = & '!ADB_PATH!' install -r '!APK_FILE!' 2>&1; $result | Tee-Object -FilePath '%LOG_FILE%' -Append; exit $LASTEXITCODE"
set INSTALL_EXITCODE=%ERRORLEVEL%

if %INSTALL_EXITCODE% NEQ 0 (
    echo. | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
    echo ======================================== | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
    echo INSTALLATION FAILED! | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
    echo ======================================== | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
    echo. | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
    pause
    exit /b 1
)

echo. | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
echo Installation successful! | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
echo. | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"

@rem Launch app
set PACKAGE_NAME=com.homeplanner
set MAIN_ACTIVITY=com.homeplanner.MainActivity

echo Launching app... | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
powershell -Command "$result = & '!ADB_PATH!' shell am start -n '%PACKAGE_NAME%/%MAIN_ACTIVITY%' 2>&1; $result | Tee-Object -FilePath '%LOG_FILE%' -Append; exit $LASTEXITCODE"
set LAUNCH_EXITCODE=%ERRORLEVEL%

if %LAUNCH_EXITCODE% EQU 0 (
    echo. | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
    echo ======================================== | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
    echo App launched successfully! | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
    echo ======================================== | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
    echo. | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
) else (
    echo. | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
    echo WARNING: App may have failed to launch | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
    echo. | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
)

echo. | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
echo Log saved to: %LOG_FILE% | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
echo. | powershell -Command "$input | Tee-Object -FilePath '%LOG_FILE%' -Append"
pause
endlocal

