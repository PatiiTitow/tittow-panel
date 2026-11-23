package com.tittow.panel;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Der Bauplan der Blutfeste (Datei assets/tittowpanel/burg/bauplan.bin.gz, erzeugt mit Burgen.java bauplandatei).
 * Plan-Koordinaten: x und z 0..121 (im Vorschau-Plan 14..135), y = 0 ist die Höhe deiner Füße am Tor.
 * Gebaut wird in Abschnitten: erst der Sockel, dann je 6 Blöcke Höhe.
 */
final class Bauplan {
	/** Im Vorschau-Plan (Burgvorschau) liegt die Burg bei 14..135. */
	static final int VERSATZ = 14;
	private static final int ABSCHNITT_HOEHE = 6;

	private static Bauplan geladen;

	final int sockel, w, h, l;
	final String[] ids;
	final BlockState[] zustaende;
	private final byte[] daten;

	private Bauplan(int sockel, int w, int h, int l, String[] ids, byte[] daten) {
		this.sockel = sockel;
		this.w = w;
		this.h = h;
		this.l = l;
		this.ids = ids;
		this.daten = daten;
		zustaende = new BlockState[ids.length];
		for (int i = 0; i < ids.length; i++) {
			zustaende[i] = BuiltInRegistries.BLOCK.getValue(Identifier.parse(ids[i])).defaultBlockState();
		}
	}

	/** Lädt den Bauplan einmal (null, wenn die Datei fehlt). */
	static Bauplan laden() {
		if (geladen != null) {
			return geladen;
		}
		try (InputStream roh = Bauplan.class.getResourceAsStream("/assets/tittowpanel/burg/bauplan.bin.gz")) {
			if (roh == null) {
				return null;
			}
			DataInputStream in = new DataInputStream(new GZIPInputStream(roh));
			byte[] kopf = new byte[4];
			in.readFully(kopf);
			if (!new String(kopf).equals("TPBP")) {
				return null;
			}
			int sockel = in.readInt(), w = in.readInt(), h = in.readInt(), l = in.readInt();
			String[] ids = new String[in.readInt()];
			for (int i = 0; i < ids.length; i++) {
				ids[i] = in.readUTF();
			}
			byte[] daten = new byte[w * h * l];
			in.readFully(daten);
			geladen = new Bauplan(sockel, w, h, l, ids, daten);
		} catch (Exception e) {
			System.err.println("[Tittow Panel] Bauplan nicht lesbar: " + e);
		}
		return geladen;
	}

	/** Paletten-Nummer an dieser Plan-Stelle (0 = Luft oder außerhalb). */
	int index(int x, int y, int z) {
		if (x < 0 || y < 0 || z < 0 || x >= w || y >= h || z >= l) {
			return 0;
		}
		return daten[(y * l + z) * w + x] & 0xFF;
	}

	/** Kann der Bot das setzen? Luft, Lava und Wasser nicht (Lava im Brunnen kommt per Eimer). */
	boolean setzbar(int index) {
		if (index == 0) {
			return false;
		}
		String id = ids[index];
		return !id.equals("minecraft:air") && !id.equals("minecraft:lava") && !id.equals("minecraft:water");
	}

	/** Die Kästen der Türme (im Vorschau-Plan, {x0, z0, x1, z1}): Ecktürme, Torturm, Torhaus und Mauertürme. */
	private static final List<int[]> TURM_KAESTEN = new ArrayList<>();

	static {
		TURM_KAESTEN.addAll(List.of(new int[] {14, 14, 24, 24}, new int[] {125, 14, 135, 24}, new int[] {14, 125, 24, 135},
				new int[] {125, 125, 135, 135}, new int[] {60, 125, 68, 135}, new int[] {82, 125, 90, 135}, new int[] {69, 125, 81, 135}));
		for (int m : new int[] {38, 56, 75, 94, 112}) {
			TURM_KAESTEN.add(new int[] {m - 3, 14, m + 3, 22});
			TURM_KAESTEN.add(new int[] {14, m - 3, 22, m + 3});
			TURM_KAESTEN.add(new int[] {127, m - 3, 135, m + 3});
			if (m == 38 || m == 112) {
				TURM_KAESTEN.add(new int[] {m - 3, 127, m + 3, 135});
			}
		}
	}

	/** Gehört diese Plan-Stelle (x, z) zur Mauer mit Türmen (Außenmauer, Ecktürme, Mauertürme, Tor)? */
	static boolean mauerRing(int x, int z) {
		int mx = x + VERSATZ, mz = z + VERSATZ;
		if (mx <= 20 || mx >= 129 || mz <= 20 || mz >= 129) {
			return true;
		}
		for (int[] b : TURM_KAESTEN) {
			if (mx >= b[0] && mx <= b[2] && mz >= b[1] && mz <= b[3]) {
				return true;
			}
		}
		return false;
	}

	/** Bis Höhe 19 geht die Mauer mit Zinnen rundherum, ab hier stehen nur noch die einzelnen Türme. */
	static final int TURM_AB = 20;

	/** Die einzelnen Türme ab Höhe TURM_AB als Kästen {x0, z0, x1, z1}, einmal im Kreis um die Burg sortiert. */
	List<int[]> mauerTuerme() {
		boolean[] voll = new boolean[w * l];
		for (int y = TURM_AB; y < h; y++) {
			for (int z = 0; z < l; z++) {
				for (int x = 0; x < w; x++) {
					if (mauerRing(x, z) && setzbar(index(x, y, z))) {
						voll[z * w + x] = true;
					}
				}
			}
		}
		boolean[] gesehen = new boolean[w * l];
		List<int[]> tuerme = new ArrayList<>();
		for (int z = 0; z < l; z++) {
			for (int x = 0; x < w; x++) {
				if (!voll[z * w + x] || gesehen[z * w + x]) {
					continue;
				}
				int[] k = {x, z, x, z};
				java.util.ArrayDeque<int[]> offen = new java.util.ArrayDeque<>();
				offen.add(new int[] {x, z});
				gesehen[z * w + x] = true;
				while (!offen.isEmpty()) {
					int[] p = offen.poll();
					k[0] = Math.min(k[0], p[0]);
					k[1] = Math.min(k[1], p[1]);
					k[2] = Math.max(k[2], p[0]);
					k[3] = Math.max(k[3], p[1]);
					for (int dz = -1; dz <= 1; dz++) {
						for (int dx = -1; dx <= 1; dx++) {
							int xx = p[0] + dx, zz = p[1] + dz;
							if (xx >= 0 && zz >= 0 && xx < w && zz < l && voll[zz * w + xx] && !gesehen[zz * w + xx]) {
								gesehen[zz * w + xx] = true;
								offen.add(new int[] {xx, zz});
							}
						}
					}
				}
				tuerme.add(k);
			}
		}
		double cx = (w - 1) / 2.0, cz = (l - 1) / 2.0;
		tuerme.sort((a, b) -> Double.compare(Math.atan2((a[1] + a[3]) / 2.0 - cz, (a[0] + a[2]) / 2.0 - cx),
				Math.atan2((b[1] + b[3]) / 2.0 - cz, (b[0] + b[2]) / 2.0 - cx)));
		return tuerme;
	}

	/** Die oberste Höhe, in der die Mauer mit Türmen noch Blöcke hat. */
	int mauerOben() {
		for (int y = h - 1; y >= sockel; y--) {
			for (int z = 0; z < l; z++) {
				for (int x = 0; x < w; x++) {
					if (setzbar(index(x, y, z)) && mauerRing(x, z)) {
						return y;
					}
				}
			}
		}
		return sockel;
	}

	/** Abschnitte als {von y, bis y}: zuerst der Sockel, dann je 6 Blöcke hoch (leere oben weg). */
	List<int[]> abschnitte() {
		List<int[]> a = new ArrayList<>();
		a.add(new int[] {0, sockel - 1});
		for (int y = sockel; y < h; y += ABSCHNITT_HOEHE) {
			int bis = Math.min(h - 1, y + ABSCHNITT_HOEHE - 1);
			if (!material(y, bis).isEmpty()) {
				a.add(new int[] {y, bis});
			}
		}
		return a;
	}

	/** Welche Blöcke in den Höhen von..bis gebraucht werden (Block-Id -> Anzahl), die meisten zuerst. */
	Map<String, Long> material(int von, int bis) {
		long[] n = new long[ids.length];
		for (int y = Math.max(0, von); y <= Math.min(h - 1, bis); y++) {
			for (int z = 0; z < l; z++) {
				for (int x = 0; x < w; x++) {
					n[index(x, y, z)]++;
				}
			}
		}
		Map<String, Long> m = new LinkedHashMap<>();
		List<Integer> reihe = new ArrayList<>();
		for (int i = 0; i < ids.length; i++) {
			if (n[i] > 0 && setzbar(i)) {
				reihe.add(i);
			}
		}
		reihe.sort((p, q) -> Long.compare(n[q], n[p]));
		for (int i : reihe) {
			m.put(ids[i], n[i]);
		}
		return m;
	}

	/** Paletten-Nummern, die in Höhe y vorkommen (setzbare), die häufigste zuerst. */
	List<Integer> sortenInSchicht(int y) {
		long[] n = new long[ids.length];
		for (int z = 0; z < l; z++) {
			for (int x = 0; x < w; x++) {
				n[index(x, y, z)]++;
			}
		}
		List<Integer> reihe = new ArrayList<>();
		for (int i = 0; i < ids.length; i++) {
			if (n[i] > 0 && setzbar(i)) {
				reihe.add(i);
			}
		}
		reihe.sort((p, q) -> Long.compare(n[q], n[p]));
		return reihe;
	}
}
