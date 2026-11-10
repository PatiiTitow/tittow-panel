# Tittow Panel – Mod für Minecraft 26.3 (Fabric)

Diese Mod lässt **deine eigene Spielfigur** in deiner Mob-Farm laufen und Mobs schlagen.
**Numpad 8** öffnet im Spiel das **Tittow Panel**, dort schaltest du den Bot an und aus.

> Nur für deine eigene Welt oder deinen eigenen Server. Auf fremden Servern ist so etwas verboten.

## Einmalig: Mod bauen und installieren
1. Entpacke `TittowPanel-Mod.zip` z. B. auf den Desktop.
2. Doppelklick auf **`bauen.bat`**.
   - Beim ersten Mal lädt es Java 25 und die Minecraft-Bauteile herunter (ein paar Minuten).
   - Danach wird die Mod gebaut und automatisch in
     `curseforge\minecraft\Instances\Pumpcraft\mods` gelegt.
3. Falls ein **FEHLER** kommt: schick die Datei `build-log.txt` aus dem Ordner an Claude.
4. In Pumpcraft muss **Fabric API** installiert sein (das prüft `bauen.bat` und sagt Bescheid).

## Im Spiel
1. Pumpcraft starten und in deine Welt gehen.
2. Schwert und Essen in die **Hotbar** legen (der Bot nimmt nur Sachen aus der Hotbar).
3. **Numpad 8** drücken (NumLock an) → das Tittow Panel geht auf.
4. Fläche einrichten (einmalig, wird gespeichert):
   - Stell dich auf eine Ecke der **ganzen Fläche** → **„Ecke 1 hier“**
   - Stell dich auf die gegenüberliegende Ecke → **„Ecke 2 hier“**
   - Stell dich auf eine Ecke vom **Mob-Bereich** → **„Mob-Ecke 1 hier“**
   - Gegenüberliegende Ecke vom Mob-Bereich → **„Mob-Ecke 2 hier“**
   - Oben im Panel siehst du z. B. „Fläche 5x5, Mob-Bereich 3x3, 16 freie Felder“.
5. Stell dich auf die freie Fläche und klick **„Bot: AUS“** → der Bot geht an und das Fenster schließt sich.
6. Zum Ausschalten wieder **Numpad 8** und auf **„Bot: AN“** klicken.

Solange ein Menü offen ist (Inventar, Chat, Pause), macht der Bot Pause.
Wenn du stirbst oder die Welt verlässt, geht er automatisch aus.
Die Taste kannst du unter **Optionen → Steuerung → Tittow Panel** ändern.

## Was der Bot macht
- Läuft zufällig über die ganze freie Fläche, oft direkt an den Mob-Bereich, und betritt den Mob-Bereich nie
- Bleibt zum Schlagen stehen, schlägt ein paar Mal, läuft dann weiter
- Schlägt nur **Monster**, niemals Spieler, Tiere, Dorfbewohner oder Golems
- **Menschlich:** weiche Kamera mit leichtem Zittern, Reaktionszeit, unregelmäßiger Schlagtakt,
  ab und zu daneben, unterschiedliche Pausen, umschauen, mal springen oder schleichen
- Nimmt das beste Schwert (sonst Axt) aus der Hotbar und isst, wenn der Hunger unter 14 fällt

## Dateien
- `src/client/java/com/tittow/panel/BotLogik.java` – Laufen, Schlagen, Essen
- `src/client/java/com/tittow/panel/TittowScreen.java` – das Fenster
- `src/client/java/com/tittow/panel/Bereich.java` – Fläche und Mob-Bereich (gespeichert in `config/tittowpanel.json`)
