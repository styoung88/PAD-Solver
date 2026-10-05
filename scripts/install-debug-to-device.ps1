param(
    [string]$Serial
)

$ErrorActionPreference = 'Stop'

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Split-Path -Parent $scriptDirectory
$androidSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb = Join-Path $androidSdk 'platform-tools\adb.exe'
$apk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$androidUserHome = Join-Path $projectRoot '.android-user-home'
$gradleUserHome = Join-Path $env:USERPROFILE '.gradle'
$javaHome = 'C:\Program Files\Android\Android Studio\jbr'

if (-not (Test-Path -LiteralPath $adb)) {
    throw "ADB was not found at $adb"
}
if (-not (Test-Path -LiteralPath (Join-Path $javaHome 'bin\java.exe'))) {
    throw "Android Studio's Java runtime was not found at $javaHome"
}

New-Item -ItemType Directory -Path $androidUserHome -Force | Out-Null
$env:JAVA_HOME = $javaHome
$env:GRADLE_USER_HOME = $gradleUserHome
$env:ANDROID_HOME = $androidSdk
$env:ANDROID_SDK_ROOT = $androidSdk
$env:ANDROID_USER_HOME = $androidUserHome
$env:ANDROID_SERIAL = $null

$deviceLines = & $adb devices
if ($LASTEXITCODE -ne 0) {
    throw 'ADB could not list connected devices.'
}
$physicalDevices = @(
    $deviceLines | ForEach-Object {
        if ($_ -match '^([^\s]+)\s+device(?:\s|$)' -and $Matches[1] -notmatch '^emulator-') {
            $Matches[1]
        }
    }
)

if ([string]::IsNullOrWhiteSpace($Serial)) {
    if ($physicalDevices.Count -ne 1) {
        throw "Found $($physicalDevices.Count) physical Android devices. Re-run with -Serial <device-serial>."
    }
    $Serial = $physicalDevices[0]
} elseif ($Serial -notin $physicalDevices) {
    throw "Device '$Serial' is not connected and authorized."
}

Push-Location $projectRoot
try {
    & '.\gradlew.bat' --offline ':app:assembleDebug'
    if ($LASTEXITCODE -ne 0) {
        throw 'Debug APK build failed.'
    }
} finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $apk)) {
    throw "Build completed without producing $apk"
}

& $adb -s $Serial install -r $apk
if ($LASTEXITCODE -ne 0) {
    throw "APK installation failed on device '$Serial'."
}

Write-Output "Installed the current debug APK on $Serial."
