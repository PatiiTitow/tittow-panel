@echo off
chcp 65001 >nul
title Tittow Panel - aktualisieren
rem Lädt die neueste fertige Tittow-Panel-Mod von GitHub und legt sie in den Pumpcraft-mods-Ordner.
set "REPO=PatiiTitow/tittow-panel"
set "MODS=%USERPROFILE%\curseforge\minecraft\Instances\Pumpcraft\mods"

echo.
echo  ===== Tittow Panel: neueste Version holen =====
echo.
if not exist "%MODS%" (
  echo Den Ordner %MODS% gibt es nicht.
  pause
  exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "Invoke-WebRequest 'https://github.com/%REPO%/releases/download/neueste/tittowpanel.jar' -OutFile \"$env:TEMP\tittowpanel.jar\""
if errorlevel 1 (
  echo FEHLER: Konnte die Mod nicht herunterladen. Ist das Internet an?
  pause
  exit /b 1
)
del /q "%MODS%\tittowpanel*.jar" 2>nul
move /y "%TEMP%\tittowpanel.jar" "%MODS%\tittowpanel.jar" >nul
echo Fertig! Die neueste Mod liegt jetzt in: %MODS%
dir /b "%MODS%\fabric-api*.jar" >nul 2>&1
if errorlevel 1 echo ACHTUNG: In Pumpcraft fehlt "Fabric API". Bitte in CurseForge installieren.
echo.
echo Wenn Minecraft gerade laeuft: einmal neu starten, dann ist die neue Version aktiv.
pause
