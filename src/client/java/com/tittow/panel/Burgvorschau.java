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
	/** Wie oft der Bauplan schon gestartet wurde (nach dem Ende wird nachgeschaut und höchstens 3-mal neu gestartet). */
	private int durchgang;
	private final FunkTerminal terminal = new FunkTerminal();
	private final Versorgung versorgung = new Versorgung();
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
	private record Gelaende(String text, int oben, int unten) {
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

	/**
	 * Startet oder stoppt das Ebnen der Sockel-Fläche, alles in einem Durchgang: Baritone gräbt alles ab Fußhöhe
	 * weg (Hügel, Bäume, Gras) und stopft dabei gleich die Löcher und das Wasser in der Schicht unter deinen Füßen
	 * mit Erde zu – immer da, wo es gerade vorbeikommt.
	 */
	public String ebnenUmschalten(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (phase != 0) {
			BaritoneRahmen.befehl("stop");
			terminal.abbrechen();
			versorgung.abbrechen(Minecraft.getInstance());
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
		// Baritone darf nichts im Inventar umräumen (sonst landen Sachen auf Slot 1 und 9). Erde legt Versorgung in Slot 6 nach.
		BaritoneRahmen.einstellung("allowInventory", false);
		if (!einstellungen.burgEbnen || einstellungen.burgStartInventar == null) {
			// neuer Start (nicht "weitermachen"): merken, was jetzt im Inventar ist – das bleibt immer drin
			einstellungen.burgStartInventar = FunkTerminal.inventar(p);
			einstellungen.speichern();
		}
		Gelaende g = gelaendePruefen(mc);
		chat(p, g.text());
		if (g.oben() == Integer.MIN_VALUE && g.unten() == Integer.MIN_VALUE) {
			ebnenMerken(false);
			return "Die Fläche ist schon eben, nichts zu tun.";
		}
		durchgang = 0;
		if (!ebnenStarten(p, g)) {
			return "Baritone hat den Bauplan nicht angenommen.";
		}
		return "Ebnen läuft: freigraben und Löcher stopfen gleichzeitig.";
	}

	/** Gibt Baritone den Ebnen-Bauplan: von der Schicht unter deinen Füßen bis zum höchsten Hindernis. */
	private boolean ebnenStarten(LocalPlayer p, Gelaende g) {
		int fuss = tor.getY();
		int oben = Math.max(g.oben(), fuss); // mindestens die Fußhöhe (z. B. Gras), auch wenn nur Löcher da sind
		int[] a = welt(tor, vorne, 14, 14);
		int[] b = welt(tor, vorne, 135, 135);
		BlockPos ecke = new BlockPos(Math.min(a[0], b[0]), fuss - 1, Math.min(a[1], b[1]));
		if (!BaritoneRahmen.ebnenBauen(ecke, BREITE, oben - fuss + 2, BREITE)) {
			phase = 0;
			return false;
		}
		phase = 1;
		wartet = 40;
		terminalPause = 0;
		durchgang++;
		ebnenMerken(true);
		chat(p, "Baritone gräbt frei (bis Y " + oben + ") und stopft dabei Löcher und Wasser auf Y " + (fuss - 1)
				+ " mit Erde. Geht die Erde aus, holt der Bot neue aus dem Funk-Terminal in Slot 8.");
		return true;
	}

	/** Jeden Tick: essen, Erde nachlegen, Funk-Terminal, und am Ende nachschauen, ob alles eben ist. */
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
			return;
		}
		// Baritone ist fertig: nochmal nachschauen (z. B. Stellen, die vorher nicht geladen waren)
		Gelaende g = gelaendePruefen(mc);
		if ((g.oben() != Integer.MIN_VALUE || g.unten() != Integer.MIN_VALUE) && durchgang < 3 && ebnenStarten(mc.player, g)) {
			return;
		}
		phase = 0;
		ebnenMerken(false);
		chat(mc.player, "Fertig: Die Fläche ist eben. " + g.text());
		mc.player.sendOverlayMessage(Component.literal("Burg-Platz: fertig geebnet"));
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
		int fuss = tor.getY();
		int minBoden = Integer.MAX_VALUE, maxBoden = Integer.MIN_VALUE;
		int huegel = 0, loecher = 0, wasser = 0, baeume = 0, unbekannt = 0;
		int oben = Integer.MIN_VALUE, unten = Integer.MAX_VALUE;
		for (int x = 14; x <= 135; x++) {
			for (int z = 14; z <= 135; z++) {
				int[] w = welt(tor, vorne, x, z);
				if (!mc.level.hasChunk(w[0] >> 4, w[1] >> 4)) {
					unbekannt++;
					continue;
				}
				boolean baum = false, nass = false;
				int boden = Integer.MIN_VALUE, grund = Integer.MIN_VALUE;
				for (int y = fuss + SUCH_HOEHE; y >= fuss - FUELL_TIEFE - 1; y--) {
					BlockState s = mc.level.getBlockState(new BlockPos(w[0], y, w[1]));
					if (s.isAir()) {
						continue;
					}
					if (!s.liquid() && y >= fuss) {
						oben = Math.max(oben, y); // alles ab Fußhöhe muss weg (Hügel, Bäume, Gras)
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
		int alle = BREITE * BREITE;
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
		return new Gelaende(t.toString(), oben, unten == Integer.MAX_VALUE ? Integer.MIN_VALUE : unten);
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
