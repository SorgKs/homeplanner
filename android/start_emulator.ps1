<# 
    PowerShell script to launch Android emulator using paths from local.properties.
    Usage:
        .\start_emulator.ps1                # uses avd.name from local.properties or default
        .\start_emulator.ps1 Pixel_6_34     # start specific AVD
#>

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $scriptDir) { $scriptDir = "." }

$localPropsPath = Join-Path $scriptDir "local.properties"
if (-not (Test-Path $localPropsPath)) {
    Write-Host "ERROR: local.properties not found at $localPropsPath" -ForegroundColor Red
    exit 1
}

$properties = @{}
Get-Content $localPropsPath | ForEach-Object {
    if ($_ -match "^\s*$" -or $_ -match "^\s*#") { return }
    if ($_ -notmatch "=") { return }
    $parts = $_ -split "=", 2
    if ($parts.Length -eq 2) {
        $key = $parts[0].Trim()
        $value = $parts[1].Trim().Trim('"')
        $properties[$key] = $value
    }
}

if (-not $properties.ContainsKey("sdk.dir")) {
    Write-Host "ERROR: sdk.dir not defined in local.properties" -ForegroundColor Red
    exit 1
}

$sdkDir = $properties["sdk.dir"].Replace('/', '\')
$emulatorPath = Join-Path $sdkDir "emulator\emulator.exe"

if (-not (Test-Path $emulatorPath)) {
    Write-Host "ERROR: Emulator binary not found at $emulatorPath" -ForegroundColor Red
    exit 1
}

$avdName = $args[0]
if ([string]::IsNullOrWhiteSpace($avdName)) {
    if ($properties.ContainsKey("avd.name")) {
        $avdName = $properties["avd.name"]
    } else {
        $avdName = "HomePlanner34"
    }
}

Write-Host "Starting Android emulator: $avdName" -ForegroundColor Cyan
Write-Host "Emulator binary: $emulatorPath" -ForegroundColor Gray
Write-Host ""

Start-Process -FilePath $emulatorPath -ArgumentList "-avd", $avdName, "-netdelay", "none", "-netspeed", "full"

Write-Host ""
Write-Host "Emulator launching. Use 'adb devices' to verify once booted." -ForegroundColor Green
Write-Host ""

