@echo off
chcp 65001 >nul
title Tittow Panel - Mod bauen
cd /d "%~dp0"
echo.
echo  ===== Tittow Panel: Mod bauen =====
echo.

rem ---- 1. Java 25 besorgen (wird nur beim ersten Mal heruntergeladen) ----
if not exist "jdk\bin\java.exe" (
  echo [1/3] Lade Java 25 herunter, das dauert einen Moment ...
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ErrorActionPreference='Stop';" ^
    "Invoke-WebRequest 'https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile 'jdk.zip';" ^
    "Expand-Archive 'jdk.zip' -DestinationPath 'jdk-tmp' -Force;" ^
    "Move-Item (Get-ChildItem 'jdk-tmp' | Select-Object -First 1).FullName 'jdk';" ^
    "Remove-Item 'jdk.zip','jdk-tmp' -Recurse -Force"
  if not exist "jdk\bin\java.exe" (
    echo FEHLER: Java konnte nicht heruntergeladen werden. Ist das Internet an?
    pause
    exit /b 1
  )
) else (
  echo [1/3] Java 25 ist schon da.
)
set "JAVA_HOME=%~dp0jdk"
set "PATH=%JAVA_HOME%\bin;%PATH%"

rem ---- 2. Mod bauen ----
echo [2/3] Baue die Mod, beim ersten Mal dauert das ein paar Minuten ...
call gradlew.bat build --no-daemon --console=plain > build-log.txt 2>&1
if errorlevel 1 (
  echo.
  echo FEHLER beim Bauen. Hier die letzten Zeilen:
  echo ------------------------------------------------------------
  powershell -NoProfile -Command "Get-Content build-log.txt -Tail 40"
  echo ------------------------------------------------------------
  echo Bitte schick die Datei build-log.txt an Claude, dann wird es repariert.
  pause
  exit /b 1
)

rem ---- 3. In den mods-Ordner von Pumpcraft kopieren ----
set "MODS=%USERPROFILE%\curseforge\minecraft\Instances\Pumpcraft\mods"
if not exist "%MODS%" (
  echo Den Ordner %MODS% gibt es nicht.
  echo Die fertige Mod liegt hier: %~dp0build\libs\tittowpanel-1.0.0.jar
  pause
  exit /b 0
)
del /q "%MODS%\tittowpanel*.jar" 2>nul
copy /y "build\libs\tittowpanel-1.0.0.jar" "%MODS%\tittowpanel.jar" >nul
echo [3/3] Fertig! Die Mod liegt jetzt in: %MODS%

dir /b "%MODS%\fabric-api*.jar" >nul 2>&1
if errorlevel 1 (
  echo.
  echo ACHTUNG: In Pumpcraft fehlt "Fabric API". Installier sie in CurseForge, sonst startet die Mod nicht.
)
echo.
echo Starte jetzt Pumpcraft in CurseForge und drueck im Spiel Numpad 8.
pause
