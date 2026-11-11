# installieren.ps1 - baut die Tittow-Panel-Mod und legt sie direkt in den Pumpcraft-mods-Ordner.
# Claude führt das nach jeder Änderung aus. Danach nur noch Minecraft starten.
#
#   .\installieren.ps1               bauen + installieren
#   .\installieren.ps1 -NurBauen     nur bauen (z.B. wenn Minecraft gerade läuft)
#   .\installieren.ps1 -NurInstallieren   den zuletzt gebauten Stand installieren
#   -WarteAufMinecraft               läuft Pumpcraft gerade, wird nach dem Schließen installiert

param(
    [switch]$NurBauen,
    [switch]$NurInstallieren,
    [switch]$WarteAufMinecraft
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$mods   = Join-Path $env:USERPROFILE 'curseforge\minecraft\Instances\Pumpcraft\mods'
$jdk    = Join-Path $PSScriptRoot 'jdk'
$backup = Join-Path $PSScriptRoot 'alte-versionen'

function Get-GebauteJar {
    Get-ChildItem (Join-Path $PSScriptRoot 'build\libs') -Filter 'tittowpanel-*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '-sources' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
}

if (-not $NurInstallieren) {
    # ---- 1. Java 25 (JDK) besorgen, nur beim ersten Mal ----
    if (-not (Test-Path (Join-Path $jdk 'bin\javac.exe'))) {
        Write-Host '[1/3] Lade Java 25 (JDK) herunter ...'
        $zip = Join-Path $PSScriptRoot 'jdk.zip'
        $tmp = Join-Path $PSScriptRoot 'jdk-tmp'
        if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest 'https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile $zip
        Expand-Archive $zip -DestinationPath $tmp -Force
        Move-Item (Get-ChildItem $tmp | Select-Object -First 1).FullName $jdk
        Remove-Item $zip, $tmp -Recurse -Force
    } else {
        Write-Host '[1/3] Java 25 ist schon da.'
    }
    $env:JAVA_HOME = $jdk
    $env:PATH = "$jdk\bin;$env:PATH"

    # ---- 2. Mod bauen ----
    Write-Host '[2/3] Baue die Mod ...'
    # cmd übernimmt die Umleitung, damit PowerShell 5.1 Gradle-Meldungen auf stderr nicht als Fehler wertet.
    # .\ davor, weil cmd sonst je nach Einstellung (NoDefaultCurrentDirectoryInExePath) den Ordner nicht durchsucht.
    cmd /c ".\gradlew.bat build --no-daemon --console=plain > build-log.txt 2>&1"
    if ($LASTEXITCODE -ne 0) {
        Get-Content build-log.txt -Tail 40
        throw 'FEHLER beim Bauen, siehe build-log.txt'
    }
    Write-Host "      Gebaut: $((Get-GebauteJar).Name)"
}

if ($NurBauen) { return }

# ---- 3. In Pumpcraft installieren ----
Write-Host '[3/3] Installiere in Pumpcraft ...'
if (-not (Test-Path $mods)) { throw "Den mods-Ordner gibt es nicht: $mods" }

# Nie austauschen, während Pumpcraft läuft: das laufende Spiel liest noch aus der alten Datei.
$laeuft = Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" |
    Where-Object { $_.CommandLine -match 'Instances\\Pumpcraft' }
if ($laeuft) {
    if (-not $WarteAufMinecraft) {
        Write-Host 'MINECRAFT LAEUFT: Pumpcraft ist offen, die Mod wurde gebaut, aber noch nicht installiert.'
        Write-Host 'Minecraft schliessen, dann: .\installieren.ps1 -NurInstallieren (oder gleich mit -WarteAufMinecraft)'
        exit 2
    }
    Write-Host "Pumpcraft läuft noch. Warte, bis Minecraft geschlossen wird ($(Get-Date -Format 'HH:mm:ss')) ..."
    $laeuft | ForEach-Object { Wait-Process -Id $_.ProcessId -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 3   # bis Windows die Datei freigegeben hat
    Write-Host "Minecraft ist zu ($(Get-Date -Format 'HH:mm:ss')), installiere jetzt."
}
$neu = Get-GebauteJar
if (-not $neu) { throw 'Keine gebaute Mod gefunden. Erst ohne -NurInstallieren ausführen.' }

# Erst als .neu ablegen (Fabric ignoriert das), dann die alte Version austauschen.
$ziel   = Join-Path $mods 'tittowpanel.jar'
$staged = Join-Path $mods 'tittowpanel.jar.neu'
Copy-Item $neu.FullName $staged -Force

$alte = @(Get-ChildItem $mods -Filter 'tittowpanel*.jar')
New-Item -ItemType Directory -Force $backup | Out-Null
$stempel = Get-Date -Format 'yyyyMMdd-HHmmss'
try {
    foreach ($a in $alte) {
        Copy-Item $a.FullName (Join-Path $backup "$stempel-$($a.Name)") -Force
        Remove-Item $a.FullName -Force
    }
} catch {
    Remove-Item $staged -Force -ErrorAction SilentlyContinue
    throw 'Minecraft läuft noch und hält die alte Mod fest. Bitte Minecraft schließen und nochmal installieren.'
}
Move-Item $staged $ziel -Force

# Nur die letzten 10 Sicherungen behalten.
Get-ChildItem $backup -Filter '*.jar' | Sort-Object LastWriteTime -Descending | Select-Object -Skip 10 | Remove-Item -Force

if (-not (Get-ChildItem $mods -Filter 'fabric-api*.jar')) {
    Write-Warning 'In Pumpcraft fehlt "Fabric API". Bitte in CurseForge installieren.'
}
$hash = (Get-FileHash $ziel -Algorithm SHA256).Hash.Substring(0, 12)
Write-Host "Fertig! $ziel ($((Get-Item $ziel).Length) Bytes, SHA256 $hash...)"
Write-Host 'Jetzt einfach Minecraft (Pumpcraft) starten.'
