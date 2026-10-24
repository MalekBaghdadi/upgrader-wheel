@echo off
setlocal
rem Builds Upgrader Wheel and puts it in the RLCraft Dregora mods folder, replacing the old version.
rem Close Minecraft first. Change MODS below if your instance lives somewhere else.
set "MODS=%APPDATA%\.minecraft\versions\RLCraftDragoraEZ\mods"
cd /d "%~dp0"

if not exist "%MODS%" (
    echo Can't find the mods folder: %MODS%
    pause
    exit /b 1
)

rem Minecraft locks the jar while it runs, so check that the old one can be removed before building
del /q "%MODS%\upgraderwheel-*.jar" 2>nul
if exist "%MODS%\upgraderwheel-*.jar" (
    echo Minecraft is still running. Close it, then run this again.
    pause
    exit /b 1
)

del /q "build\libs\*.jar" 2>nul
echo Building...
call gradlew.bat build
if errorlevel 1 (
    echo.
    echo BUILD FAILED. Send the messages above to Claude.
    pause
    exit /b 1
)

for %%f in (build\libs\upgraderwheel-*.jar) do call :install "%%f"
echo.
echo Done. Start Minecraft as usual.
pause
exit /b 0

:install
set "NAME=%~n1"
if /i not "%NAME:-dev=%"=="%NAME%" goto :eof
if /i not "%NAME:-sources=%"=="%NAME%" goto :eof
copy /y %1 "%MODS%\" >nul
echo Installed %~nx1
goto :eof
