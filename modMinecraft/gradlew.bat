@echo off
setlocal
set "DIR=%~dp0"

:: Detectar Gradle en ~/.gradle/wrapper/dists o PATH
set "GRADLE_EXEC="
if exist "%USERPROFILE%\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat" (
    set "GRADLE_EXEC=%USERPROFILE%\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat"
) else (
    for /f "delims=" %%g in ('dir /b /s "%USERPROFILE%\.gradle\wrapper\dists\*gradle.bat" 2^>nul') do (
        if not defined GRADLE_EXEC set "GRADLE_EXEC=%%g"
    )
)

if not defined GRADLE_EXEC (
    where gradle >nul 2>&1 && set "GRADLE_EXEC=gradle"
)

if not defined GRADLE_EXEC (
    echo [ERROR] No se encontro ninguna instalacion de Gradle.
    exit /b 1
)

:: Asegurar JAVA_HOME con Java 17
if not defined JAVA_HOME (
    if exist "C:\dev\java" set "JAVA_HOME=C:\dev\java"
)

"%GRADLE_EXEC%" %*
exit /b %errorlevel%
