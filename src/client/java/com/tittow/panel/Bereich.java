package com.tittow.panel;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Die Farm-Fläche: eine beliebige Form aus Ecken (z. B. 6 Ecken für ein L), in der der Bot laufen darf,
 * und optional ein Rechteck für den Mob-Bereich (mobs1/mobs2), den er nie betritt.
 * Die Ecken setzt man der Reihe nach, einmal außen herum.
 * Wird in config/tittowpanel.json gespeichert.
 */
public final class Bereich {
	/** Ein Block-Feld auf der Fläche (nur x und z, die Höhe ist egal). */
	public record Feld(int x, int z) {
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path DATEI = FabricLoader.getInstance().getConfigDir().resolve("tittowpanel.json");

	// Gespeicherte Werte (null = noch nicht gesetzt)
	public List<int[]> ecken = new ArrayList<>(); // Ecken der Lauf-Fläche, der Reihe nach
	public int[] ecke1; // alte Version (Rechteck), wird nur genutzt, wenn keine Ecken gesetzt sind
	public int[] ecke2;
	public int[] mobs1;
	public int[] mobs2;
	public boolean hell = false; // "Hell" an oder aus
	public boolean kisten = false; // Kisten-Anzeige an oder aus
	public boolean werfen = true; // Enderperlen, Schriftrollen und Augen zu den Mobs werfen
	public List<String> geoeffneteKisten = new ArrayList<>(); // schon geöffnete Kisten ("Dimension x y z" bzw. "lore UUID")

	public static Bereich laden() {
		try {
			if (Files.exists(DATEI)) {
				try (Reader r = Files.newBufferedReader(DATEI)) {
					Bereich b = GSON.fromJson(r, Bereich.class);
					if (b != null) {
						if (b.ecken == null) {
							b.ecken = new ArrayList<>();
						}
						if (b.geoeffneteKisten == null) {
							b.geoeffneteKisten = new ArrayList<>();
						}
						return b;
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[Tittow Panel] Konnte Bereich nicht laden: " + e.getMessage());
		}
		return new Bereich();
	}

	public void speichern() {
		try {
			Files.createDirectories(DATEI.getParent());
			try (Writer w = Files.newBufferedWriter(DATEI)) {
				GSON.toJson(this, w);
			}
		} catch (Exception e) {
			System.err.println("[Tittow Panel] Konnte Bereich nicht speichern: " + e.getMessage());
		}
	}

	public boolean flaecheGesetzt() {
		return ecken.size() >= 3 || (ecke1 != null && ecke2 != null);
	}

	private boolean mitEcken() {
		return ecken.size() >= 3;
	}

	/**
	 * Bringt die Ecken in die richtige Reihenfolge, egal in welcher Reihenfolge sie gesetzt wurden.
	 * Klappt bei Formen mit nur geraden Kanten (Rechteck, L, U, ...), egal wie gedreht oder gespiegelt:
	 * Jede Ecke hat genau einen Nachbarn in der gleichen Reihe und einen in der gleichen Spalte.
	 * Wenn das nicht aufgeht, bleibt die gesetzte Reihenfolge.
	 */
	private List<int[]> geordneteEcken() {
		int n = ecken.size();
		if (n < 4 || n % 2 != 0) {
			return ecken;
		}
		int[] waagrecht = new int[n];
		int[] senkrecht = new int[n];
		if (!paaren(waagrecht, true) || !paaren(senkrecht, false)) {
			return ecken;
		}
		List<int[]> ordnung = new ArrayList<>();
		boolean[] besucht = new boolean[n];
		int i = 0;
		boolean waag = true;
		while (!besucht[i]) {
			besucht[i] = true;
			ordnung.add(ecken.get(i));
			i = waag ? waagrecht[i] : senkrecht[i];
			waag = !waag;
		}
		return ordnung.size() == n ? ordnung : ecken;
	}

	/** Paart Ecken mit gleicher z (waagrecht) bzw. gleicher x (senkrecht), jeweils nebeneinander sortiert. */
	private boolean paaren(int[] partner, boolean waagrecht) {
		Map<Integer, List<Integer>> gruppen = new HashMap<>();
		for (int i = 0; i < ecken.size(); i++) {
			int schluessel = waagrecht ? ecken.get(i)[2] : ecken.get(i)[0];
			gruppen.computeIfAbsent(schluessel, k -> new ArrayList<>()).add(i);
		}
		for (List<Integer> g : gruppen.values()) {
			if (g.size() % 2 != 0) {
				return false;
			}
			g.sort((a, b) -> Integer.compare(waagrecht ? ecken.get(a)[0] : ecken.get(a)[2],
					waagrecht ? ecken.get(b)[0] : ecken.get(b)[2]));
			for (int k = 0; k < g.size(); k += 2) {
				partner[g.get(k)] = g.get(k + 1);
				partner[g.get(k + 1)] = g.get(k);
			}
		}
		return true;
	}

	/** Liegt die Mitte des Feldes in der Form (Rand zählt mit)? */
	private boolean inForm(int x, int z) {
		double px = x + 0.5;
		double pz = z + 0.5;
		boolean drinnen = false;
		List<int[]> form = geordneteEcken();
		int n = form.size();
		for (int i = 0, j = n - 1; i < n; j = i++) {
			double xi = form.get(i)[0] + 0.5, zi = form.get(i)[2] + 0.5;
			double xj = form.get(j)[0] + 0.5, zj = form.get(j)[2] + 0.5;
			// auf dem Rand?
			double kreuz = (px - xi) * (zj - zi) - (pz - zi) * (xj - xi);
			if (Math.abs(kreuz) < 1e-9 && px >= Math.min(xi, xj) - 1e-9 && px <= Math.max(xi, xj) + 1e-9
					&& pz >= Math.min(zi, zj) - 1e-9 && pz <= Math.max(zi, zj) + 1e-9) {
				return true;
			}
			if ((zi > pz) != (zj > pz) && px < (xj - xi) * (pz - zi) / (zj - zi) + xi) {
				drinnen = !drinnen;
			}
		}
		return drinnen;
	}

	/** {minX, maxX, minZ, maxZ} der Fläche */
	public int[] grenzen() {
		if (mitEcken()) {
			int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
			for (int[] e : ecken) {
				minX = Math.min(minX, e[0]);
				maxX = Math.max(maxX, e[0]);
				minZ = Math.min(minZ, e[2]);
				maxZ = Math.max(maxZ, e[2]);
			}
			return new int[] {minX, maxX, minZ, maxZ};
		}
		return new int[] {Math.min(ecke1[0], ecke2[0]), Math.max(ecke1[0], ecke2[0]),
			Math.min(ecke1[2], ecke2[2]), Math.max(ecke1[2], ecke2[2])};
	}

	public boolean mobsGesetzt() {
		return mobs1 != null && mobs2 != null;
	}

	private static boolean drin(int[] a, int[] b, int x, int z) {
		return a != null && b != null
				&& x >= Math.min(a[0], b[0]) && x <= Math.max(a[0], b[0])
				&& z >= Math.min(a[2], b[2]) && z <= Math.max(a[2], b[2]);
	}

	/** Darf der Bot auf diesem Feld stehen? */
	public boolean erlaubt(int x, int z) {
		if (!flaecheGesetzt()) {
			return false;
		}
		if (mitEcken() ? !inForm(x, z) : !drin(ecke1, ecke2, x, z)) {
			return false;
		}
		return !drin(mobs1, mobs2, x, z);
	}

	public boolean imMobBereich(int x, int z) {
		return drin(mobs1, mobs2, x, z);
	}

	public List<Feld> freieFelder() {
		List<Feld> felder = new ArrayList<>();
		if (!flaecheGesetzt()) {
			return felder;
		}
		int[] g = grenzen();
		for (int x = g[0]; x <= g[1]; x++) {
			for (int z = g[2]; z <= g[3]; z++) {
				if (erlaubt(x, z)) {
					felder.add(new Feld(x, z));
				}
			}
		}
		return felder;
	}

	/** Freie Felder direkt neben dem Mob-Bereich, von dort trifft der Bot am besten. */
	public List<Feld> felderAmMob() {
		List<Feld> ergebnis = new ArrayList<>();
		for (Feld f : freieFelder()) {
			outer:
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (imMobBereich(f.x() + dx, f.z() + dz)) {
						ergebnis.add(f);
						break outer;
					}
				}
			}
		}
		return ergebnis;
	}

	/** Mitte vom Mob-Bereich (zum Hinschauen), oder null. */
	public double[] mobMitte() {
		if (!mobsGesetzt()) {
			return null;
		}
		return new double[] {(mobs1[0] + mobs2[0]) / 2.0 + 0.5, (mobs1[1] + mobs2[1]) / 2.0, (mobs1[2] + mobs2[2]) / 2.0 + 0.5};
	}

	/**
	 * Kürzester Weg über erlaubte Felder (Breitensuche, nur gerade Schritte),
	 * damit der Bot bei einer L-Form außen herum läuft und nie durch den Mob-Bereich.
	 */
	public List<Feld> weg(Feld start, Feld ziel) {
		ArrayDeque<Feld> offen = new ArrayDeque<>();
		Map<Feld, Feld> herkunft = new HashMap<>();
		offen.add(start);
		herkunft.put(start, start);
		int[][] schritte = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (!offen.isEmpty()) {
			Feld f = offen.poll();
			if (f.equals(ziel)) {
				List<Feld> weg = new ArrayList<>();
				for (Feld p = ziel; !p.equals(start); p = herkunft.get(p)) {
					weg.add(p);
				}
				Collections.reverse(weg);
				return weg;
			}
			for (int[] s : schritte) {
				Feld n = new Feld(f.x() + s[0], f.z() + s[1]);
				if (!herkunft.containsKey(n) && erlaubt(n.x(), n.z())) {
					herkunft.put(n, f);
					offen.add(n);
				}
			}
		}
		return null; // nicht erreichbar
	}

	public String beschreibung() {
		if (!flaecheGesetzt()) {
			return ecken.isEmpty() ? "Fläche: noch nicht eingerichtet"
					: "Fläche: " + ecken.size() + " Ecke(n) gesetzt, mindestens 3 nötig";
		}
		int[] g = grenzen();
		String form = mitEcken() ? ecken.size() + " Ecken, " : "";
		String mobs = mobsGesetzt()
				? (Math.abs(mobs1[0] - mobs2[0]) + 1) + "x" + (Math.abs(mobs1[2] - mobs2[2]) + 1)
				: "keiner";
		return "Fläche " + form + (g[1] - g[0] + 1) + "x" + (g[3] - g[2] + 1) + ", Mob-Bereich " + mobs
				+ ", " + freieFelder().size() + " freie Felder";
	}
}
