# Build and install the app
Write-Host "Building and installing the app..."
.\gradlew.bat installDebug

# Ignore Battery Optimization
Write-Host "Granting battery optimization exemption..."
adb shell dumpsys deviceidle whitelist +com.example.extendedscreentimeout

# Enable Accessibility Service
Write-Host "Enabling Accessibility Service..."
$targetService = "com.example.extendedscreentimeout/com.example.extendedscreentimeout.DynamicTimeoutService"
$currentServices = adb shell settings get secure enabled_accessibility_services

# adb output usually has trailing newlines/carriage returns, so we trim it
if ($currentServices) {
    $currentServices = $currentServices.Trim()
}

if ($currentServices -match "com.example.extendedscreentimeout.DynamicTimeoutService") {
    Write-Host "Service is already enabled."
} else {
    $newServices = ""
    if ([string]::IsNullOrWhiteSpace($currentServices) -or $currentServices -eq "null") {
        $newServices = $targetService
    } else {
        $newServices = "${currentServices}:$targetService"
    }
    adb shell settings put secure enabled_accessibility_services $newServices
    adb shell settings put secure accessibility_enabled 1
    Write-Host "Service enabled."
}

# Launch the app
Write-Host "Launching the app..."
adb shell am start -n com.example.extendedscreentimeout/.MainActivity

Write-Host "Done!"
