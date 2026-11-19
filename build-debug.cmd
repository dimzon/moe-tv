@echo off
setlocal EnableExtensions
cd /d "%~dp0"

rem ---------------------------------------------------------------------------
rem  MyOwnTV - debug build (cmd.exe)
rem
rem  Usage: build-debug.cmd [universal|standard|x86_64|all]
rem    universal  all ABIs in one APK — the shipped flavor; DEFAULT
rem    standard   arm64-v8a + armeabi-v7a (legacy, not published)
rem    x86_64     x86_64 emulator (legacy, not published)
rem    all        every ABI flavor (:app:assembleDebug)
rem
rem  Output: app\build\outputs\apk\<flavor>\debug\
rem ---------------------------------------------------------------------------

set "FLAVOR=%~1"
if not defined FLAVOR set "FLAVOR=universal"

if /I "%FLAVOR%"=="all"       goto :flavor_all
if /I "%FLAVOR%"=="standard"  goto :flavor_standard
if /I "%FLAVOR%"=="x86_64"    goto :flavor_x86
if /I "%FLAVOR%"=="universal" goto :flavor_universal
goto :usage

:flavor_all
set "TASK=assembleDebug"
set "OUTDIR=app\build\outputs\apk"
goto :run

:flavor_standard
set "TASK=assembleStandardDebug"
set "OUTDIR=app\build\outputs\apk\standard\debug"
goto :run

:flavor_x86
set "TASK=assembleX86_64Debug"
set "OUTDIR=app\build\outputs\apk\x86_64\debug"
goto :run

:flavor_universal
set "TASK=assembleUniversalDebug"
set "OUTDIR=app\build\outputs\apk\universal\debug"
goto :run

:usage
echo Unknown flavor "%FLAVOR%".
echo Usage: build-debug.cmd [standard^|x86_64^|universal^|all]
exit /b 2

:run
if not exist "gradlew.bat" (
    echo ERROR: gradlew.bat not found. Run this script from the repository root.
    exit /b 2
)

echo.
echo === MyOwnTV - debug build: %FLAVOR% ===
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
