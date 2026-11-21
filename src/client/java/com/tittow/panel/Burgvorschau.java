package com.tittow.panel;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Burg-Vorschau: zeigt die Umrisse der "Blutfeste" (122 x 122 Blöcke, 124 hoch) als Rahmen in der Welt,
 * auch durch Wände (Baritone zeichnet sie, siehe BaritoneRahmen).
 * Wo du beim Einschalten stehst, ist die Mitte der Eingangsmauer (das Tor). Die Burg geht in die Richtung,
 * in die du schaust. Der Sockel beginnt auf Höhe deiner Füße.
 * Dazu "Platz ebnen": Baritone gräbt die Sockel-Fläche (der äußere grüne Rahmen) frei und stopft gleichzeitig Löcher
 * und Wasser mit Erde, nur 1 Block tief (die Schicht direkt unter deinen Füßen). Fehlt Erde oder ist das Inventar voll, hilft FunkTerminal.
 */
public final class Burgvorschau {
	/** Größe der ganzen Burg mit Sockel (Kante zu Kante). */
	public static final int BREITE = 122;
	public static final int HOEHE = 124;

	/** Tor-Mitte im Bauplan: x = 75, z = 130 (Mitte der Eingangsmauer). Die Burg liegt im Plan bei 14..135. */
	private static final int TOR_X = 75, TOR_Z = 130;

	/** Teile der Burg im Bauplan: {x0, z0, x1, z1, y0, y1}, y = 0 ist die Höhe deiner Füße. */
	private static final int[][] TEILE = {
		{14, 14, 135, 135, 0, 3},        // Sockel 122 x 122, 4 hoch
		{18, 18, 131, 20, 4, 18},        // Mauer hinten
		{18, 129, 131, 131, 4, 14},      // Eingangsmauer (niedriger)
		{18, 18, 20, 131, 4, 18},        // Mauer links
		{129, 18, 131, 131, 4, 18},      // Mauer rechts
		{14, 14, 24, 24, 4, 51},         // Eckturm
		{125, 14, 135, 24, 4, 51},       // Eckturm
		{14, 125, 24, 135, 4, 51},       // Eckturm
		{125, 125, 135, 135, 4, 51},     // Eckturm
		{60, 125, 68, 133, 4, 40},       // Torturm
		{82, 125, 90, 133, 4, 40},       // Torturm
		{69, 127, 81, 133, 4, 17},       // Torhaus
		{21, 35, 37, 123, 4, 24},        // lange Halle links
		{112, 35, 128, 123, 4, 24},      // lange Halle rechts
		{36, 21, 113, 35, 4, 24},        // lange Halle hinten
		{61, 36, 89, 50, 4, 43},         // runder Chor hinten
		{61, 50, 89, 98, 4, 59},         // Kirchenschiff
		{46, 63, 61, 81, 4, 38},         // Querschiff links
		{89, 63, 104, 81, 4, 38},        // Querschiff rechts
		{55, 98, 95, 104, 4, 48},        // Fassade
		{44, 92, 54, 102, 4, 70},        // Fassadenturm
		{96, 92, 106, 102, 4, 70},       // Fassadenturm
		{66, 78, 84, 96, 4, 123},        // großer Mittelturm
	};
	/** Kleine Mauertürme (7 breit) an den Mauern, Mitte bei diesen Stellen. */
	private static final int[] MAUERTUERME = {38, 56, 75, 94, 112};

	/** Wie tief Löcher unter dem Sockel höchstens aufgefüllt werden (Blöcke unter deinen Füßen). */
	private static final int FUELL_TIEFE = 1;
	/** Bis wie hoch über deinen Füßen nach Hügeln und Bäumen gesucht wird. */
	private static final int SUCH_HOEHE = 64;

	private boolean an;
	private List<BlockPos[]> boxen = List.of();
	private BlockPos tor;
	private Direction vorne;

	/** Platz ebnen: 0 = aus, 1 = Baritone gräbt frei und stopft Löcher. */
	private int phase;
	private int wartet;
	private final FunkTerminal terminal = new FunkTerminal();
	private final Versorgung versorgung = new Versorgung();
	private final Abwehr abwehr = new Abwehr();
	/** Nach einem Fehlschlag beim Funk-Terminal eine Weile nicht nochmal versuchen. */
	private int terminalPause;

	/** Hier wird die platzierte Burg gespeichert, damit sie nach dem Neu-Joinen wieder da ist. */
	private final Bereich einstellungen;
	/** Nach dem Joinen kurz warten, bis Welt und Baritone bereit sind. */
	private int wiederWarte = 60;

	public Burgvorschau(Bereich einstellungen) {
		this.einstellungen = einstellungen;
	}

	/** Ebnen war gestartet und ist noch nicht fertig (z. B. weil du den Server verlassen hast)? */
	public boolean ebnenOffen() {
		return einstellungen.burgEbnen;
	}

	/** Ergebnis der Gelände-Prüfung. oben/unten = Integer.MIN_VALUE, wenn nichts zu tun ist. */
	private record Gelaende(String text, int oben, int unten, int offen, int bloecke, int stopf) {
		/** Blöcke ab Fußhöhe, die noch weg müssen. */
		int abbau() {
			return bloecke - stopf;
		}
	}

	public boolean istAn() {
		return an;
	}

	public boolean ebnetGerade() {
		return phase != 0;
	}

	/** Sind die grünen Linien eingeschaltet? (Die Burg-Stelle bleibt auch bei AUS gespeichert.) */
	public boolean linienAn() {
		return !einstellungen.burgLinienAus;
	}

	/** Grüne Linien ein- oder ausblenden. Die Stelle der Burg bleibt dabei gespeichert. */
	public String linienUmschalten() {
		einstellungen.burgLinienAus = !einstellungen.burgLinienAus;
		einstellungen.speichern();
		rahmenAnzeigen();
		if (!an) {
			return linienAn() ? "Linien AN (sobald eine Burg gesetzt ist)." : "Linien AUS.";
		}
		return linienAn() ? "Linien AN." : "Linien AUS – die Burg-Stelle bleibt gespeichert.";
	}

	/** Zeigt die Burg-Rahmen, wenn eine Burg gesetzt ist und die Linien an sind, sonst keine. */
	private void rahmenAnzeigen() {
		BaritoneRahmen.zeigen("burg", an && linienAn() ? boxen : List.of());
	}

	/** Bis wann ein zweiter Klick auf "Burg neu setzen" das Verschieben bestätigt. */
	private long bestaetigenBis;

	/**
	 * Burg an deiner Stelle und Blickrichtung setzen (Tor-Mitte = du). Ist schon eine gesetzt, muss man zum
	 * Verschieben zweimal klicken, damit sie nicht aus Versehen wegspringt. Gibt eine Meldung zurück.
	 */
	public String setzen(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			return "";
		}
		if (phase != 0) {
			return "Erst das Ebnen stoppen.";
		}
		if (!BaritoneRahmen.vorhanden()) {
			return "Ohne Baritone kann die Vorschau keine Rahmen zeichnen.";
		}
		if (an && System.currentTimeMillis() > bestaetigenBis) {
			bestaetigenBis = System.currentTimeMillis() + 5000;
			return "Burg wirklich hierher verschieben? Dann innerhalb von 5 Sekunden nochmal klicken.";
		}
		bestaetigenBis = 0;
		vorne = p.getDirection(); // Blickrichtung, auf Norden/Osten/Süden/Westen gerundet
		tor = p.blockPosition();
		boxen = rahmenBerechnen(tor, vorne);
		an = true;
		einstellungen.burgLinienAus = false; // neu gesetzt: Linien zeigen
		rahmenAnzeigen();
		einstellungen.burgTor = new int[] {tor.getX(), tor.getY(), tor.getZ()};
		einstellungen.burgRichtung = vorne.name();
		einstellungen.burgWelt = weltName(mc);
		einstellungen.burgEbnen = false;
		einstellungen.speichern();
		Gelaende g = gelaendePruefen(mc);
		p.sendOverlayMessage(Component.literal("Burg-Vorschau: AN, Tor hier, Burg Richtung " + richtung(vorne)));
		chat(p, "Tor bei " + tor.getX() + " " + tor.getY() + " " + tor.getZ() + ", Burg geht nach " + richtung(vorne) + ", "
				+ BREITE + " x " + BREITE + " Blöcke, Spitze bei Y " + (tor.getY() + HOEHE - 1) + ".");
		chat(p, g.text());
		return "Burg Richtung " + richtung(vorne) + " – Gelände-Prüfung steht im Chat.";
	}

	/**
	 * Rahmen weg (auch beim Verlassen der Welt). Ein laufendes Ebnen wird gestoppt. Gespeichert bleibt alles:
	 * nach dem Neu-Joinen kommt die Vorschau wieder, und das Ebnen kann weitermachen.
	 */
	public void aus() {
		if (phase != 0) {
			BaritoneRahmen.befehl("stop");
			terminal.abbrechen();
			versorgung.abbrechen(Minecraft.getInstance());
			abwehr.abbrechen();
			phase = 0;
		}
		an = false;
		boxen = List.of();
		rahmenAnzeigen();
		wiederWarte = 60;
	}

	/** Nach dem Joinen: war hier eine Burg platziert, kommt die Vorschau von selbst wieder. */
	private void wiederherstellen(Minecraft mc) {
		int[] t = einstellungen.burgTor;
		if (t == null || t.length < 3 || einstellungen.burgRichtung == null || !BaritoneRahmen.vorhanden()) {
			return;
		}
		if (wiederWarte > 0) {
			wiederWarte--;
			return;
		}
		if (!weltName(mc).equals(einstellungen.burgWelt)) {
			return; // andere Welt oder Dimension: erst, wenn du wieder dort bist
		}
		try {
			vorne = Direction.valueOf(einstellungen.burgRichtung);
		} catch (IllegalArgumentException e) {
			return;
		}
		tor = new BlockPos(t[0], t[1], t[2]);
		boxen = rahmenBerechnen(tor, vorne);
		an = true;
		rahmenAnzeigen();
		chat(mc.player, "Burg-Vorschau wieder da: Tor bei " + tor.getX() + " " + tor.getY() + " " + tor.getZ()
				+ ", Burg geht nach " + richtung(vorne) + ".");
		if (einstellungen.burgEbnen) {
			chat(mc.player, "Das Ebnen war noch nicht fertig. Numpad 8 → „Ebnen weiter“, "
					+ "dann macht der Bot da weiter, wo er aufgehört hat.");
		}
	}

	/** Server-Adresse (bzw. Name der Einzelspieler-Welt) und Dimension, damit die Burg nur dort wieder auftaucht. */
	private static String weltName(Minecraft mc) {
		String ort;
		if (mc.getCurrentServer() != null) {
			ort = mc.getCurrentServer().ip;
		} else if (mc.getSingleplayerServer() != null) {
			ort = "einzel:" + mc.getSingleplayerServer().getWorldData().getLevelName();
		} else {
			ort = "?";
		}
		return ort + " " + mc.level.dimension();
	}

	private void ebnenMerken(boolean offen) {
		if (einstellungen.burgEbnen != offen) {
			einstellungen.burgEbnen = offen;
			einstellungen.speichern();
		}
	}

	// ---- Platz ebnen -------------------------------------------------------------

	/** Breite eines Streifens (Blöcke). Die Fläche wird Streifen für Streifen geebnet, wie beim Rasenmähen. */
	private static final int STREIFEN_BREITE = 8;
	private static final int STREIFEN = (BREITE + STREIFEN_BREITE - 1) / STREIFEN_BREITE;

	/** Wie oft der aktuelle Streifen schon gestartet wurde. */
	private int versuch;
	/** Im aktuellen Streifen ist nichts mehr abzubauen: jetzt nur noch Löcher zumachen. */
	private boolean stopfen;

	/**
	 * Startet oder stoppt das Ebnen der Sockel-Fläche. Sie wird in Streifen (8 breit, vom Tor bis nach hinten)
	 * abgearbeitet, einer nach dem anderen: Im Streifen baut Baritone alles ab Fußhöhe ab und macht Löcher und
	 * Wasser auf dem Weg in der Schicht unter deinen Füßen gleich mit Erde zu.
	 */
	public String ebnenUmschalten(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (phase != 0) {
			BaritoneRahmen.befehl("stop");
			terminal.abbrechen();
			versorgung.abbrechen(Minecraft.getInstance());
			abwehr.abbrechen();
			phase = 0;
			ebnenMerken(false);
			return "Ebnen gestoppt.";
		}
		if (p == null || mc.level == null) {
			return "";
		}
		if (!an) {
			return "Erst „Burg hier setzen“ – geebnet wird genau die Sockel-Fläche.";
		}
		if (!BaritoneRahmen.vorhanden()) {
			return "Ohne Baritone geht das Ebnen nicht.";
		}
		boolean weiter = einstellungen.burgEbnen;
		if (!weiter || einstellungen.burgStartInventar == null) {
			// neuer Start (nicht "weitermachen"): merken, was jetzt im Inventar ist – das bleibt immer drin
			einstellungen.burgStartInventar = FunkTerminal.inventar(p);
		}
		if (!weiter) {
			einstellungen.burgStreifen = 0;
		}
		einstellungen.speichern();
		// Baritone darf nichts im Inventar umräumen (sonst landen Sachen auf Slot 1 und 9). Erde legt Versorgung in Slot 6 nach.
		BaritoneRahmen.einstellung("allowInventory", false);
		// Wasser meiden, fertigen Boden (auch frisch gestopfte Erde) nicht wieder abbauen, nicht nach unten graben
		BaritoneRahmen.einstellung("walkOnWaterOnePenalty", 20.0);
		BaritoneRahmen.einstellung("okIfWater", true);
		BaritoneRahmen.einstellung("breakCorrectBlockPenaltyMultiplier", 1000.0);
		BaritoneRahmen.einstellung("allowDownward", false);
		BaritoneRahmen.einstellung("buildInLayers", false); // ein Streifen ist schmal genug, keine Schichten nötig
		Gelaende g = gelaendePruefen(mc);
		chat(p, g.text());
		if (g.offen() == 0) {
			ebnenMerken(false);
			return "Die Fläche ist schon eben, nichts zu tun.";
		}
		phase = 1;
		ebnenMerken(true);
		chat(p, "Die Fläche wird in " + STREIFEN + " Streifen (je " + STREIFEN_BREITE + " breit, vom Tor bis hinten) geebnet, "
				+ "einer nach dem anderen. Erde holt der Bot bei Bedarf aus dem Funk-Terminal in Slot 8.");
		if (!naechsterStreifen(mc, false)) {
			return "Baritone hat den Bauplan nicht angenommen.";
		}
		return "Ebnen läuft, Streifen " + (einstellungen.burgStreifen + 1) + " von " + STREIFEN + ".";
	}

	/** Bauplan-Spalten (x im Plan) eines Streifens. */
	private static int streifenX0(int s) {
		return 14 + s * STREIFEN_BREITE;
	}

	private static int streifenX1(int s) {
		return Math.min(135, streifenX0(s) + STREIFEN_BREITE - 1);
	}

	/**
	 * Startet den aktuellen Streifen (gleich=true: denselben nochmal) oder sucht den nächsten, in dem noch etwas
	 * zu tun ist. Gibt false zurück, wenn Baritone den Bauplan nicht annimmt. Sind alle fertig, ist Schluss.
	 */
	private boolean naechsterStreifen(Minecraft mc, boolean gleich) {
		LocalPlayer p = mc.player;
		if (!gleich) {
			versuch = 0;
			stopfen = false;
		}
		while (einstellungen.burgStreifen < STREIFEN) {
			int s = einstellungen.burgStreifen;
			Gelaende g = gelaendePruefen(mc, streifenX0(s), streifenX1(s));
			if (!stopfen && g.abbau() == 0 && g.stopf() > 0) {
				// nichts mehr abzubauen, nur noch Löcher: jetzt gezielt zumachen
				stopfen = true;
				versuch = 0;
				chat(p, "Streifen " + (s + 1) + ": nichts mehr abzubauen – jetzt werden die " + g.stopf() + " Löcher zugemacht.");
			}
			if ((stopfen ? g.stopf() : g.offen()) > 0) {
				if (!streifenBauen(s, g)) {
					anhalten(mc, "Baritone hat den Bauplan nicht angenommen.");
					return false;
				}
				versuch++;
				letzterRest = g.bloecke();
				stillstand = 0;
				aufpassTicks = 0;
				wasserTicks = 0;
				rausTicks = 0;
				if (versuch == 1 && !stopfen) {
					chat(p, "Streifen " + (s + 1) + " von " + STREIFEN + " (" + g.offen() + " Stellen zu tun).");
				}
				return true;
			}
			einstellungen.burgStreifen++; // nichts zu tun: gleich weiter
			einstellungen.speichern();
			versuch = 0;
			stopfen = false;
		}
		// alle Streifen durch
		Gelaende g = gelaendePruefen(mc);
		phase = 0;
		ebnenMerken(false);
		einstellungen.burgStreifen = 0;
		einstellungen.speichern();
		chat(p, "Fertig: alle Streifen durch. " + g.text());
		p.sendOverlayMessage(Component.literal("Burg-Platz: fertig geebnet"));
		return true;
	}

	/**
	 * Gibt Baritone den Bauplan für einen Streifen: von der Schicht unter deinen Füßen bis zum höchsten Hindernis.
	 * Beim Stopfen nur die Schicht unter den Füßen (dann sucht Baritone gezielt die Löcher und macht sie zu).
	 */
	private boolean streifenBauen(int s, Gelaende g) {
		int fuss = tor.getY();
		int oben = stopfen ? fuss - 1 : Math.max(g.oben(), fuss); // mindestens die Fußhöhe (z. B. Gras), auch wenn nur Löcher da sind
		int[] a = welt(tor, vorne, streifenX0(s), 14);
		int[] b = welt(tor, vorne, streifenX1(s), 135);
		BlockPos ecke = new BlockPos(Math.min(a[0], b[0]), fuss - 1, Math.min(a[1], b[1]));
		int breitX = Math.abs(a[0] - b[0]) + 1, laengeZ = Math.abs(a[1] - b[1]) + 1;
		wartet = 40;
		terminalPause = 0;
		return BaritoneRahmen.ebnenBauen(ecke, breitX, oben - fuss + 2, laengeZ);
	}

	// ---- Aufpasser: hängt der Bot fest? -------------------------------------------

	private int aufpassTicks, letzterRest = Integer.MAX_VALUE, stillstand, wasserTicks, rausTicks;

	/**
	 * Läuft, solange Baritone arbeitet: steckt der Bot im Wasser fest, läuft er von der Fläche weg (fremde
	 * Grundstücke!) oder kommt er im Streifen eine Minute lang nicht voran, wird der Streifen neu gestartet,
	 * nach 3 Versuchen übersprungen.
	 */
	private void aufpassen(Minecraft mc) {
		LocalPlayer p = mc.player;
		// 1. im Wasser hängen geblieben
		if (p.isInWater()) {
			if (++wasserTicks > 400) { // 20 Sekunden
				neuStarten(mc, "steckt im Wasser fest");
				return;
			}
		} else {
			wasserTicks = 0;
		}
		// 2. von der Fläche weggelaufen
		int[] a = welt(tor, vorne, 14, 14);
		int[] b = welt(tor, vorne, 135, 135);
		int x = p.getBlockX(), z = p.getBlockZ();
		int raus = Math.max(Math.max(Math.min(a[0], b[0]) - x, x - Math.max(a[0], b[0])),
				Math.max(Math.min(a[1], b[1]) - z, z - Math.max(a[1], b[1])));
		if (raus > 30) {
			anhalten(mc, "Der Bot ist über 30 Blöcke von der Fläche weg (Vorsicht, fremde Grundstücke) – Ebnen angehalten. "
					+ "Geh zurück auf die Fläche und klick „Ebnen weiter“.");
			return;
		}
		if (raus > 8) {
			if (++rausTicks > 200) { // 10 Sekunden
				neuStarten(mc, "ist von der Fläche runtergelaufen");
				return;
			}
		} else {
			rausTicks = 0;
		}
		// 3. baut er gerade einen Block ab, arbeitet er – das zählt nie als Festhängen
		if (mc.gameMode != null && mc.gameMode.isDestroying()) {
			stillstand = 0;
		}
		// 4. alle 30 Sekunden: werden die Blöcke im Streifen weniger? Erst nach 2 Minuten ohne einen einzigen
		//    Block weniger (und ohne Abbauen) gilt er als festgehangen.
		if (++aufpassTicks >= 600) {
			aufpassTicks = 0;
			int s = einstellungen.burgStreifen;
			Gelaende g = gelaendePruefen(mc, streifenX0(s), streifenX1(s));
			if (!stopfen && g.abbau() == 0 && g.stopf() > 0) {
				// alles abgebaut, Baritone sucht nur noch herum: gleich auf Löcherstopfen umschalten
				BaritoneRahmen.befehl("stop");
				naechsterStreifen(mc, true);
				return;
			}
			int rest = g.bloecke();
			if (rest < letzterRest) {
				letzterRest = rest;
				stillstand = 0;
			} else if (++stillstand >= 4) {
				neuStarten(mc, "kommt seit 2 Minuten nicht weiter");
			}
		}
	}

	/** Streifen neu anstoßen; nach 3 Versuchen den Streifen überspringen und mit dem nächsten weitermachen. */
	private void neuStarten(Minecraft mc, String grund) {
		BaritoneRahmen.befehl("stop");
		if (versuch >= 3) {
			streifenAufgeben(mc, "Der Bot " + grund);
			return;
		}
		chat(mc.player, "Der Bot " + grund + " – Streifen " + (einstellungen.burgStreifen + 1) + " wird neu angestoßen.");
		naechsterStreifen(mc, true);
	}

	/**
	 * 3 Versuche ohne Erfolg: Ist beim Abbauen noch etwas übrig, werden trotzdem erst die Löcher im Streifen
	 * zugemacht. Klappt auch das nicht, wird der Streifen übersprungen.
	 */
	private void streifenAufgeben(Minecraft mc, String grund) {
		int s = einstellungen.burgStreifen;
		Gelaende g = gelaendePruefen(mc, streifenX0(s), streifenX1(s));
		if (!stopfen && g.stopf() > 0) {
			chat(mc.player, grund + " – in Streifen " + (s + 1) + " bleibt etwas stehen (bitte selbst abbauen). "
					+ "Jetzt werden erst die " + g.stopf() + " Löcher zugemacht.");
			stopfen = true;
			versuch = 0;
			naechsterStreifen(mc, true);
			return;
		}
		chat(mc.player, grund + " – Streifen " + (s + 1) + " wird übersprungen (Rest bitte selbst machen), weiter mit dem nächsten.");
		einstellungen.burgStreifen++;
		einstellungen.speichern();
		naechsterStreifen(mc, false);
	}

	/** Ebnen anhalten, aber gemerkt lassen (dann heißt der Knopf "Ebnen weiter" und es geht beim Streifen weiter). */
	private void anhalten(Minecraft mc, String text) {
		BaritoneRahmen.befehl("stop");
		terminal.abbrechen();
		versorgung.abbrechen(mc);
		abwehr.abbrechen();
		phase = 0;
		chat(mc.player, text);
	}

	/** Jeden Tick: essen, Erde nachlegen, Funk-Terminal, aufpassen, und wenn ein Streifen fertig ist, der nächste. */
	public void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		if (!an) {
			wiederherstellen(mc);
		}
		if (phase == 0) {
			return;
		}
		if (terminal.laeuft()) {
			String meldung = terminal.tick(mc);
			if (meldung != null) {
				chat(mc.player, meldung);
				if (!meldung.startsWith("OK")) {
					terminalPause = 20 * 60; // eine Minute warten, dann nochmal probieren
				}
			}
			return;
		}
		if (abwehr.tick(mc, mc.player)) { // Gegner näher als 2 Blöcke: erst den angreifen
			return;
		}
		if (versorgung.essen(mc, mc.player)) { // Hunger: erst Goldene Karotten aus Slot 2 essen
			return;
		}
		// Erde in Slot 6: zum Hochbauen (an hohe Bäume und Hügel) und zum Löcherstopfen
		versorgung.erdeNachlegen(mc, mc.player);
		if (terminalPause > 0) {
			terminalPause--;
		}
		if (wartet > 0) { // Baritone braucht einen Moment, bis es loslegt
			wartet--;
			return;
		}
		if (terminalPause == 0 && FunkTerminal.inventarVoll(mc.player) && BaritoneRahmen.bautGerade()) {
			chat(mc.player, "Inventar voll: lege neue Sachen ins Funk-Terminal (was du beim Start dabei hattest, bleibt).");
			terminal.ablegen(mc, einstellungen.burgStartInventar);
			return;
		}
		if (terminalPause == 0 && FunkTerminal.erde(mc.player) < 32 && BaritoneRahmen.bautGerade()) {
			chat(mc.player, "Erde wird knapp: hole Erde aus dem Funk-Terminal (Slot 8).");
			terminal.erdeHolen(mc);
			return;
		}
		if (BaritoneRahmen.bautGerade()) {
			versorgung.spitzhackeFaehigkeit(mc, mc.player); // alle 20 Sekunden Rechtsklick mit der Spitzhacke
			aufpassen(mc);
			return;
		}
		// Baritone hat aufgehört: nachschauen, was im Streifen noch fehlt. Nichts mehr abzubauen, aber Löcher da:
		// dann werden die jetzt zugemacht. Alles fertig: der nächste Streifen.
		if (versuch < 3) {
			naechsterStreifen(mc, true);
			return;
		}
		int s = einstellungen.burgStreifen;
		Gelaende g = gelaendePruefen(mc, streifenX0(s), streifenX1(s));
		if ((stopfen ? g.stopf() : g.offen()) > 0 || (!stopfen && g.stopf() > 0)) {
			streifenAufgeben(mc, "Baritone wird hier nicht fertig");
			return;
		}
		einstellungen.burgStreifen++;
		einstellungen.speichern();
		naechsterStreifen(mc, false);
	}

	// ---- Umrechnen vom Bauplan in die Welt ------------------------------------

	/** Weltposition eines Bauplan-Punkts: Tor-Mitte = deine Stelle, "nach innen" = deine Blickrichtung. */
	private static int[] welt(BlockPos tor, Direction vorne, int x, int z) {
		Direction rechts = vorne.getClockWise();
		int seitlich = x - TOR_X;     // + = rechts von dir
		int vor = TOR_Z - z;          // + = vor dir (in die Burg hinein)
		return new int[] {
			tor.getX() + seitlich * rechts.getStepX() + vor * vorne.getStepX(),
			tor.getZ() + seitlich * rechts.getStepZ() + vor * vorne.getStepZ(),
		};
	}

	private static List<BlockPos[]> rahmenBerechnen(BlockPos tor, Direction vorne) {
		List<int[]> teile = new ArrayList<>(List.of(TEILE));
		for (int m : MAUERTUERME) {
			teile.add(new int[] {m - 3, 16, m + 3, 22, 4, 39});    // hinten
			teile.add(new int[] {16, m - 3, 22, m + 3, 4, 39});    // links
			teile.add(new int[] {127, m - 3, 133, m + 3, 4, 39});  // rechts
			if (m == 38 || m == 112) {
				teile.add(new int[] {m - 3, 127, m + 3, 133, 4, 39}); // vorne (die mittleren ersetzt das Tor)
			}
		}
		List<BlockPos[]> ergebnis = new ArrayList<>();
		for (int[] t : teile) {
			int[] a = welt(tor, vorne, t[0], t[1]);
			int[] b = welt(tor, vorne, t[2], t[3]);
			ergebnis.add(new BlockPos[] {
				new BlockPos(Math.min(a[0], b[0]), tor.getY() + t[4], Math.min(a[1], b[1])),
				new BlockPos(Math.max(a[0], b[0]), tor.getY() + t[5], Math.max(a[1], b[1])),
			});
		}
		return ergebnis;
	}

	// ---- Gelände prüfen --------------------------------------------------------

	/**
	 * Schaut sich den Boden unter der ganzen Sockel-Fläche an: wie hoch er ist, wo Hügel im Weg sind,
	 * wo Löcher oder Wasser unter dem Sockel wären und wo Bäume stehen. Merkt sich außerdem, bis wohin
	 * freigegraben (oben) und ab wo aufgefüllt werden müsste (unten).
	 */
	private Gelaende gelaendePruefen(Minecraft mc) {
		return gelaendePruefen(mc, 14, 135);
	}

	/** Wie oben, aber nur für die Bauplan-Spalten x0 bis x1 (ein Streifen). */
	private Gelaende gelaendePruefen(Minecraft mc, int x0, int x1) {
		int fuss = tor.getY();
		int minBoden = Integer.MAX_VALUE, maxBoden = Integer.MIN_VALUE;
		int huegel = 0, loecher = 0, wasser = 0, baeume = 0, unbekannt = 0;
		int offen = 0; // Spalten, in denen noch etwas zu tun ist
		int stopf = 0; // Löcher in der Schicht unter den Füßen, die noch zugemacht werden müssen
		int bloecke = 0; // einzelne Blöcke, die noch weg müssen oder hin müssen (zum Prüfen, ob der Bot vorankommt)
		int oben = Integer.MIN_VALUE, unten = Integer.MAX_VALUE;
		for (int x = x0; x <= x1; x++) {
			for (int z = 14; z <= 135; z++) {
				int[] w = welt(tor, vorne, x, z);
				if (!mc.level.hasChunk(w[0] >> 4, w[1] >> 4)) {
					unbekannt++;
					continue;
				}
				boolean baum = false, nass = false, arbeit = false;
				BlockState drunter = mc.level.getBlockState(new BlockPos(w[0], fuss - 1, w[1]));
				if (drunter.isAir() || drunter.liquid() || drunter.canBeReplaced()) {
					arbeit = true; // hier muss Erde hin
					bloecke++;
					stopf++;
				}
				for (int y = fuss; y <= fuss + SUCH_HOEHE; y++) { // jeden Block zählen, der noch weg muss
					BlockState s = mc.level.getBlockState(new BlockPos(w[0], y, w[1]));
					if (!s.isAir() && !s.liquid()) {
						bloecke++;
					}
				}
				int boden = Integer.MIN_VALUE, grund = Integer.MIN_VALUE;
				for (int y = fuss + SUCH_HOEHE; y >= fuss - FUELL_TIEFE - 1; y--) {
					BlockState s = mc.level.getBlockState(new BlockPos(w[0], y, w[1]));
					if (s.isAir()) {
						continue;
					}
					if (!s.liquid() && y >= fuss) {
						oben = Math.max(oben, y); // alles ab Fußhöhe muss weg (Hügel, Bäume, Gras)
						arbeit = true;
					}
					if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) {
						baum = true;
						continue;
					}
					if (s.liquid()) {
						if (boden == Integer.MIN_VALUE) {
							boden = y;
							nass = true;
						}
						continue; // unter dem Wasser weiter nach dem Grund suchen
					}
					if (s.isSolid()) {
						if (boden == Integer.MIN_VALUE) {
							boden = y;
						}
						grund = y;
						break;
					}
				}
				if (arbeit) {
					offen++;
				}
				if (baum) {
					baeume++;
				}
				if (boden == Integer.MIN_VALUE) {
					loecher++;
					unten = Math.min(unten, fuss - FUELL_TIEFE);
					continue;
				}
				minBoden = Math.min(minBoden, boden);
				maxBoden = Math.max(maxBoden, boden);
				if (nass) {
					wasser++;
				} else if (boden >= fuss) {
					huegel++;
				} else if (boden < fuss - 1) {
					loecher++;
				}
				if (nass || boden < fuss - 1) {
					int ab = grund == Integer.MIN_VALUE ? fuss - FUELL_TIEFE : grund + 1;
					unten = Math.min(unten, Math.max(ab, fuss - FUELL_TIEFE));
				}
			}
		}
		int alle = (x1 - x0 + 1) * BREITE;
		StringBuilder t = new StringBuilder();
		if (minBoden != Integer.MAX_VALUE) {
			t.append("Boden Y ").append(minBoden).append(" bis ").append(maxBoden).append(" (du Y ").append(fuss - 1).append("). ");
		}
		t.append("Hügel ").append(prozent(huegel, alle))
				.append(", Löcher ").append(prozent(loecher, alle))
				.append(", Wasser ").append(prozent(wasser, alle))
				.append(", Bäume ").append(prozent(baeume, alle));
		if (unbekannt > 0) {
			t.append(", ").append(prozent(unbekannt, alle)).append(" nicht geladen (näher rangehen)");
		}
		return new Gelaende(t.toString(), oben, unten == Integer.MAX_VALUE ? Integer.MIN_VALUE : unten, offen, bloecke, stopf);
	}

	private static void chat(LocalPlayer p, String text) {
		p.sendSystemMessage(Component.literal("§d[Burg]§r " + text));
	}

	private static String prozent(int teil, int alle) {
		return Math.round(100.0 * teil / alle) + "%";
	}

	private static String richtung(Direction d) {
		return switch (d) {
			case NORTH -> "Norden";
			case SOUTH -> "Süden";
			case EAST -> "Osten";
			default -> "Westen";
		};
	}
}
