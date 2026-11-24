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
| 1 | Schwert bzw. Glefe | kommt **irgendein Tier oder Monster näher als 3 Blöcke** (alles: Monster, Tiere, auch zahme, die eigenen Pump-Monster und -Tiere wie das Nilpferd), hält der Bot kurz an, nimmt die Waffe und tötet es. **Nie Spieler, nie Dorfbewohner oder Händler, nie Rüstungsständer.** Danach arbeitet er weiter |
| 2 | Essen (Goldene Karotten) | isst, sobald der Hunger unter 18 fällt, bis du wieder satt bist |
| 3, 4, 5 | Schaufel, Spitzhacke, Axt | Baritone nimmt jeweils das passende Werkzeug. Die **Fähigkeit der Spitzhacke** (Rechtsklick) benutzt der Bot alle 20 Sekunden |
| 6 | Erde | die Mod legt Erde von oben aus dem Inventar hierher nach. Baritone baut sich damit hoch (an hohe Bäume und Hügel) und stopft Löcher. Fehlt Erde (unter 32), holt der Bot höchstens 5 Stacks aus dem Funk-Terminal und lässt dabei ein paar Plätze im Inventar frei |
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
  - Bis zu 5 Stacks Erde behält er, fürs Löcherstopfen. Mehr legt er zurück.
  - Geht nichts hinein (z. B. Lager voll), steht das im Chat und er versucht es nach einer Minute nochmal.
- Am Ende steht „fertig geebnet“ im Chat. Nochmal klicken (**„Ebnen: Stopp“**) hält alles an. Die Linien dürfen beim Ebnen auch aus sein.

### Material prüfen
Knopf **„Material prüfen“** (unten im Panel, links neben „Mauer bauen“). Das Panel geht zu, der Bot öffnet das Funk-Terminal
(Slot 8), blättert **alle Seiten** durch und zählt, was im Lager ist. Was du im Inventar hast, zählt mit.
Geprüft wird **nur die Mauer mit Türmen** (Außenmauer, 4 Ecktürme, die Mauertürme und das Tor, vom Sockel bis ganz oben,
ca. 41.000 Blöcke). Der Sockel ist fertig und wird nicht mehr geprüft.
Gezählt wird **nur das, was noch fehlt**: Der Bot schaut in der Welt nach, was schon steht,
und rechnet nur den Rest (also ab da, wo das Bauen aufgehört hat). Oben im Bericht steht, wie viele Blöcke noch fehlen
und wie viel Prozent fertig sind. Dafür muss die Burg gesetzt sein und du musst nah genug stehen, damit die ganze Burg-Fläche
geladen ist (sonst sagt er „Geh näher an die Burg“). Danach steht im Chat jeder Block einzeln:
- ✔ grün: davon ist genug da (z. B. „Erde: 14.200 da, noch 3.000 nötig“),
- ✘ rot: zu wenig, mit „fehlen …“,
- oben drüber: wie viele Sorten schon reichen und wie viele Blöcke zusammen noch fehlen.

Darunter steht, was **auch nach dem Herstellen und Brennen** noch fehlt, also welche Rohstoffe du noch besorgen musst
(z. B. fehlt polierter Andesit, aber genug Andesit ist da: dann ist alles gut). Steht dort „Mit Herstellen und Brennen reicht alles!“,
ist das Material komplett.

**Danach stellt der Bot gleich alles für diesen Bauteil her und brennt es** (siehe „Herstellen“ unten). Der Knopf heißt solange
**„Herstellen: Stopp“**. Am Ende steht „Fertig mit Herstellen für die Mauer mit Türmen.“ Alles liegt dann im Funk-Terminal.
Am besten bei den Strom-Öfen neben dem Tor starten.
Gezählt wird nur, was an der Mauer **noch fehlt** (steht schon etwas, zählt es nicht mehr).

Die Mengen kommen aus dem Bauplan der Blutfeste (Tiefenschiefer, Hauptturm aus Schwarzstein, Netherziegel statt Rot, Leuchtstein als Licht).
Zeigt das Lager nur einen Stapel und schreibt die echte Menge in die Beschreibung (z. B. „Menge: 1.600“), wird diese Zahl genommen.
Geht nicht, während der Bot ebnet.

**Kamera:** Beim Ebnen, Herstellen und Bauen schaut die Kamera in die Richtung, in die der Bot läuft und baut, und dreht
sich dabei weich mit (vorher blieb das Bild einfach stehen).

### Mauer bauen (Mauer mit Türmen)
Knopf **„Mauer bauen“** (unten im Panel, rechts neben „Material prüfen“). Am besten bei den Strom-Öfen neben dem Tor starten.
Gebaut werden die Außenmauer, die 4 Ecktürme, die Mauertürme und das Tor, von Höhe 4 bis Höhe 54 (ca. 41.000 Blöcke).
**Das Innere der Burg bleibt frei.**
1. **Vorbereiten:** Er schaut nach, was noch fehlt, stellt es her und brennt es. Fehlt danach noch Material, hält er an und sagt, was.
2. **Bauen, Höhe 4 bis 19 (Mauer mit Zinnen):** Höhe für Höhe einmal rundherum, in 28 Feldern. Er läuft oben auf der Mauer
   und steigt jede Runde einen Block höher. Die Blöcke holt er selbst aus dem Funk-Terminal.
   **Ab Höhe 20 (die 23 Türme):** Er baut **einen Turm komplett bis zur Spitze**, oben auf dem Rand steigt er mit.
   Ist der Turm fertig, **springt er runter auf die Mauer** (direkt neben dem Turm) und geht zum nächsten Turm.
   So baut er sich pro Turm nur einmal hoch. Vor dem Sprung wartet er, bis er **9 Herzen** hat, denn der tiefste Sprung
   (von den großen Ecktürmen) kostet ohne Federfall bis zu 7,5 Herzen. Hat er nach 2 Minuten nicht genug Leben oder findet
   er keinen Landeplatz, nimmt er als Notlösung eine Erd-Treppe (die baut er am Ende wieder ab).
   Zum Hochkommen benutzt er Erde (Slot 5). Alle 5 Minuten steht im Chat, wie viel Prozent fertig sind und wie lange es noch dauert.
3. **Nachholen:** Was er nicht erreicht hat, versucht er oben angekommen nochmal (bis zu 3 Durchgänge).
4. **Aufräumen:** Zum Schluss baut er seine Gerüst-Erde wieder ab und bessert Löcher nach.

**Gerüst:** Steht er außen an der Burg, geht er vor dem nächsten Mauerstück erst auf die Innenseite, damit die Erde möglichst
innen landet. Ganz verhindern lässt sich Erde außen nicht, weil Baritone selbst entscheidet. Der Bot merkt sich aber jeden
Erdblock, den er selbst setzt, auch außerhalb der Burg, und baut genau diese am Ende wieder ab. Natürliche Erde und Hügel bleiben.
Die Liste steht in `config/tittowpanel-geruest.txt` und übersteht einen Neustart.

**Schutz:** Normal isst er ganz wie immer (Goldene Karotten aus Slot 2).
- Bei **4 Herzen oder weniger** hört er kurz auf, baut sich mit Erde ein, isst dort und wartet. Ab **7 Herzen** baut er weiter.
- Sind es nach 3 Minuten immer noch 4 Herzen oder weniger, hält er an und sagt Bescheid.
- **Stirbst du**, hält er sofort an und schreibt in den Chat, wo das war. Hol deine Sachen und klick „Mauer weiter“.

**Funk-Terminal beim Bauen:** Er benutzt es jederzeit von dort, wo er gerade steht. Er holt Blöcke, sobald sie knapp werden.
Ist das Inventar voll, legt er alles hinein, was nicht gebraucht wird (auch beim Aufräumen), und baut sofort weiter.
Er behält die aktuelle Blocksorte, 5 Stapel Gerüst-Erde und deine eigenen Sachen vom Start. Nach jeder Höhe räumt er auch auf.
Geht trotzdem nichts mehr hinein, hält er an und sagt Bescheid.

- **Anhalten:** Numpad 8 und **„Mauer: Stopp“**. Weiter geht es mit **„Mauer weiter“**: Was schon steht, überspringt er.
- Verlässt du die Welt, hält er an. Danach einfach „Mauer weiter“ drücken.
- Beim Start schaltet er die Beton-Farm aus, damit nicht beides gleichzeitig klickt.
- Fehlen am Ende noch Blöcke, steht das im Chat und der Knopf heißt weiter „Mauer weiter“.

### Sockel bauen (Knopf entfernt – der Sockel ist fertig)
Den Knopf **„Sockel bauen“** gibt es nicht mehr, weil der Sockel steht. So hat der Bot den Sockel gebaut:
Erst mal wird **nur der Sockel** gebaut (4 hoch), und es wird **nur dafür** hergestellt und gebrannt. Der Rest der Burg kommt später.
1. **Vorbereiten:** Der Bot schaut in der Welt nach, was am Sockel noch fehlt, und stellt alles dafür her und brennt es
   (siehe „Herstellen“ unten).
2. **Prüfen:** Er zählt das Funk-Terminal durch. Fehlt noch etwas, hält er an und schreibt in den Chat, **was fehlt**.
   Dann das Material (oder die Rohstoffe dafür) besorgen und **„Sockel weiter bauen“** klicken.
3. **Bauen:** Schicht für Schicht von unten nach oben. Jede Schicht teilt er in **Felder von 16 × 16 Blöcken** und baut
   sie **von außen nach innen** (Ring für Ring, einmal herum). Außen steht schon die Mauer, so hat jeder neue Block meist
   schon einen Nachbarn, an den er ihn setzen kann. Und die Wege bleiben kurz, er läuft nicht quer über die ganze Fläche.
   In jedem Feld baut er **eine Blocksorte nach der anderen**, und das Feld bis zu 3 Mal, solange es etwas bringt.
   Bleibt in einer Schicht etwas übrig, geht er die Felder nochmal durch.
   Die Sorte holt er aus dem Funk-Terminal, **höchstens 5 Stapel auf einmal**, und legt sie in **Slot 6**. Baritone baut
   nur diese Sorte und startet erst, wenn der Block **wirklich in Slot 6** liegt.
   - Reste behält er fürs nächste Feld. Am Ende jeder Höhe legt er alles übrige Baumaterial **zurück ins Funk-Terminal**.
   - Baumaterial, das vom letzten Mal noch im Inventar liegt, legt er auch zurück. Ist das Inventar fast voll, legt er
     **erst ab und holt dann**. So meldet er nicht mehr fälschlich „alle“, nur weil kein Platz frei war.
   - Ist eine Sorte im Funk-Terminal **wirklich alle**, hält er nicht an, sondern baut mit den anderen Sorten weiter.
     Am Schluss sagt er, was gefehlt hat. Dann Material besorgen und **„Sockel weiter bauen“** klicken.
   (Alles Hergestellte und Gebrannte liegt vorher schon im Funk-Terminal, das legt „Herstellen“ dort ab.)
   - Baritone baut dabei **nichts ab** (damit nichts Fertiges kaputtgeht). **Nur Erde darf er abbauen**, auch wenn er
     obendrauf steht: So kommt Gerüst-Erde, die vom Hochbauen dort liegt, wo ein anderer Block hin soll, wieder weg,
     und er setzt den richtigen Block.
   - Baritone bekommt beim Bauen **mehr Zeit zum Wegrechnen**. Ein **blauer** Weg heißt: er rechnet noch. Ein **roter**
     Weg ist fertig. So springt er seltener zwischen zwei halben Wegen hin und her. Am Ende werden die alten Zeiten
     zurückgestellt.
   - **Zum Hochbauen nimmt er Erde.** Die liegt beim Bauen in **Slot 5**, die Axt kommt solange nach oben ins Inventar
     und am Ende (oder bei „Bauen: Stopp“) wieder zurück nach Slot 5. Erde holt er sich bei Bedarf aus dem Funk-Terminal.
   - Nach dem Abschnitt **baut er das Erd-Gerüst wieder ab** (nur Erde, die über dem Abschnitt herausschaut und wo laut
     Bauplan nichts hingehört). Dabei darf er **nur Erde** abbauen, nie den fertigen Boden. Erde unten im hohlen Sockel
     bleibt liegen. Danach schaut er nochmal nach: Fehlt doch noch etwas (z. B. ein Loch), bessert er nach.
   - **Hängt er 10 Sekunden** (setzt keinen Block und läuft nicht), **springt er, geht 3–4 Blöcke zur Seite** und setzt
     mit derselben Sorte neu an. Hängt er danach wieder, lässt er diese Sorte im Feld erst mal und nimmt die nächste.
     Ist er mit allen Sorten der Höhe durch, fängt er **wieder von vorne an** (inzwischen haben die Blöcke oft Nachbarn,
     an die er sie setzen kann). Bringt eine ganze Runde nichts mehr, macht er erst mit der nächsten Höhe weiter.
   - **Fortschritt:** Beim Losbauen und dann **alle 5 Minuten** steht im Chat, wie viel Prozent vom Sockel fertig sind und
     wie lange es noch dauert, z. B. „Sockel: 62 % geschafft – braucht noch 1 Stunden 20 Minuten“.
     Die Restzeit rechnet er aus dem bisherigen Tempo aus.
   - **Am Schluss holt er alles Übersprungene nach:** Oben angekommen, geht er die Höhen nochmal von unten durch und baut
     nur, was noch fehlt (bis zu 3 Durchgänge). Erst was dann immer noch nicht geht, sollst du selbst setzen.
   - Alle Tiere und Monster in 3 Blöcken (auch Pump-Tiere wie das Nilpferd, nie Spieler oder Dorfbewohner) tötet er, bei Hunger isst er.
4. Ist der Sockel fertig, steht **„Der Sockel ist fertig gebaut!“** im Chat und der Knopf heißt **„Sockel fertig ✔“**.
   Klickst du trotzdem darauf, schaut er nach, ob wirklich alles steht. Fehlt etwas (z. B. Löcher), bessert er es aus.
   Das merkt er sich auch nach dem Neu-Joinen.
- Nochmal klicken (**„Bauen: Stopp“**) hält an, **„Sockel weiter bauen“** macht da weiter, wo er aufgehört hat.
- **Er merkt sich, wo er war**, auch nach dem Neu-Joinen oder Neustart: Beim Weiterbauen schaut er in der Welt nach, was schon
  steht, und baut nur den Rest. Auch beim Herstellen macht er nur noch, was für den Rest fehlt. Steh dafür in der Nähe der Burg,
  damit die ganze Fläche geladen ist.

### Herstellen (Werkbank und Öfen) – passiert nach „Material prüfen“ und beim Bauen automatisch
1. Der Bot zählt das Funk-Terminal durch und schreibt einen **Plan** in den Chat: was er in der Werkbank macht,
   was in die Öfen kommt und welche Rohstoffe im Lager **fehlen** (die musst du selbst besorgen).
2. **Zuerst die Öfen füllen**, weil Brennen am längsten dauert: Er sucht die Kisten mit den Schildern **„reinlegen“** (oben)
   und **„rausnehmen“** (unten) im Umkreis von 64 Blöcken, läuft hin und verteilt alles, was schon gebrannt werden kann,
   gleichmäßig auf die „reinlegen“-Kisten (Bruchstein → Stein, Netherrack → Netherziegel, Sand → Glas).
3. **Dann die Werkbank** (Virtuelle Werkbank, Slot 7), während die Öfen brennen. Er holt die Zutaten aus dem Funk-Terminal,
   legt sie ins Feld und nimmt alles mit Shift-Klick heraus:
   - **Zuerst**, was danach noch in den Ofen muss: Bruchtiefenschiefer → polierter Tiefenschiefer → Tiefenschiefer-Ziegel.
     Die Ziegel für die rissigen Ziegel kommen **gleich danach in die Öfen**.
   - Dann der Rest: Tiefenschiefer-Fliesen, Andesit → polierter Andesit, Magmacreme → Magmablock, 4 Leuchtsteinstaub → 1 Leuchtstein.
   - Für die Mauer mit Türmen außerdem: 1 roter Farbstoff + 4 Sand + 4 Kies → 8 roter Trockenbeton,
     4 Amethystscherben + 1 Glas → 2 getöntes Glas. Das Glas brennt er vorher in den Öfen aus Sand.
   - **Roter Beton:** Der Bot macht **roten Trockenbeton**, **in roten Beton umwandeln machst du selbst**. Im Plan und am
     Ende steht, wie viel Trockenbeton das ist. Den roten Farbstoff nimmt er aus dem Lager. Ist keiner mehr da, macht er
     ihn aus Mohn (Rosenbüsche nimmt er nicht).
4. **Dann Gebranntes abholen** aus den „rausnehmen“-Kisten (er nimmt nur, was er gebrannt hat). Kommt 3 Minuten nichts, macht er ohne weiter.
5. **Zum Schluss**, was aus Gebranntem gemacht wird: 4 Netherziegel → 1 Netherziegel-Block.
6. Alles Fertige legt er ins **Funk-Terminal**. Was du beim Start im Inventar hattest, bleibt drin.
   Am Ende steht „Fertig mit Herstellen“ im Chat.

- Es wird nur so viel gemacht, wie für den Abschnitt fehlt (in der Werkbank kann es ein Stapel mehr werden).
- Beim Laufen zu den Öfen baut Baritone nichts ab und nichts hin.
- Er sucht sich vor den Öfen einen **freien Stehplatz**, von dem aus er die oberen und die unteren Kisten erreicht.
  Geht das nicht, sagt er es sofort im Chat.

## Beton-Farm (Numpad 9)
Für die automatische Beton-Farm: Der Bot macht **die ganze Zeit Linksklick und gleich danach Rechtsklick**.
1. Stell dich an die Farm und schau auf den Block.
2. **Numpad 9** drücken, oder im Panel auf **„Beton-Farm: AUS“** klicken. Dann fängt er sofort an.
3. **Linksklick:** Die Taste bleibt gedrückt, bis der Block weg ist (höchstens 6 Sekunden).
   **Gleich danach Rechtsklick.** Schaust du auf nichts, macht er nur Rechtsklick.
4. Es wird benutzt, was du in der Hand hast, wie beim echten Klicken. Spitzhacke in der Haupthand und Trockenbeton
   in der Zweithand geht auch.
5. **Ausschalten:** wieder **Numpad 9**, oder im Panel auf „Beton-Farm: AN“ klicken.

- Ist ein Menü offen (Inventar, Chat, Panel), macht er Pause.
- Ist das Werkzeug in der Hand fast kaputt (noch 10 Haltbarkeit), hört er von selbst auf.
- Die Taste lässt sich in den Steuerungs-Einstellungen unter „Tittow Panel“ ändern.

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
- `src/client/java/com/tittow/panel/Abwehr.java` – beim Ebnen, Herstellen und Bauen: alle Tiere und Monster in 3 Blöcken (außer Spieler und Dorfbewohner) mit der Waffe aus Slot 1 töten
