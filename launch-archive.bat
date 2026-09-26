@echo off
setlocal enabledelayedexpansion
title The Archive // APEX Console
color 0b
cd /d "%~dp0"

echo ======================================================================
echo    THE ARCHIVE // APEX CONSOLE - DESKTOP RUNNER
echo ======================================================================
echo.

set STARTED_BACKEND=0

:: Check if backend is already listening on port 61069
powershell -NoProfile -Command "if ((Get-NetTCPConnection -LocalPort 61069 -State Listen -ErrorAction SilentlyContinue).Count -gt 0) { exit 0 } else { exit 1 }"
if %ERRORLEVEL% equ 0 (
    echo [*] Backend engine is already active on port 61069.
    goto launch_electron
)

echo [*] Starting Spring Boot backend engine on port 61069...
set STARTED_BACKEND=1
start "The Archive Engine" /min cmd /c "cd /d "%~dp0" && .\gradlew.bat bootRun"

echo [*] Initializing backend services, please wait...
set /a attempts=0
:wait_loop
set /a attempts+=1
if %attempts% gtr 45 (
    echo [!] Backend is taking longer than expected. Launching frontend now...
    goto launch_electron
)
timeout /t 2 /nobreak >nul
powershell -NoProfile -Command "if ((Get-NetTCPConnection -LocalPort 61069 -State Listen -ErrorAction SilentlyContinue).Count -gt 0) { exit 0 } else { exit 1 }"
if %ERRORLEVEL% neq 0 (
    echo     Waiting for port 61069... (!attempts!/45)
    goto wait_loop
)

echo [*] Backend engine is online and ready!
echo.

:launch_electron
echo [*] Launching APEX Console Desktop App...
cd /d "%~dp0frontend"
call npx electron .

:: Clean up background backend if started by this script
if "%STARTED_BACKEND%"=="1" (
    echo.
    echo [*] Stopping background backend services...
    powershell -NoProfile -Command "$conn = Get-NetTCPConnection -LocalPort 61069 -State Listen -ErrorAction SilentlyContinue; if ($conn) { Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue }"
)

echo [*] Session closed.
