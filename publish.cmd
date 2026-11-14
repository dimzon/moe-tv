@echo off
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0"

rem ---------------------------------------------------------------------------
rem  МОЁ ТВ - publish a release to the download site
rem
rem  Usage: publish.cmd ^<version^> [notes-file]
rem    ^<version^>     short version, e.g. 1.1 -> tag v1.1 on the site repo
rem    [notes-file]  optional Markdown file with release notes
rem
rem  Does, in order:
rem    1. builds the universal release APK (VERSION_NAME=<version>,
rem       VERSION_CODE still auto-increments from versioncode.txt);
rem    2. copies it to %TEMP%\MoeTV_<version>_universal.apk;
rem    3. creates (or updates) the GitHub release v<version> on the public
rem       download-site repo and uploads the APK there - the app's in-app
rem       updater reads exactly that release;
rem    4. when the site checkout below exists, puts <version> on the download
rem       page and pushes it.
rem
rem  Requires: gh (logged in), release keystore configured.
rem  Build only: build-release.cmd universal
rem ---------------------------------------------------------------------------

set "VER=%~1"
set "NOTES=%~2"
if not defined VER goto :usage

where gh >nul 2>nul || (echo gh CLI not found in PATH & exit /b 1)

set "SITE_DIR=C:\Work\moe-tv-site"
set "SITE_REPO=dimzon/moe-tv-site"
set "APK=app\build\outputs\apk\universal\release\app-universal-release.apk"
set "ASSET=%TEMP%\MoeTV_%VER%_universal.apk"
set "BODY=%TEMP%\moetv-release-notes.md"

echo === Building universal release %VER% ===
set "VERSION_NAME=%VER%"
call gradlew.bat :app:assembleUniversalRelease || exit /b 1
if not exist "%APK%" (echo APK not found: %APK% & exit /b 1)

copy /y "%APK%" "%ASSET%" >nul || exit /b 1

if defined NOTES (
  copy /y "%NOTES%" "%BODY%" >nul
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "Set-Content -Encoding UTF8 '%BODY%' 'МОЁ ТВ %VER%.`n`nУстановка: adb install MoeTV_%VER%_universal.apk (Android 8.0+, ARM и x86_64).'"
)

echo === Publishing v%VER% to %SITE_REPO% ===
gh release view v%VER% --repo %SITE_REPO% >nul 2>nul
if errorlevel 1 (
  gh release create v%VER% "%ASSET%" --repo %SITE_REPO% --title "МОЁ ТВ %VER%" --notes-file "%BODY%" || exit /b 1
) else (
  echo Release v%VER% exists - replacing its APK asset.
  gh release upload v%VER% "%ASSET%" --repo %SITE_REPO% --clobber || exit /b 1
)

if exist "%SITE_DIR%\index.html" (
  echo === Updating the download page ===
  pushd "%SITE_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$c = Get-Content index.html -Raw; $c = $c -replace '(?<=<span id=\"ver\">)[^<]+', '%VER%'; $c = $c -replace 'adb install MoeTV_[0-9.]+_universal\.apk', 'adb install MoeTV_%VER%_universal.apk'; Set-Content index.html $c -NoNewline"
  git add index.html && git -c user.name="publish.cmd" commit -qm "Версия %VER%" && git push -q origin main
  popd
)

echo.
echo Done: https://github.com/%SITE_REPO%/releases/tag/v%VER%
exit /b 0

:usage
echo Usage: publish.cmd ^<version^> [notes-file]
echo   e.g. publish.cmd 1.1
echo.
echo Build only: build-release.cmd universal
exit /b 1
