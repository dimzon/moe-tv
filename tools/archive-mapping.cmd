@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0.."

rem ---------------------------------------------------------------------------
rem  Archive the R8 mapping.txt of every release variant into build\mapping\.
rem
rem  A crash report can only be retraced with the mapping of the exact build that
rem  produced it. app\build is erased by a clean and CI workspaces are disposable,
rem  so build-release.cmd and publish.cmd copy the map out before they finish.
rem  publish.cmd also uploads the universal map as a release asset.
rem
rem  Usage: tools\archive-mapping.cmd <variant> [variant ...]
rem    e.g. tools\archive-mapping.cmd universalRelease
rem
rem  Only the variants built by this invocation may be passed: app\build also
rem  holds mapping.txt of older builds for the other flavors, and archiving those
rem  under the current version code would mislabel retrace data.
rem
rem  File name: <variant>-<VERSION_NAME_>-<versionCode>.txt. VERSION_NAME is
rem  optional; the version code alone identifies every build.
rem ---------------------------------------------------------------------------

if "%~1"=="" (
    echo Usage: tools\archive-mapping.cmd ^<variant^> [variant ...]
    echo   e.g. tools\archive-mapping.cmd universalRelease
    exit /b 2
)

set "VC="
if exist "versioncode.txt" set /p VC=<versioncode.txt
if not defined VC set "VC=unknown"
set "VN="
if defined VERSION_NAME set "VN=%VERSION_NAME%_"

if not exist "app\build\outputs\mapping" (
    echo ERROR: app\build\outputs\mapping not found - this was not a minified release build.
    exit /b 1
)
if not exist "build\mapping" mkdir "build\mapping"

set "FOUND="
for %%V in (%*) do (
    set "DIR=app\build\outputs\mapping\%%V"
    if exist "!DIR!\mapping.txt" (
        set "DEST=build\mapping\%%V-!VN!!VC!.txt"
        copy /y "!DIR!\mapping.txt" "!DEST!" >nul
        if errorlevel 1 (
            echo ERROR: could not copy !DIR!\mapping.txt to !DEST!
            exit /b 1
        )
        echo   mapping: !DEST!
        set "FOUND=1"
    )
)
if not defined FOUND (
    echo ERROR: no mapping.txt found - refusing to lose R8 retrace data.
    exit /b 1
)
exit /b 0
