@echo off
set GRADLE_VERSION=8.10.2
set CACHE_DIR=%USERPROFILE%\.gradle\custom-wrapper\gradle-%GRADLE_VERSION%
set DIST=%CACHE_DIR%\gradle-%GRADLE_VERSION%
if not exist "%DIST%\bin\gradle.bat" (
  mkdir "%CACHE_DIR%" 2>nul
  powershell -NoProfile -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%CACHE_DIR%\gradle.zip'; Expand-Archive -Force '%CACHE_DIR%\gradle.zip' '%CACHE_DIR%'"
)
call "%DIST%\bin\gradle.bat" %*
