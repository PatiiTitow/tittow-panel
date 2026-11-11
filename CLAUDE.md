# Tittow Panel

Fabric-Client-Mod für Minecraft 26.3 (Java 25, Loom). Läuft in der CurseForge-Instanz **Pumpcraft**:
`%USERPROFILE%\curseforge\minecraft\Instances\Pumpcraft\mods`. Beschreibung und Bedienung stehen in `ANLEITUNG.md`.
Der Bot ist nur für die eigene Welt oder den eigenen Server gedacht.

Der Nutzer schreibt Deutsch. Antworte auf Deutsch und einfach, ohne Fachjargon.

## Arbeitsablauf: Änderung → sofort installieren

Der Nutzer will nichts selbst ausführen. Er sagt, was geändert werden soll, und startet danach nur Minecraft.
Nach jeder Code-Änderung deshalb selbst bauen und installieren:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\installieren.ps1
```

- Das Skript lädt beim ersten Mal ein JDK 25 nach `jdk\` und baut mit `gradlew build`. Das Log steht in `build-log.txt`.
- Danach legt es die Mod als `mods\tittowpanel.jar` ab. Die vorherige Version wird nach `alte-versionen\` gesichert.
- Solange Pumpcraft läuft, tauscht das Skript nichts aus (Exit-Code 2), weil das laufende Spiel noch aus der alten jar liest. Läuft Minecraft, das Skript mit `-WarteAufMinecraft` im Hintergrund starten (`run_in_background`). Dann installiert es von selbst, sobald der Nutzer Minecraft schließt, und beim nächsten Start ist die neue Version aktiv. Das dem Nutzer so sagen.
- Im mods-Ordner darf nur **eine** `tittowpanel*.jar` liegen, sonst stürzt Fabric wegen einer doppelten Mod ab.
- Ändert sich das Verhalten, auch `ANLEITUNG.md` anpassen.

## GitHub

Dieser Ordner ist ein Klon von `PatiiTitow/tittow-panel` (Branch `main`). Bei jedem Push baut `.github/workflows/build.yml` das Release `neueste`, das `aktualisieren.bat` herunterlädt.

- Git liegt unter `C:\Program Files\Git\cmd\git.exe` (seit 2026-11-11). Falls `git` nicht im PATH ist, diesen Pfad verwenden. Die Anmeldung läuft über den Git Credential Manager.
- Nach jeder Änderung, die gebaut und installiert wurde, auch committen und nach `main` pushen, damit GitHub und das Release aktuell bleiben. Vorher `git pull --rebase`, falls inzwischen woanders Änderungen dazugekommen sind.
- Commit-Autor ist wie bisher `Claude <noreply@anthropic.com>` (lokal im Repo eingestellt). Commit-Nachrichten kurz und auf Deutsch.
- Der lokale Build ist byte-gleich mit dem GitHub-Build. `aktualisieren.bat` holt also denselben Stand, sobald gepusht ist.
