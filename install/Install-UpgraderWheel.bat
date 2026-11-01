@echo off
rem Upgrader Wheel installer for Windows: double-click to install the newest Upgrader Wheel into a Minecraft instance.
rem Runs install.ps1 from the same folder, or downloads it from GitHub when this file was downloaded on its own.
setlocal
set "SCRIPT=%~dp0install.ps1"
if not exist "%SCRIPT%" goto download
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%" %*
goto done
:download
powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12; irm https://raw.githubusercontent.com/MalekBaghdadi/upgrader-wheel/main/install/install.ps1 | iex"
:done
exit /b %ERRORLEVEL%
