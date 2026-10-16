param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $projectRoot '.android-user-home'
$apksigner = Join-Path $env:ANDROID_HOME 'build-tools\36.0.0\apksigner.bat'
$apkDirectory = Join-Path $projectRoot 'app\build\outputs\apk\release'
$apk = Join-Path $apkDirectory 'app-release.apk'

if (-not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    throw "Android Studio's Java runtime was not found at $env:JAVA_HOME"
}
if (-not (Test-Path -LiteralPath $apksigner)) {
    throw "APK signature verifier was not found at $apksigner"
}

Push-Location $projectRoot
try {
    & '.\gradlew.bat' --offline ':app:validateRelease'
    if ($LASTEXITCODE -ne 0) { throw 'Release identity or signing configuration is incomplete.' }

    & '.\gradlew.bat' --offline ':app:testDebugUnitTest' ':app:lintRelease' ':app:assembleRelease'
    if ($LASTEXITCODE -ne 0) { throw 'Release checks or APK build failed.' }
    if (-not (Test-Path -LiteralPath $apk)) { throw "Build did not produce $apk" }

    & $apksigner verify --verbose --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw 'Release APK signature verification failed.' }

    $metadata = Get-Content -LiteralPath (Join-Path $apkDirectory 'output-metadata.json') -Raw | ConvertFrom-Json
    $versionName = $metadata.elements[0].versionName
    if ($versionName -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]*$') {
        throw "Version name '$versionName' cannot be used in an APK filename."
    }
    $distributionDirectory = Join-Path $projectRoot 'app\build\outputs\distribution'
    New-Item -ItemType Directory -Path $distributionDirectory -Force | Out-Null
    $distributionApk = Join-Path $distributionDirectory "PAD-Solver-$versionName.apk"
    Copy-Item -LiteralPath $apk -Destination $distributionApk
    $hash = (Get-FileHash -LiteralPath $distributionApk -Algorithm SHA256).Hash.ToLowerInvariant()
    $filename = Split-Path -Leaf $distributionApk
    Set-Content -LiteralPath "$distributionApk.sha256" -Value "$hash  $filename" -Encoding Ascii
    Write-Output "Signed APK: $distributionApk"
    Write-Output "SHA-256 checksum: $distributionApk.sha256"
} finally {
    Pop-Location
}
