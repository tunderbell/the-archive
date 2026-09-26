@echo off
setlocal enabledelayedexpansion
title The Archive // APEX Console
color 0b
cd /d "%~dp0"

echo ======================================================================
echo    THE ARCHIVE // APEX CONSOLE - TURBO RUNNER
echo ======================================================================
echo.

set STARTED_BACKEND=0

:: 1. Instant check if backend is already listening on port 61069 using native netstat (20ms)
netstat -ano | findstr /R ":61069 .*LISTENING" >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo [*] Backend engine is already active on port 61069.
    goto launch_electron
)

:: 2. Launch directly via compiled Fat JAR (2-3s) if present, else fallback to bootRun
set STARTED_BACKEND=1
if exist "build\libs\the-archive-0.0.1-SNAPSHOT.jar" (
    echo [*] Launching optimized backend engine (Fat JAR)...
    start "The Archive Engine" /min javaw -XX:+UseG1GC -jar "build\libs\the-archive-0.0.1-SNAPSHOT.jar"
) else (
    echo [*] Fat JAR not found. Starting via Gradle bootRun...
    start "The Archive Engine" /min cmd /c "cd /d "%~dp0" && .\gradlew.bat bootRun"
)

:: 3. Fast native polling (checks every 1s using native netstat)
echo [*] Initializing backend services, please wait...
set /a attempts=0
:wait_loop
set /a attempts+=1
if %attempts% gtr 30 (
    echo [!] Backend taking longer than expected. Launching frontend now...
    goto launch_electron
)
timeout /t 1 /nobreak >nul
netstat -ano | findstr /R ":61069 .*LISTENING" >nul 2>&1
if %ERRORLEVEL% neq 0 (
    goto wait_loop
)

echo [*] Backend engine is online!
echo.

:launch_electron
echo [*] Launching APEX Console Desktop Window...
cd /d "%~dp0frontend"

:: 4. Direct binary launch (skips npx resolution overhead)
if exist "node_modules\electron\dist\electron.exe" (
    "node_modules\electron\dist\electron.exe" .
) else (
    call npx electron .
)

:: 5. Clean up backend if started by this session
if "%STARTED_BACKEND%"=="1" (
    echo.
    echo [*] Stopping background backend services...
    for /f "tokens=5" %%a in ('netstat -aon ^| findstr /R ":61069 .*LISTENING"') do (
        taskkill /F /PID %%a >nul 2>&1
    )
)

echo [*] Session closed.
