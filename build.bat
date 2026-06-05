@echo off
for /d %%i in ("%USERPROFILE%\.gradle\wrapper\dists\gradle-8.5-bin\*") do (
    for /d %%j in ("%%i\gradle-*") do (
        "%%j\bin\gradle.bat" assembleDebug > build_output.txt 2>&1
        goto :end
    )
)
:end
