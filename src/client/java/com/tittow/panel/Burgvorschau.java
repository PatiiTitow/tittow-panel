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
 * Dazu "Platz ebnen": Baritone gräbt die Sockel-Fläche frei und stopft danach Löcher und Wasser mit Erde.
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
	private static final int FUELL_TIEFE = 8;
	/** Bis wie hoch über deinen Füßen nach Hügeln und Bäumen gesucht wird. */
	private static final int SUCH_HOEHE = 64;

	private boolean an;
	private List<BlockPos[]> boxen = List.of();
	private BlockPos tor;
	private Direction vorne;

	/** Platz ebnen: 0 = aus, 1 = Baritone gräbt frei, 2 = Baritone stopft Löcher. */
	private int phase;
	private int wartet;

	/** Ergebnis der Gelände-Prüfung. oben/unten = Integer.MIN_VALUE, wenn nichts zu tun ist. */
	private record Gelaende(String text, int oben, int unten) {
	}

	public boolean istAn() {
		return an;
	}

	public boolean ebnetGerade() {
		return phase != 0;
	}

	/** Einschalten: Burg an deiner Stelle und Blickrichtung zeigen. Ausschalten: Rahmen weg. Gibt eine Meldung zurück. */
	public String umschalten(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (an) {
			if (phase != 0) {
				return "Erst das Ebnen stoppen.";
			}
			aus();
			return "Burg-Vorschau aus.";
		}
		if (p == null || mc.level == null) {
			return "";
		}
		if (!BaritoneRahmen.vorhanden()) {
			return "Ohne Baritone kann die Vorschau keine Rahmen zeichnen.";
		}
		vorne = p.getDirection(); // Blickrichtung, auf Norden/Osten/Süden/Westen gerundet
		tor = p.blockPosition();
		boxen = rahmenBerechnen(tor, vorne);
		an = true;
		BaritoneRahmen.zeigen("burg", boxen);
		Gelaende g = gelaendePruefen(mc);
		p.sendOverlayMessage(Component.literal("Burg-Vorschau: AN, Tor hier, Burg Richtung " + richtung(vorne)));
		chat(p, "Tor bei " + tor.getX() + " " + tor.getY() + " " + tor.getZ() + ", Burg geht nach " + richtung(vorne) + ", "
				+ BREITE + " x " + BREITE + " Blöcke, Spitze bei Y " + (tor.getY() + HOEHE - 1) + ".");
		chat(p, g.text());
		return "Burg Richtung " + richtung(vorne) + " – Gelände-Prüfung steht im Chat.";
	}

	/** Rahmen weg (auch beim Verlassen der Welt). Ein laufendes Ebnen wird gestoppt. */
	public void aus() {
		if (phase != 0) {
			BaritoneRahmen.befehl("stop");
			phase = 0;
		}
		an = false;
		boxen = List.of();
		BaritoneRahmen.zeigen("burg", boxen);
	}

	// ---- Platz ebnen -------------------------------------------------------------

	/**
	 * Startet oder stoppt das Ebnen der Sockel-Fläche: Baritone gräbt erst alles ab Fußhöhe weg (Hügel, Bäume,
	 * Gras), dann füllt es Löcher und Wasser darunter mit Erde auf (höchstens FUELL_TIEFE tief).
	 */
	public String ebnenUmschalten(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (phase != 0) {
			BaritoneRahmen.befehl("stop");
			phase = 0;
			return "Ebnen gestoppt.";
		}
		if (p == null || mc.level == null) {
			return "";
		}
		if (!an) {
			return "Erst „Burg hier zeigen“ – geebnet wird genau die Sockel-Fläche.";
		}
		if (!BaritoneRahmen.vorhanden()) {
			return "Ohne Baritone geht das Ebnen nicht.";
		}
		Gelaende g = gelaendePruefen(mc);
		chat(p, g.text());
		if (g.oben() != Integer.MIN_VALUE) {
			if (!bereichBefehl(tor.getY(), g.oben(), "sel cleararea")) {
				return "Baritone hat den Befehl nicht angenommen.";
			}
			phase = 1;
			wartet = 40;
			chat(p, "Schritt 1: Baritone gräbt frei, von Y " + tor.getY() + " bis Y " + g.oben() + ". Danach werden Löcher gestopft.");
			return "Ebnen läuft: erst freigraben, dann Löcher stopfen.";
		}
		if (g.unten() != Integer.MIN_VALUE) {
			return fuellenStarten(p, g) ? "Nichts zum Freigraben, Löcher werden gestopft." : "Baritone hat den Befehl nicht angenommen.";
		}
		return "Die Fläche ist schon eben, nichts zu tun.";
	}

	/** Jeden Tick: ist das Freigraben fertig, kommt das Löcherstopfen, danach ist Schluss. */
	public void tick(Minecraft mc) {
		if (phase == 0 || mc.player == null || mc.level == null) {
			return;
		}
		if (wartet > 0) { // Baritone braucht einen Moment, bis es loslegt
			wartet--;
			return;
		}
		if (BaritoneRahmen.bautGerade()) {
			return;
		}
		if (phase == 1) {
			// nach dem Graben nochmal nachschauen, wo jetzt noch Löcher sind
			Gelaende g = gelaendePruefen(mc);
			if (g.unten() != Integer.MIN_VALUE && fuellenStarten(mc.player, g)) {
				return;
			}
		}
		phase = 0;
		chat(mc.player, "Fertig: Die Fläche ist eben. Zum Kontrollieren die Vorschau nochmal neu zeigen.");
		mc.player.sendOverlayMessage(Component.literal("Burg-Platz: fertig geebnet"));
	}

	private boolean fuellenStarten(LocalPlayer p, Gelaende g) {
		if (!bereichBefehl(g.unten(), tor.getY() - 1, "sel replace air cave_air water lava dirt")) {
			phase = 0;
			return false;
		}
		phase = 2;
		wartet = 40;
		chat(p, "Schritt 2: Baritone stopft Löcher und Wasser mit Erde, von Y " + g.unten() + " bis Y " + (tor.getY() - 1)
				+ ". Du brauchst genug Erde im Inventar.");
		return true;
	}

	/**
	 * Lässt Baritone einen "sel"-Befehl auf der Sockel-Fläche zwischen zwei Höhen ausführen. Baritone nimmt dafür alle
	 * Auswahl-Rahmen, deshalb kommen Kisten- und Burg-Rahmen kurz weg und danach wieder hin.
	 */
	private boolean bereichBefehl(int y0, int y1, String befehl) {
		int[] a = welt(tor, vorne, 14, 14);
		int[] b = welt(tor, vorne, 135, 135);
		BaritoneRahmen.befehl("sel clear");
		BaritoneRahmen.vergessen();
		boolean ok = BaritoneRahmen.befehl("sel pos1 " + a[0] + " " + y0 + " " + a[1])
				&& BaritoneRahmen.befehl("sel pos2 " + b[0] + " " + y1 + " " + b[1])
				&& BaritoneRahmen.befehl(befehl);
		BaritoneRahmen.befehl("sel clear"); // der Auftrag ist schon bei Baritone, die Auswahl braucht es nicht mehr
		BaritoneRahmen.vergessen();
		BaritoneRahmen.zeigen("burg", boxen);
		return ok;
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
