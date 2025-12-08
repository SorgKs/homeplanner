# PowerShell script to build Android app
# Usage: build.ps1 [--debug|-d]
#   --debug, -d: Build debug version (doesn't increment version, doesn't require signing)

# ErrorActionPreference will be set locally for each command

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $scriptDir) { $scriptDir = "." }

# Configure logging to both console and file
$logFile = Join-Path $scriptDir "build_output.log"
$transcriptStarted = $false
try {
    Start-Transcript -Path $logFile -Append | Out-Null
    $transcriptStarted = $true
    Write-Host "Logging build output to $logFile" -ForegroundColor Gray
} catch {
    Write-Host "WARNING: Failed to start transcript: $($_.Exception.Message)" -ForegroundColor Yellow
}

# Check for debug flag
$buildDebug = $false
foreach ($arg in $args) {
    if ($arg -eq "--debug" -or $arg -eq "-d") {
        $buildDebug = $true
        break
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
if ($buildDebug) {
    Write-Host "Building debug version (patch will be incremented)" -ForegroundColor Cyan
} else {
    Write-Host "Building release version (patch will be incremented)" -ForegroundColor Cyan
}
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Read java.home from local.properties if it exists
$localPropsPath = Join-Path $scriptDir "local.properties"
if (Test-Path $localPropsPath) {
    $localProps = Get-Content $localPropsPath | Where-Object { $_ -match "^java\.home=" }
    if ($localProps) {
        $javaHome = ($localProps -split "=", 2)[1].Trim().Trim('"')
        if ($javaHome) {
            $env:JAVA_HOME = $javaHome
            Write-Host "Using JDK from local.properties: $javaHome" -ForegroundColor Gray
        }
    }
}

# Read and increment version BEFORE build (so APK has the new version)
$versionFile = Join-Path $scriptDir "version.json"
$newPatch = $null

if (-not (Test-Path $versionFile)) {
    Write-Host "ERROR: version.json not found at $versionFile" -ForegroundColor Red
    exit 1
}

try {
    $versionJson = Get-Content $versionFile | ConvertFrom-Json
    $currentPatch = $versionJson.patch
    
    if ($null -eq $currentPatch) {
        Write-Host "ERROR: Could not read patch version from version.json" -ForegroundColor Red
        exit 1
    }
    
    Write-Host "Current patch version: $currentPatch" -ForegroundColor Yellow
    
    # Increment patch version BEFORE build
    $newPatch = $currentPatch + 1
    Write-Host "Incrementing patch version: $currentPatch -> $newPatch" -ForegroundColor Cyan
    
    # Update version.json BEFORE build
    $versionJson.patch = $newPatch
    $versionJson | ConvertTo-Json | Set-Content $versionFile -Encoding UTF8
    
    Write-Host "APK will be built with version: $newPatch" -ForegroundColor Green
    Write-Host ""
} catch {
    Write-Host "ERROR: Failed to update version: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

try {
    
    # Clean previous build
    Write-Host "Cleaning previous build..." -ForegroundColor Gray
    $gradlewPath = Join-Path $scriptDir "gradlew.bat"
    
    Push-Location $scriptDir
    $oldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        & $gradlewPath clean 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Host "WARNING: Clean failed, but continuing..." -ForegroundColor Yellow
        }
    } catch {
        Write-Host "WARNING: Clean failed, but continuing..." -ForegroundColor Yellow
    } finally {
        $ErrorActionPreference = $oldErrorAction
    }
    
    Write-Host ""
    if ($buildDebug) {
        Write-Host "Building debug APK..." -ForegroundColor Cyan
    } else {
        Write-Host "Building release APK..." -ForegroundColor Cyan
    }
    Write-Host ""
    
    # Build APK
    $oldErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        if ($buildDebug) {
            & $gradlewPath assembleDebug
        } else {
            & $gradlewPath assembleRelease
        }
        $buildExitCode = $LASTEXITCODE
        
        if ($buildExitCode -ne 0) {
            Write-Host ""
            Write-Host "========================================" -ForegroundColor Red
            Write-Host "BUILD FAILED!" -ForegroundColor Red
            Write-Host "========================================" -ForegroundColor Red
            Write-Host ""
            Write-Host "Exit code: $buildExitCode" -ForegroundColor Red
            Write-Host ""
            Pop-Location
            exit 1
        }
    } catch {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Red
        Write-Host "BUILD FAILED!" -ForegroundColor Red
        Write-Host "========================================" -ForegroundColor Red
        Write-Host ""
        Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host ""
        Pop-Location
        exit 1
    } finally {
        $ErrorActionPreference = $oldErrorAction
    }
    Pop-Location
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "BUILD SUCCESSFUL!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    
    # Find the APK file
    if ($buildDebug) {
        $apkDir = Join-Path $scriptDir "app\build\outputs\apk\debug"
    } else {
        $apkDir = Join-Path $scriptDir "app\build\outputs\apk\release"
    }
    $apkFiles = Get-ChildItem -Path $apkDir -Filter "homeplanner_*.apk" -ErrorAction SilentlyContinue
    
    if ($apkFiles) {
        $apkFile = $apkFiles | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        Write-Host "APK file created:" -ForegroundColor Green
        Write-Host $apkFile.FullName -ForegroundColor White
        Write-Host ""
        
        $apkSizeMB = [math]::Round($apkFile.Length / 1MB, 2)
        Write-Host "File size: $apkSizeMB MB" -ForegroundColor Gray
        Write-Host ""
    } else {
        Write-Host "WARNING: Could not find APK file in $apkDir" -ForegroundColor Yellow
        Write-Host "Please check the build output manually." -ForegroundColor Yellow
    }
    
    Write-Host ""
    Write-Host "Build completed successfully!" -ForegroundColor Green
    if ($null -ne $newPatch) {
        Write-Host "Next build will use patch: $($newPatch + 1)" -ForegroundColor Gray
    }
    Write-Host ""
    
    # Ask if user wants to install
    $install = Read-Host "Do you want to install on connected device? (Y/N)"
    if ($install -eq "Y" -or $install -eq "y") {
        Write-Host ""
        Write-Host "Installing on device..." -ForegroundColor Cyan
        Push-Location $scriptDir
        try {
            if ($buildDebug) {
                & $gradlewPath :app:installDebug
            } else {
                & $gradlewPath :app:installRelease
            }
            if ($LASTEXITCODE -eq 0) {
                Write-Host ""
                Write-Host "Installation completed successfully!" -ForegroundColor Green
            } else {
                Write-Host ""
                Write-Host "Installation failed. Make sure device is connected with USB debugging enabled." -ForegroundColor Red
            }
        } catch {
            Write-Host ""
            Write-Host "Installation failed. Make sure device is connected with USB debugging enabled." -ForegroundColor Red
            Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        }
        Pop-Location
    }
    
} catch {
    Write-Host ""
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host $_.ScriptStackTrace
    exit 1
}

if ($transcriptStarted) {
    try {
        Stop-Transcript | Out-Null
    } catch {
        Write-Host "WARNING: Failed to stop transcript: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "Press any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

