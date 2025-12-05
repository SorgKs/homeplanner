# PowerShell script to install and run Android app on emulator
# Automatically finds latest APK and installs it

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $scriptDir) { $scriptDir = "." }

# Start logging to file
$logFile = Join-Path $scriptDir "install_output.log"
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
"`n========================================" | Out-File -FilePath $logFile -Append -Encoding UTF8
"Install and Run - Started at $timestamp" | Out-File -FilePath $logFile -Append -Encoding UTF8
"========================================`n" | Out-File -FilePath $logFile -Append -Encoding UTF8

try {
    # Start transcript for all output
    Start-Transcript -Path $logFile -Append -NoClobber | Out-Null
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "Installing and running on emulator" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""

# Read paths from local.properties
$localPropsPath = Join-Path $scriptDir "local.properties"
if (-not (Test-Path $localPropsPath)) {
    Write-Host "ERROR: local.properties not found at $localPropsPath" -ForegroundColor Red
    exit 1
}

$properties = @{}
Get-Content $localPropsPath | ForEach-Object {
    if ($_ -match "^#") { return }
    if ($_ -notmatch "=") { return }
    $parts = $_ -split "=", 2
    if ($parts.Length -eq 2) {
        $properties[$parts[0].Trim()] = $parts[1].Trim().Trim('"')
    }
}

if (-not $properties.ContainsKey("sdk.dir")) {
    Write-Host "ERROR: sdk.dir not defined in local.properties" -ForegroundColor Red
    exit 1
}

$sdkDir = $properties["sdk.dir"].Replace('/', '\')
$adbPath = Join-Path $sdkDir "platform-tools\adb.exe"

if (-not (Test-Path $adbPath)) {
    Write-Host "ERROR: adb.exe not found at $adbPath" -ForegroundColor Red
    exit 1
}

# Check for connected devices/emulators
Write-Host "Checking for connected devices..." -ForegroundColor Gray
$devicesOutput = & $adbPath devices 2>&1
$devicesOutput | ForEach-Object { Write-Host $_ }

$deviceLines = $devicesOutput | Where-Object { $_ -match "device$" }
if ($deviceLines.Count -eq 0) {
    Write-Host ""
    Write-Host "ERROR: No devices or emulators found!" -ForegroundColor Red
    Write-Host "Please start an emulator or connect a device." -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

Write-Host ""
Write-Host "Found $($deviceLines.Count) device(s)" -ForegroundColor Green
Write-Host ""

# Find latest APK (prefer release, fallback to debug)
$releaseApkDir = Join-Path $scriptDir "app\build\outputs\apk\release"
$debugApkDir = Join-Path $scriptDir "app\build\outputs\apk\debug"

$apkFile = $null

# Try release first
if (Test-Path $releaseApkDir) {
    $releaseApks = Get-ChildItem -Path $releaseApkDir -Filter "homeplanner_*.apk" -ErrorAction SilentlyContinue
    if ($releaseApks) {
        $apkFile = $releaseApks | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        Write-Host "Found release APK: $($apkFile.Name)" -ForegroundColor Green
    }
}

# Fallback to debug
if (-not $apkFile -and (Test-Path $debugApkDir)) {
    $debugApks = Get-ChildItem -Path $debugApkDir -Filter "homeplanner_*.apk" -ErrorAction SilentlyContinue
    if ($debugApks) {
        $apkFile = $debugApks | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        Write-Host "Found debug APK: $($apkFile.Name)" -ForegroundColor Yellow
    }
}

if (-not $apkFile) {
    Write-Host ""
    Write-Host "ERROR: No APK file found!" -ForegroundColor Red
    Write-Host "Please build the app first using build.ps1 or gradlew assembleDebug" -ForegroundColor Yellow
    Write-Host ""
    exit 1
}

Write-Host "APK path: $($apkFile.FullName)" -ForegroundColor Gray
$apkSizeMB = [math]::Round($apkFile.Length / 1MB, 2)
Write-Host "APK size: $apkSizeMB MB" -ForegroundColor Gray
Write-Host ""

# Install APK
Write-Host "Installing APK on device..." -ForegroundColor Cyan
$installResult = & $adbPath install -r $apkFile.FullName 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "INSTALLATION FAILED!" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
    Write-Host ""
    Write-Host $installResult
    Write-Host ""
    exit 1
}

Write-Host ""
Write-Host "Installation successful!" -ForegroundColor Green
Write-Host ""

# Launch app
$packageName = "com.homeplanner"
$mainActivity = "com.homeplanner.MainActivity"

Write-Host "Launching app..." -ForegroundColor Cyan
$launchResult = & $adbPath shell am start -n "$packageName/$mainActivity" 2>&1

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "App launched successfully!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
} else {
    Write-Host ""
    Write-Host "WARNING: App may have failed to launch" -ForegroundColor Yellow
    Write-Host $launchResult
    Write-Host ""
}

Write-Host "Press any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
} finally {
    # Stop transcript
    Stop-Transcript | Out-Null
    Write-Host ""
    Write-Host "Log saved to: $logFile" -ForegroundColor Gray
}

