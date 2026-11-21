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
- **In Streifen, wie beim Rasenmähen:** Die Fläche wird in 16 Streifen geteilt (je 8 Blöcke breit, vom Tor bis hinten).
  Der Bot macht einen Streifen nach dem anderen fertig: abbauen, und Löcher und Wasser auf dem Weg gleich mit Erde zumachen.
  Erst danach kommt der Streifen daneben dran. So stopft er Wasser immer vom fertigen Streifen aus zu.
  Im Chat steht, bei welchem Streifen er ist (z. B. „Streifen 3 von 16“). Fertigen Boden und gestopfte Erde baut er
  nicht wieder ab, und er gräbt sich nicht nach unten.
- **Danach Löcher zumachen:** Findet der Bot im Streifen nichts mehr zum Abbauen, aber es sind noch Löcher da, schaltet er
  um und macht gezielt nur noch die Löcher zu (Meldung „nichts mehr abzubauen – jetzt werden die … Löcher zugemacht“).
  Erst wenn auch die zu sind, kommt der nächste Streifen. Bleibt beim Abbauen nach 3 Versuchen etwas stehen, macht er
  trotzdem erst die Löcher zu.
- Bei „Ebnen weiter“ (z. B. nach dem Neu-Joinen) macht er bei dem Streifen weiter, bei dem er aufgehört hat.
- **Aufpasser:** Der Bot schaut beim Ebnen selbst nach, ob er festhängt, und startet den Streifen dann neu (Meldung im Chat):
  - steckt er **über 20 Sekunden im Wasser**,
  - ist er **über 10 Sekunden mehr als 8 Blöcke von der Fläche weg**,
  - oder ist im Streifen **2 Minuten lang kein einziger Block weniger** geworden (solange er abbaut, gilt er nie als festgehangen).
  - Klappt ein Streifen nach 3 Versuchen nicht (auch nicht das Löcherzumachen), überspringt er ihn und macht mit dem nächsten weiter (Rest selbst machen).
  - Ist er **über 30 Blöcke** von der Fläche weg (Vorsicht, fremde Grundstücke mit Geschütztürmen), hält er an.
    Dann selbst zurückgehen und „Ebnen weiter“ klicken.
  - Baritone meidet außerdem Wasser, so gut es geht (läuft nicht mehr einfach hinein).
  Dafür braucht Baritone **Erde im Inventar** (die vom Graben zählt mit).

**Hotbar beim Ebnen:**

| Slot | Was | Was der Bot damit macht |
|---|---|---|
| 1 | Schwert bzw. Glefe | kommt ein **Gegner näher als 2 Blöcke**, hält der Bot kurz an, nimmt die Waffe und greift an (nur Monster, nie Spieler, Tiere oder Dorfbewohner). Danach arbeitet er weiter |
| 2 | Essen (Goldene Karotten) | isst, sobald der Hunger unter 18 fällt, bis du wieder satt bist |
| 3, 4, 5 | Schaufel, Spitzhacke, Axt | Baritone nimmt jeweils das passende Werkzeug. Die **Fähigkeit der Spitzhacke** (Rechtsklick) benutzt der Bot alle 20 Sekunden |
| 6 | Erde | die Mod legt Erde von oben aus dem Inventar hierher nach. Baritone baut sich damit hoch (an hohe Bäume und Hügel) und stopft Löcher. Fehlt Erde (unter 32), holt der Bot etwa 8 Stacks aus dem Funk-Terminal und lässt dabei ein paar Plätze im Inventar frei |
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
  - Bis zu 10 Stacks Erde behält er immer, fürs Löcherstopfen (mehr, als er auf einmal holt, damit er sie nicht gleich wieder reinlegt).
  - Geht nichts hinein (z. B. Lager voll), steht das im Chat und er versucht es nach einer Minute nochmal.
- Am Ende steht „fertig geebnet“ im Chat. Nochmal klicken (**„Ebnen: Stopp“**) hält alles an. Die Linien dürfen beim Ebnen auch aus sein.

### Material prüfen
Knopf **„Material prüfen (Funk-Terminal)“** (ganz unten im Panel). Das Panel geht zu, der Bot öffnet das Funk-Terminal
(Slot 8), blättert **alle Seiten** durch und zählt, was im Lager ist. Was du im Inventar hast, zählt mit.
Geprüft wird **nur der Sockel**. Danach steht im Chat jeder Block einzeln:
- ✔ grün: davon ist genug da (z. B. „Erde: 14.200 da, 13.924 nötig“),
- ✘ rot: zu wenig, mit „fehlen …“,
- oben drüber: wie viele Sorten schon reichen und wie viele Blöcke zusammen noch fehlen.

Darunter steht, was **auch nach dem Herstellen und Brennen** noch fehlt, also welche Rohstoffe du noch besorgen musst
(z. B. fehlt polierter Andesit, aber genug Andesit ist da: dann ist alles gut). Steht dort „Mit Herstellen und Brennen reicht alles!“,
kann „Sockel bauen“ los.

**Danach stellt der Bot gleich alles für den Sockel her und brennt es** (siehe „Herstellen“ unten). Der Knopf heißt solange
**„Herstellen: Stopp“**. Am Ende steht „Fertig mit Herstellen für den Sockel. Jetzt kannst du „Sockel bauen“ drücken.“
Dann muss „Sockel bauen“ nichts mehr herstellen und legt sofort los. Am besten bei den Strom-Öfen neben dem Tor starten.
Gezählt wird nur, was am Sockel **noch fehlt** (steht schon etwas, zählt es nicht mehr).

Die Mengen kommen aus dem Bauplan der Blutfeste (Tiefenschiefer, Hauptturm aus Schwarzstein, Netherziegel statt Rot, Leuchtstein als Licht).
Zeigt das Lager nur einen Stapel und schreibt die echte Menge in die Beschreibung (z. B. „Menge: 1.600“), wird diese Zahl genommen.
Geht nicht, während der Bot ebnet.

### Sockel bauen
Knopf **„Sockel bauen“** (neben „Material prüfen“). Am besten bei den Strom-Öfen neben dem Tor starten.
Erst mal wird **nur der Sockel** gebaut (4 hoch), und es wird **nur dafür** hergestellt und gebrannt. Der Rest der Burg kommt später.
1. **Vorbereiten:** Der Bot schaut in der Welt nach, was am Sockel noch fehlt, und stellt alles dafür her und brennt es
   (siehe „Herstellen“ unten).
2. **Prüfen:** Er zählt das Funk-Terminal durch. Fehlt noch etwas, hält er an und schreibt in den Chat, **was fehlt**.
   Dann das Material (oder die Rohstoffe dafür) besorgen und **„Sockel weiter bauen“** klicken.
3. **Bauen:** Schicht für Schicht von unten nach oben, in jeder Schicht **eine Blocksorte nach der anderen**.
   Die Sorte holt er **jedes Mal frisch aus dem Funk-Terminal** und legt sie in **Slot 6**. Baritone baut nur diese Sorte.
   Ist die Sorte fertig, legt er den **Rest zurück ins Funk-Terminal**, dann kommt die nächste.
   (Alles Hergestellte und Gebrannte liegt vorher schon im Funk-Terminal, das legt „Herstellen“ dort ab.)
   - Baritone baut dabei **nichts ab** (damit nichts Fertiges kaputtgeht) und nimmt keine eigenen Blöcke zum Hochbauen.
   - Kommt Baritone 2 Minuten nicht weiter, stößt er es neu an. Nach 3 Versuchen sagt er im Chat, wie viele Blöcke
     in welcher Höhe übrig sind (selbst setzen) und macht weiter.
   - Gegner in 2 Blöcken greift er an, bei Hunger isst er.
4. Ist der Sockel fertig, steht **„Der Sockel ist fertig gebaut!“** im Chat und der Knopf heißt **„Sockel fertig ✔“**.
   Das merkt er sich auch nach dem Neu-Joinen.
- Nochmal klicken (**„Bauen: Stopp“**) hält an, **„Sockel weiter bauen“** macht da weiter, wo er aufgehört hat.
- **Er merkt sich, wo er war**, auch nach dem Neu-Joinen oder Neustart: Beim Weiterbauen schaut er in der Welt nach, was schon
  steht, und baut nur den Rest. Auch beim Herstellen macht er nur noch, was für den Rest fehlt. Steh dafür in der Nähe der Burg,
  damit die ganze Fläche geladen ist.

### Herstellen (Werkbank und Öfen) – passiert nach „Material prüfen“ und beim Bauen automatisch
1. Der Bot zählt das Funk-Terminal durch und schreibt einen **Plan** in den Chat: was er in der Werkbank macht,
   was in die Öfen kommt und welche Rohstoffe im Lager **fehlen** (die musst du selbst besorgen).
2. **Werkbank** (Virtuelle Werkbank, Slot 7): holt die Zutaten aus dem Funk-Terminal, legt sie ins Feld und nimmt
   alles mit Shift-Klick heraus:
   - Bruchtiefenschiefer → polierter Tiefenschiefer → Tiefenschiefer-Ziegel → Tiefenschiefer-Fliesen
   - Andesit → polierter Andesit, Magmacreme → Magmablock, 4 Netherziegel → 1 Netherziegel-Block, 4 Leuchtsteinstaub → 1 Leuchtstein
3. **Öfen**: sucht die Kisten mit den Schildern **„reinlegen“** (oben) und **„rausnehmen“** (unten) im Umkreis von 64 Blöcken,
   läuft hin, verteilt das Material gleichmäßig auf die „reinlegen“-Kisten und holt das Gebrannte aus den „rausnehmen“-Kisten:
   - Bruchstein → Stein, Tiefenschiefer-Ziegel → rissige Tiefenschiefer-Ziegel, Netherrack → Netherziegel (danach in der Werkbank zum Block)
   - Aus den „rausnehmen“-Kisten nimmt er nur, was er gebrannt hat. Kommt 3 Minuten nichts, macht er ohne weiter.
4. Alles Fertige legt er ins **Funk-Terminal**. Was du beim Start im Inventar hattest, bleibt drin.
5. Am Ende steht „Fertig mit Herstellen“ im Chat, danach geht es mit dem Prüfen und Bauen weiter.

- Es wird nur so viel gemacht, wie für den Abschnitt fehlt (in der Werkbank kann es ein Stapel mehr werden).
- Beim Laufen zu den Öfen baut Baritone nichts ab und nichts hin.

## Dateien
- `src/client/java/com/tittow/panel/BotLogik.java` – Laufen, Schlagen, Essen
- `src/client/java/com/tittow/panel/TittowScreen.java` – das Fenster
- `src/client/java/com/tittow/panel/Bereich.java` – Fläche, Mob-Bereich und Einstellungen (gespeichert in `config/tittowpanel.json`)
- `src/client/java/com/tittow/panel/Kisten.java` – Kisten-Anzeige: Suchen und geöffnete Kisten merken
- `src/client/java/com/tittow/panel/BaritoneRahmen.java` – lässt Baritone die grünen Rahmen zeichnen
- `src/client/java/com/tittow/panel/Burgvorschau.java` – Burg-Vorschau: Umrisse der Blutfeste, Gelände-Prüfung und Platz ebnen
- `src/client/java/com/tittow/panel/FunkTerminal.java` – Funk-Terminal (Slot 8): Erde holen, volles Inventar hineinlegen, Lager durchzählen
- `src/client/java/com/tittow/panel/Material.java` – Material prüfen: Bericht „da / nötig / fehlt“ für Sockel und Burg
- `src/client/java/com/tittow/panel/Herstellen.java` – Herstellen: Plan, Virtuelle Werkbank, Strom-Öfen über die Schilder-Kisten
- `src/client/java/com/tittow/panel/BurgBau.java` – Burg bauen: je Abschnitt vorbereiten, prüfen, Schicht für Schicht bauen
- `src/client/java/com/tittow/panel/Bauplan.java` – liest den Bauplan der Blutfeste
- `src/main/resources/assets/tittowpanel/burg/bauplan.bin.gz` – der Bauplan (erzeugt mit `Burgen.java bauplandatei`)
- `src/main/resources/assets/tittowpanel/burg/material.txt` – Materialliste der Blutfeste (erzeugt mit `Desktop\Burgen\generator\Burgen.java materialdatei`)
- `src/client/java/com/tittow/panel/Versorgung.java` – beim Ebnen: aus Slot 2 essen, Erde in Slot 6 nachlegen
- `src/client/java/com/tittow/panel/Abwehr.java` – beim Ebnen: Gegner in 2 Blöcken mit der Waffe aus Slot 1 angreifen
