@echo off
setlocal EnableExtensions
cd /d "%~dp0"

rem ---------------------------------------------------------------------------
rem  MyOwnTV - release build (cmd.exe)
rem
rem  Usage: build-release.cmd [universal|standard|x86_64|all]
rem    universal  all ABIs in one APK — the shipped flavor; DEFAULT
rem    standard   arm64-v8a + armeabi-v7a (legacy, not published)
rem    x86_64     x86_64 emulator (legacy, not published)
rem    all        every ABI flavor (:app:assembleRelease)
rem
rem  Release is minified with R8 and signed when a keystore is configured:
rem  keystore.properties in the repo root, KEYSTORE_FILE/KEYSTORE_PASSWORD/
rem  KEY_ALIAS/KEY_PASSWORD env vars, or owntv.keystore* Gradle properties.
rem
rem  Output: app\build\outputs\apk\<flavor>\release\
rem ---------------------------------------------------------------------------

set "FLAVOR=%~1"
if not defined FLAVOR set "FLAVOR=universal"

if /I "%FLAVOR%"=="all"       goto :flavor_all
if /I "%FLAVOR%"=="standard"  goto :flavor_standard
if /I "%FLAVOR%"=="x86_64"    goto :flavor_x86
if /I "%FLAVOR%"=="universal" goto :flavor_universal
goto :usage

:flavor_all
set "TASK=assembleRelease"
set "OUTDIR=app\build\outputs\apk"
goto :run

:flavor_standard
set "TASK=assembleStandardRelease"
set "OUTDIR=app\build\outputs\apk\standard\release"
goto :run

:flavor_x86
set "TASK=assembleX86_64Release"
set "OUTDIR=app\build\outputs\apk\x86_64\release"
goto :run

:flavor_universal
set "TASK=assembleUniversalRelease"
set "OUTDIR=app\build\outputs\apk\universal\release"
goto :run

:usage
echo Unknown flavor "%FLAVOR%".
echo Usage: build-release.cmd [standard^|x86_64^|universal^|all]
exit /b 2

:run
if not exist "gradlew.bat" (
    echo ERROR: gradlew.bat not found. Run this script from the repository root.
    exit /b 2
)

if not exist "keystore.properties" if not defined KEYSTORE_FILE (
    echo NOTE: keystore.properties not found in the repo root.
    echo       Signing falls back to KEYSTORE_FILE or owntv.keystore* Gradle
    echo       properties; if neither is configured the APK will be unsigned.
    echo.
)

echo === MyOwnTV - release build: %FLAVOR% ===
echo Task: :app:%TASK%
echo.

call "%~dp0gradlew.bat" ":app:%TASK%" --stacktrace
if errorlevel 1 (
    echo.
    echo BUILD FAILED. See the Gradle output above.
    exit /b 1
)

echo.
echo BUILT APK:
for /r "%OUTDIR%" %%F in (*.apk) do echo   %%~fF  [%%~zF bytes]

set "VC="
if exist "versioncode.txt" set /p VC=<versioncode.txt
if defined VC echo.
if defined VC echo versioncode.txt: %VC% ^(next build uses the next number^)

echo.
echo Done.
exit /b 0
