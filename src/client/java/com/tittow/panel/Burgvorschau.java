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
 * auch durch Wände (Baritone zeichnet sie, siehe BaritoneRahmen). Es wird nichts gebaut.
 * Wo du beim Einschalten stehst, ist die Mitte der Eingangsmauer (das Tor). Die Burg geht in die Richtung,
 * in die du schaust. Der Sockel beginnt auf Höhe deiner Füße.
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

	private boolean an;
	private List<BlockPos[]> boxen = List.of();

	public boolean istAn() {
		return an;
	}

	/** Einschalten: Burg an deiner Stelle und Blickrichtung zeigen. Ausschalten: Rahmen weg. Gibt eine Meldung zurück. */
	public String umschalten(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (an) {
			aus();
			return "Burg-Vorschau aus.";
		}
		if (p == null || mc.level == null) {
			return "";
		}
		if (!BaritoneRahmen.vorhanden()) {
			return "Ohne Baritone kann die Vorschau keine Rahmen zeichnen.";
		}
		Direction vorne = p.getDirection(); // Blickrichtung, auf Norden/Osten/Süden/Westen gerundet
		BlockPos tor = p.blockPosition();
		boxen = rahmenBerechnen(tor, vorne);
		an = true;
		BaritoneRahmen.zeigen("burg", boxen);
		String pruefung = gelaendePruefen(mc, tor, vorne);
		p.sendOverlayMessage(Component.literal("Burg-Vorschau: AN, Tor hier, Burg Richtung " + richtung(vorne)));
		p.sendSystemMessage(Component.literal("§d[Burg-Vorschau]§r Tor bei " + tor.getX() + " " + tor.getY() + " " + tor.getZ()
				+ ", Burg geht nach " + richtung(vorne) + ", " + BREITE + " x " + BREITE + " Blöcke, Spitze bei Y "
				+ (tor.getY() + HOEHE - 1) + "."));
		p.sendSystemMessage(Component.literal("§d[Burg-Vorschau]§r " + pruefung));
		return "Burg Richtung " + richtung(vorne) + " – Gelände-Prüfung steht im Chat.";
	}

	/** Rahmen weg (auch beim Verlassen der Welt). */
	public void aus() {
		an = false;
		boxen = List.of();
		BaritoneRahmen.zeigen("burg", boxen);
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
	 * wo Löcher oder Wasser unter dem Sockel wären und wo Bäume stehen.
	 */
	private static String gelaendePruefen(Minecraft mc, BlockPos tor, Direction vorne) {
		int fuss = tor.getY();
		int minBoden = Integer.MAX_VALUE, maxBoden = Integer.MIN_VALUE;
		int huegel = 0, loecher = 0, wasser = 0, baeume = 0, unbekannt = 0;
		for (int x = 14; x <= 135; x++) {
			for (int z = 14; z <= 135; z++) {
				int[] w = welt(tor, vorne, x, z);
				if (!mc.level.hasChunk(w[0] >> 4, w[1] >> 4)) {
					unbekannt++;
					continue;
				}
				boolean baum = false;
				int boden = Integer.MIN_VALUE;
				boolean nass = false;
				for (int y = fuss + 40; y >= fuss - 20; y--) {
					BlockState s = mc.level.getBlockState(new BlockPos(w[0], y, w[1]));
					if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS)) {
						baum = true;
						continue;
					}
					if (s.liquid()) {
						nass = true;
						boden = y;
						break;
					}
					if (s.isSolid()) {
						boden = y;
						break;
					}
				}
				if (baum) {
					baeume++;
				}
				if (boden == Integer.MIN_VALUE) {
					loecher++;
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
			t.append(", ").append(prozent(unbekannt, alle)).append(" nicht geladen");
		}
		return t.toString();
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
