# Builds Parents TV: runs the unit tests, builds the APKs and copies the release APK to
# apk\ParentsTV.apk (kept in the repository so a TV can be set up without building).
#   .\build.ps1                       tests + APKs
#   .\build.ps1 -Install              also installs on every connected device (emulator / TV)
#   .\build.ps1 -Install -Device 192.168.1.13:5555   installs on one device only
param([switch]$Install, [string]$Device)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
# Android Studio's bundled JDK; change this if Android Studio is installed elsewhere.
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'

if (-not (Test-Path .\gradlew.bat)) {
    $gradle = Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.14.3-bin" -Recurse -Filter gradle.bat |
        Select-Object -First 1 -ExpandProperty FullName
    & $gradle wrapper --gradle-version 8.14.3 -q
    if ($LASTEXITCODE -ne 0) { throw 'Gradle wrapper generation failed' }
}

.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }

New-Item -ItemType Directory -Force .\apk | Out-Null
Copy-Item .\app\build\outputs\apk\release\app-release.apk .\apk\ParentsTV.apk -Force
$apk = Resolve-Path .\apk\ParentsTV.apk
Write-Host "APK: $apk"

if ($Install) {
    $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
    $devices = if ($Device) { @($Device) } else {
        & $adb devices | Select-String '\tdevice$' | ForEach-Object { ($_ -split "`t")[0] }
    }
    foreach ($d in $devices) {
        Write-Host "Installing on $d"
        & $adb -s $d install -r $apk
    }
}
Write-Host 'BUILD DONE'
