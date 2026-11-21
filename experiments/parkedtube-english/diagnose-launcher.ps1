# Inspect Android Auto launcher eligibility without modifying the device.
# Windows PowerShell + Android platform-tools (adb) required.
# Usage: .\diagnose-launcher.ps1
param(
  [string]$Package = "dev.codex.aaparkedyoutube.en.debug"
)
$ErrorActionPreference = "Stop"
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
  throw "adb is missing. Install Android SDK Platform-Tools and place adb on PATH."
}
Write-Host "== Device connection ==" -ForegroundColor Cyan
& adb devices
Write-Host "== Android OS version =="
$android = (& adb shell getprop ro.build.version.release | Out-String).Trim()
Write-Host "Android: $android (official parked games require Android 15+)"
Write-Host "== Android Auto installed package =="
$autoPackage = "com.google.android.projection.gearhead"
$autoInfo = (& adb shell dumpsys package $autoPackage | Out-String)
if ($autoInfo -match 'versionName=([^\r\n]+)') {
  Write-Host ("Android Auto version: " + $Matches[1].Trim())
} else {
  Write-Warning "Android Auto version could not be read."
}
Write-Host "== English test package =="
$found = (& adb shell pm list packages $Package | Out-String)
if ($found -notmatch [regex]::Escape($Package)) {
  Write-Warning "$Package not installed. Check if APK installed with a different application ID."
  Write-Host "Related packages:"
  & adb shell pm list packages | Select-String 'parked|codex|sokytube|carbridge'
  exit 1
}
Write-Host "Found: $Package"
$details = (& adb shell dumpsys package $Package | Out-String)
if ($details -match "CAR_LAUNCHER") {
  Write-Host "CAR_LAUNCHER: present in package dump"
} else {
  Write-Warning "CAR_LAUNCHER: not detected in dumpsys. Check packaged manifest with aapt."
}
if ($details -match 'android.intent.action.MAIN') {
  Write-Host "MAIN activity: found"
} else {
  Write-Warning "MAIN activity: not detected"
}
Write-Host "== Interpretation =="
Write-Host "If the app is installed and CAR_LAUNCHER exists but it is absent from Customize launcher,"
Write-Host "Android Auto may be filtering the sideloaded or unsupported video app."
Write-Host "Check Android Auto > Developer settings > Unknown sources, then Customize launcher."
Write-Host "Reconnect while safely parked. No driver-distraction protections are disabled."
Write-Host "This script does not diagnose YouTube's WebView failure."
