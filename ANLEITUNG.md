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

### Normal: am Rand stehen und einfach zuschlagen
Weiß der Bot, wo die Mobs spawnen, stellt er sich auf ein Feld **direkt am Rand** (ein Feld außen herum) und schlägt
**einfach immer wieder hinein**. Er wartet nicht, bis er einen Mob erkennt, denn dort spawnen sowieso immer welche.
- Die Schlag-Stelle findet er so (der Reihe nach):
  1. der **Mob-Bereich**, wenn du einen gesetzt hast,
  2. die **Trichter** in der Nähe, z. B. deine 3x3 Trichter mit Teppich
     (Trichter unter einem ganzen Block, einer Kiste oder einem Fass zählen nicht),
  3. die **grauen Felder in der Karte**, die an deine Fläche grenzen (z. B. die Ecke im L).
- Im Panel steht neben den Kills, was er gefunden hat, z. B. „Schlag-Stelle: 9 Trichter“.
- **Menschlich:** Er schlägt im Takt der Waffe, mal sofort, mal etwas später. Er zielt in Körperhöhe, meistens auf
  einen Enderman, den er sieht, sonst irgendwo über die Trichter. Die Maus wandert dabei ein bisschen.
  Ab und zu macht er eine Pause (1 bis 4 Sekunden, selten 6 bis 12), schaut kurz zur Seite oder geht ein Feld weiter.
- Er klickt nie auf Spieler, Tiere, Dorfbewohner oder Rahmen und nie auf Blöcke, die davon kaputtgehen (z. B. Fackeln).

### Ohne Schlag-Stelle (z. B. einfache rechteckige Fläche ohne Trichter)
- Läuft zufällig über die ganze freie Fläche, oft direkt an den Mob-Bereich, und betritt den Mob-Bereich nie
- Bleibt zum Schlagen stehen, schlägt ein paar Mal, läuft dann weiter
- Schaut beim Schlagen auf den **Körper** (etwa auf Augenhöhe), nicht hoch auf den Kopf
- Greift nur Mobs an, die er **wirklich sieht**. Mobs hinter der Decke, unter dem Boden oder hinter einer Wand lässt er in Ruhe.
- Richtet sich nach der **Reichweite der Waffe**: Schwert bis 3 Blöcke, **Speer 2 bis 4,5 Blöcke**.
  Der Speer trifft nichts, was näher als 2 Blöcke steht. Dann geht der Bot einen kleinen Schritt zurück,
  wenn die Fläche dort weitergeht. Mit dem Speer schlägt er erst, wenn der Speer **ganz aufgeladen** ist.
- Schlägt nur **Monster**, niemals Spieler, Tiere, Dorfbewohner oder Golems
- **Menschlich:** weiche Kamera mit leichtem Zittern, Reaktionszeit, unregelmäßiger Schlagtakt,
  ab und zu daneben, unterschiedliche Pausen, umschauen, mal springen oder schleichen
- Nimmt das beste Schwert (sonst Axt) aus der Hotbar und isst, wenn der Hunger unter 14 fällt

## Beute werfen
Im Panel schaltet **„Beute werfen: AN/AUS“** das Werfen (am Anfang AN).
- Geworfen werden **Power Orbs**, **Scrolls** (alles mit „Scroll“ im Namen) und **Enderperlen**.
- Er wirft erst, wenn das **Inventar voll** ist. Dann sucht er die **Trichter** unter den Mobs (z. B. deine 3x3 Trichter)
  und stellt sich auf ein freies Feld **direkt daneben** (ein Feld außen herum, am liebsten an eine Seite, sonst an eine Ecke).
  Von dort zielt er auf die Mitte der Trichter und wirft die Sachen hinein. Danach farmt er normal weiter.
- **Teppich auf den Trichtern ist in Ordnung**, die Trichter holen die Sachen trotzdem. Trichter, über denen ein ganzer Block ist
  (z. B. eine Trichter-Kette unter dem Boden), zählen nicht.
- Findet er keine Trichter, geht er an den Rand zu den Mobs und wirft dort hinein.
- Er wirft nur, was **beim Farmen dazugekommen** ist. Was du beim Einschalten schon dabei hattest, bleibt bei dir.
- Waffen, Rüstung und Essen wirft er nie.
- Ohne Trichter erkennt er selbst, wo die Mobs sind (die Gruppe um das nächste Monster). Ist ein Mob-Bereich gesetzt, nimmt er den.

## Kisten leuchten grün
Im Panel schaltet **„Kisten grün: AN/AUS“** die Kisten-Anzeige (übernommen aus dem Patri-Autofarmer).
- Ungeöffnete Kisten in der Umgebung bekommen einen **grünen Rahmen, auch durch Wände**.
  Höchstens 96, die nächsten zuerst. Kistenloren zählen mit.
- **„Kisten-Weite“** im Panel stellt ein, wie weit gesucht wird: 8, 16, 24 oder 32 Chunks (jeder Klick eins weiter, am Anfang 16).
  Weiter als die **Sichtweite** geht nicht, weil das Spiel weiter weg gar keine Blöcke lädt. Für mehr Weite also auch die
  Sichtweite hochstellen (Optionen → Grafik). Auf deinem Server zählt außerdem `view-distance` in `server.properties`.
- Kisten, die du **schon geöffnet** hast, verschwinden aus der Anzeige (wird gespeichert).
- **Lager von Spielern** (mehr als 4 Kisten beieinander oder Betten, Schilder, Türen, Fackeln … daneben) werden nicht gezeigt,
  außer in Bauwerken, die kein Spieler baut (Netherfestung, Bastion, Festung, Antike Stadt, Endsiedlung, Prüfungskammer).
- Funktioniert in jeder Welt: Oberwelt, Nether und End.
- Kisten, die rundum in Stein stecken, werden nicht gezeigt.
- Die Rahmen zeichnet **Baritone**. Ohne Baritone in Pumpcraft gibt es keine Rahmen.

## Burg-Vorschau (Blutfeste)
Im Panel setzt **„Burg hier setzen“** die Burg an deine Stelle und zeigt die Umrisse der geplanten Burg als **grüne Rahmen** in der Welt, auch durch Wände.
Es wird **nichts gebaut**, du kannst alles ablaufen und anschauen, ob die Burg dort hinpasst.
- **Wo du stehst, ist die Mitte der Eingangsmauer** (das Tor). Die Burg geht in die Richtung, **in die du schaust**
  (gerundet auf Norden, Osten, Süden oder Westen). Der Sockel fängt auf Höhe deiner Füße an.
- Gezeigt werden: der Sockel (122 x 122, 4 hoch), die vier Mauern, alle Türme, die Hallen, die Kathedrale mit
  Fassade und der große Mittelturm (124 hoch).
- Im **Chat** steht danach eine **Gelände-Prüfung**: wie hoch der Boden unter der Fläche ist, und wie viel davon
  Hügel (müssten weg), Löcher (müssten aufgefüllt werden), Wasser und Bäume sind.
- **„Burg-Linien: AN/AUS“** blendet die grünen Rahmen nur ein oder aus. Die Stelle der Burg bleibt dabei gespeichert.
- **„Burg neu setzen“** verschiebt die Burg an deine jetzige Stelle. Damit das nicht aus Versehen passiert, musst du
  innerhalb von 5 Sekunden **zweimal** klicken.
- Beim Verlassen der Welt verschwindet die Vorschau. Die Rahmen zeichnet **Baritone**, wie bei den Kisten.
- Geschlossen wird das Panel jetzt mit **Esc** (der Knopf „Schließen“ hat dem Burg-Knopf Platz gemacht).

### Merkt sich die Burg
- Die platzierte Burg wird gespeichert (in `config/tittowpanel.json`). Verlässt du den Server und joinst neu,
  ist die **Vorschau nach ein paar Sekunden von selbst wieder da**, an derselben Stelle. Im Chat steht „Burg-Vorschau wieder da“.
- Das gilt nur für denselben Server (bzw. dieselbe Einzelspieler-Welt) und dieselbe Dimension. Bist du im Nether,
  kommt die Vorschau wieder, sobald du zurück in der Oberwelt bist.
- War das **Ebnen** noch nicht fertig, heißt der Knopf **„Ebnen weiter“**. Ein Klick, und der Bot
  schaut sich die Fläche neu an und macht nur noch, was fehlt.
- Die Stelle bleibt gespeichert, bis du die Burg mit „Burg neu setzen“ woanders hinsetzt. Auch bei „Burg-Linien: AUS“.

### Burg-Platz ebnen
**„Platz ebnen“** macht die Sockel-Fläche (122 x 122) eben. Vorher muss die Burg gesetzt sein,
geebnet wird **nur innerhalb des äußeren grünen Rahmens**. Das Panel geht zu und **Baritone** legt los:
- **Freigraben:** alles ab deiner Fußhöhe kommt weg – Hügel, Bäume, Gras (bis 64 Blöcke hoch).
- **Löcher stopfen, gleichzeitig:** Löcher, Wasser und Lava unter der Fläche werden mit **Erde** aufgefüllt, aber **nur 1 Block tief** (die Schicht direkt unter deinen Füßen).
  Beides passiert **in einem Durchgang**: Wo Baritone gerade gräbt, stopft es die Löcher gleich mit. Am Ende schaut der
  Bot nochmal über die Fläche und macht, was noch fehlt (höchstens 3 Runden). Im Chat steht dann das Ergebnis.
  Dafür braucht Baritone **Erde im Inventar** (die vom Graben zählt mit).

**Hotbar beim Ebnen:**

| Slot | Was | Was der Bot damit macht |
|---|---|---|
| 1 | Schwert bzw. Glefe | bleibt liegen |
| 2 | Essen (Goldene Karotten) | isst, sobald der Hunger unter 18 fällt, bis du wieder satt bist |
| 3, 4, 5 | Schaufel, Spitzhacke, Axt | Baritone nimmt jeweils das passende Werkzeug |
| 6 | Erde | die Mod legt Erde von oben aus dem Inventar hierher nach. Baritone baut sich damit hoch (an hohe Bäume und Hügel) und stopft Löcher. Fehlt Erde, holt der Bot welche aus dem Funk-Terminal (unter 32) |
| 7 | Virtuelle Werkbank (Rechtsklick in die Luft öffnet die Werkbank, wird nicht aufgestellt) | bleibt liegen (fürs Ebnen nicht nötig) |
| 8 | Funk-Terminal | zum Erde holen und Sachen ablegen |
| 9 | (deins) | wird **nie** angefasst |

Baritone selbst darf beim Ebnen nichts im Inventar umräumen (Einstellung `allowInventory` aus), damit Slot 1 und 9 bleiben, wie sie sind.

- **Erde aus dem Funk-Terminal:** Hast du zu wenig Erde (unter 32), hält der Bot kurz an, nimmt das **Funk-Terminal aus
  Slot 8** der Hotbar, öffnet es (Rechtsklick), nimmt so viel Erde, wie ins Inventar passt (blättert auch weiter, wenn
  es einen „Weiter“-Knopf gibt), macht es zu und baut weiter. Liegt das Terminal woanders im Inventar, legt er es
  wieder auf Slot 8. Findet er keine Erde, steht das im Chat und er versucht es nach einer Minute nochmal.
- **Inventar voll:** Dann öffnet der Bot das Funk-Terminal und **legt die neu aufgesammelten Sachen hinein**
  (Shift-Klick, sonst anklicken und in einen leeren Platz legen). Danach gräbt er weiter.
  - Ausgeräumt wird **nur das obere Inventar**, die Hotbar (Slot 1 bis 9) bleibt immer, wie sie ist.
  - **Was du beim Start des Ebnens dabei hattest, bleibt im Inventar.** Das merkt er sich auch beim „weitermachen“
    nach dem Neu-Joinen.
  - 128 Erde behält er immer, fürs Löcherstopfen.
  - Geht nichts hinein (z. B. Lager voll), steht das im Chat und er versucht es nach einer Minute nochmal.
- Am Ende steht „fertig geebnet“ im Chat. Nochmal klicken (**„Ebnen: Stopp“**) hält alles an. Die Linien dürfen beim Ebnen auch aus sein.

## Dateien
- `src/client/java/com/tittow/panel/BotLogik.java` – Laufen, Schlagen, Essen
- `src/client/java/com/tittow/panel/TittowScreen.java` – das Fenster
- `src/client/java/com/tittow/panel/Bereich.java` – Fläche, Mob-Bereich und Einstellungen (gespeichert in `config/tittowpanel.json`)
- `src/client/java/com/tittow/panel/Kisten.java` – Kisten-Anzeige: Suchen und geöffnete Kisten merken
- `src/client/java/com/tittow/panel/BaritoneRahmen.java` – lässt Baritone die grünen Rahmen zeichnen
- `src/client/java/com/tittow/panel/Burgvorschau.java` – Burg-Vorschau: Umrisse der Blutfeste, Gelände-Prüfung und Platz ebnen
- `src/client/java/com/tittow/panel/FunkTerminal.java` – beim Ebnen: Erde aus dem Funk-Terminal (Slot 8) holen, volles Inventar hineinlegen
- `src/client/java/com/tittow/panel/Versorgung.java` – beim Ebnen: aus Slot 2 essen, Erde in Slot 6 nachlegen
