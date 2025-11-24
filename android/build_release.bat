@echo off
@rem Script to build Android app
@rem Usage: build.bat [--debug|-d]
@rem   --debug, -d: Build debug version (doesn't increment version, doesn't require signing)

setlocal enabledelayedexpansion

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.

set LOG_FILE=%DIRNAME%build_output.log
echo Logging build output to %LOG_FILE%
if exist "%LOG_FILE%" (
    echo.>>"%LOG_FILE%"
    echo ==== Build started %DATE% %TIME% ====>>"%LOG_FILE%"
)

@rem Check for debug flag
set BUILD_DEBUG=0
if "%1"=="--debug" set BUILD_DEBUG=1
if "%1"=="-d" set BUILD_DEBUG=1

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
echo ========================================
if !BUILD_DEBUG!==1 (
    echo Building debug version
) else (
    echo Building new release version
)
echo ========================================
echo.

@rem Read and update version (only for release builds)
set VERSION_FILE=%DIRNAME%version.json
set NEW_PATCH=

if !BUILD_DEBUG!==0 (
    if not exist "%VERSION_FILE%" (
        echo ERROR: version.json not found at %VERSION_FILE%
        pause
        exit /b 1
    )
    
    @rem Extract patch version using PowerShell
    for /f "delims=" %%i in ('powershell -Command "(Get-Content '%VERSION_FILE%' | ConvertFrom-Json).patch"') do set CURRENT_PATCH=%%i
    
    if "%CURRENT_PATCH%"=="" (
        echo ERROR: Could not read patch version from version.json
        pause
        exit /b 1
    )
    
    echo Current patch version: %CURRENT_PATCH%
    
    @rem Increment patch version
    set /a NEW_PATCH=%CURRENT_PATCH%+1
    echo New patch version: %NEW_PATCH%
    
    @rem Update version.json using PowerShell
    powershell -Command "$json = Get-Content '%VERSION_FILE%' | ConvertFrom-Json; $json.patch = %NEW_PATCH%; $json | ConvertTo-Json | Set-Content '%VERSION_FILE%'"
    
    if %ERRORLEVEL% NEQ 0 (
        echo ERROR: Failed to update version.json
        pause
        exit /b 1
    )
    
    echo.
    echo Version updated successfully!
    echo.
) else (
    echo Debug build: skipping version increment
    echo.
)

@rem Clean previous build
echo Cleaning previous build...
call :run_gradle clean
if %ERRORLEVEL% NEQ 0 (
    echo WARNING: Clean failed, but continuing...
)

echo.
if !BUILD_DEBUG!==1 (
    echo Building debug APK...
) else (
    echo Building release APK...
)
echo.

@rem Build APK
if !BUILD_DEBUG!==1 (
    call :run_gradle assembleDebug
) else (
    call :run_gradle assembleRelease
)

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ========================================
    echo BUILD FAILED!
    echo ========================================
    echo.
    echo Check the error messages above.
    echo.
    pause
    exit /b 1
)

echo.
echo ========================================
echo BUILD SUCCESSFUL!
echo ========================================
echo.

@rem Find the APK file
if !BUILD_DEBUG!==1 (
    set APK_DIR=%DIRNAME%app\build\outputs\apk\debug
) else (
    set APK_DIR=%DIRNAME%app\build\outputs\apk\release
)
set APK_FILE=

for %%f in ("%APK_DIR%\homeplanner_*.apk") do (
    set APK_FILE=%%f
)

if "%APK_FILE%"=="" (
    echo WARNING: Could not find APK file in %APK_DIR%
    echo Please check the build output manually.
) else (
    echo APK file created:
    echo %APK_FILE%
    echo.
    
    @rem Get file size
    for %%A in ("%APK_FILE%") do set APK_SIZE=%%~zA
    set /a APK_SIZE_MB=%APK_SIZE%/1024/1024
    echo File size: %APK_SIZE_MB% MB
    echo.
)

echo.
if !BUILD_DEBUG!==0 (
    echo Version: %NEW_PATCH%
)
echo Build completed successfully!
echo.

@rem Ask if user wants to install
set /p INSTALL="Do you want to install on connected device? (Y/N): "
if /i "%INSTALL%"=="Y" (
    echo.
    echo Installing on device...
    call :run_gradle :app:installRelease
    if %ERRORLEVEL% EQU 0 (
        echo.
        echo Installation completed successfully!
    ) else (
        echo.
        echo Installation failed. Make sure device is connected with USB debugging enabled.
    )
)

echo.
pause
endlocal

:run_gradle
set "GRADLE_ARGS=%*"
powershell -Command "& { & '%DIRNAME%gradlew.bat' %GRADLE_ARGS% 2>&1 | Tee-Object -FilePath '%LOG_FILE%' -Append; exit $LASTEXITCODE }"
exit /b %ERRORLEVEL%

