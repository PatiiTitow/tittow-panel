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
   - Stell dich nacheinander auf jede Ecke deiner Lauf-Fläche.
     Auf jeder Ecke klickst du **„+ Ecke hier“**. Bei einem L sind das **6 Ecken**, egal wie es gedreht ist.
   - Die **Reihenfolge ist egal**, der Bot erkennt die Form von alleine, auch gespiegelt oder gedreht.
     Falsch gesetzt? **„Letzte Ecke weg“**.
   - Rechts im Panel siehst du eine **Karte zur Kontrolle**: grün = da läuft er, grau/rot = gesperrt, gelb = du.
   - Optional zusätzlich den **Mob-Bereich** sperren: „Mob-Ecke 1 hier“ und „Mob-Ecke 2 hier“.
     Brauchst du nicht, wenn das L die Mobs schon ausspart.
   - Oben im Panel siehst du z. B. „Fläche 6 Ecken, 5x5, Mob-Bereich keiner, 16 freie Felder“.
   ```
   E . . . . E        E = Ecke (hier 6 Stück)
   . . . . . .        . = Lauf-Fläche
   E . . E M M        M = Mobs (da geht der Bot nie hin)
         . M M
         E M M   <- Beispiel, die Form darf beliebig gedreht sein
   ```
5. Stell dich auf die freie Fläche und klick **„Bot: AUS“** → der Bot geht an und das Fenster schließt sich.
6. Zum Ausschalten wieder **Numpad 8** und auf **„Bot: AN“** klicken.

Solange ein Menü offen ist (Inventar, Chat, Pause), macht der Bot Pause.
Wenn du stirbst oder die Welt verlässt, geht er automatisch aus.
Die Taste kannst du unter **Optionen → Steuerung → Tittow Panel** ändern.

## Was der Bot macht
- Läuft zufällig über die ganze freie Fläche, oft direkt an den Mob-Bereich, und betritt den Mob-Bereich nie
- Bleibt zum Schlagen stehen, schlägt ein paar Mal, läuft dann weiter
- Schaut beim Schlagen auf den **Körper** (etwa auf Augenhöhe), nicht hoch auf den Kopf
- Richtet sich nach der **Reichweite der Waffe**: Schwert bis 3 Blöcke, **Speer 2 bis 4,5 Blöcke**.
  Der Speer trifft nichts, was näher als 2 Blöcke steht. Dann geht der Bot einen kleinen Schritt zurück,
  wenn die Fläche dort weitergeht. Mit dem Speer schlägt er erst, wenn der Speer **ganz aufgeladen** ist.
- Schlägt nur **Monster**, niemals Spieler, Tiere, Dorfbewohner oder Golems
- **Menschlich:** weiche Kamera mit leichtem Zittern, Reaktionszeit, unregelmäßiger Schlagtakt,
  ab und zu daneben, unterschiedliche Pausen, umschauen, mal springen oder schleichen
- Nimmt das beste Schwert (sonst Axt) aus der Hotbar und isst, wenn der Hunger unter 14 fällt

## Beute werfen
Im Panel schaltet **„Beute werfen: AN/AUS“** das Werfen (am Anfang AN).
- Sammelt der Bot beim Farmen **Enderperlen, Enderaugen, Schriftrollen oder Augen** auf, wartet er 2 bis 7 Sekunden,
  schaut dann zu den Mobs und wirft sie dort hin. So sammelt der Trichter unter den Mobs sie ein.
- Er wirft nur, was **beim Farmen dazugekommen** ist. Was du beim Einschalten schon dabei hattest, bleibt bei dir.
- Waffen, Rüstung und Essen wirft er nie.
- Ziel ist der Mob-Bereich, wenn einer gesetzt ist, sonst das nächste Monster.

## Kisten leuchten grün
Im Panel schaltet **„Kisten grün: AN/AUS“** die Kisten-Anzeige (übernommen aus dem Patri-Autofarmer).
- Ungeöffnete Kisten in der Umgebung (bis 16 Chunks, höchstens die Sichtweite) bekommen einen **grünen Rahmen, auch durch Wände**.
  Höchstens 48, die nächsten zuerst. Kistenloren zählen mit.
- Kisten, die du **schon geöffnet** hast, verschwinden aus der Anzeige (wird gespeichert).
- **Lager von Spielern** (mehr als 4 Kisten beieinander oder Betten, Schilder, Türen, Fackeln … daneben) werden nicht gezeigt,
  außer in Bauwerken, die kein Spieler baut (Netherfestung, Bastion, Festung, Antike Stadt, Endsiedlung, Prüfungskammer).
- Funktioniert in jeder Welt: Oberwelt, Nether und End.
- Kisten, die rundum in Stein stecken, werden nicht gezeigt.
- Die Rahmen zeichnet **Baritone**. Ohne Baritone in Pumpcraft gibt es keine Rahmen.

## Dateien
- `src/client/java/com/tittow/panel/BotLogik.java` – Laufen, Schlagen, Essen
- `src/client/java/com/tittow/panel/TittowScreen.java` – das Fenster
- `src/client/java/com/tittow/panel/Bereich.java` – Fläche, Mob-Bereich und Einstellungen (gespeichert in `config/tittowpanel.json`)
- `src/client/java/com/tittow/panel/Kisten.java` – Kisten-Anzeige: Suchen und geöffnete Kisten merken
- `src/client/java/com/tittow/panel/BaritoneRahmen.java` – lässt Baritone die grünen Rahmen zeichnen
