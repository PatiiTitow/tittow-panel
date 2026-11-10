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
 * Die Farm-Fläche: ein Rechteck (ecke1/ecke2), in dem der Bot laufen darf,
 * und ein Rechteck für den Mob-Bereich (mobs1/mobs2), den er nie betritt.
 * Wird in config/tittowpanel.json gespeichert.
 */
public final class Bereich {
	/** Ein Block-Feld auf der Fläche (nur x und z, die Höhe ist egal). */
	public record Feld(int x, int z) {
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path DATEI = FabricLoader.getInstance().getConfigDir().resolve("tittowpanel.json");

	// Gespeicherte Werte (null = noch nicht gesetzt)
	public int[] ecke1;
	public int[] ecke2;
	public int[] mobs1;
	public int[] mobs2;

	public static Bereich laden() {
		try {
			if (Files.exists(DATEI)) {
				try (Reader r = Files.newBufferedReader(DATEI)) {
					Bereich b = GSON.fromJson(r, Bereich.class);
					if (b != null) {
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
		return ecke1 != null && ecke2 != null;
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
		if (!flaecheGesetzt() || !drin(ecke1, ecke2, x, z)) {
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
		for (int x = Math.min(ecke1[0], ecke2[0]); x <= Math.max(ecke1[0], ecke2[0]); x++) {
			for (int z = Math.min(ecke1[2], ecke2[2]); z <= Math.max(ecke1[2], ecke2[2]); z++) {
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
			return "Fläche: noch nicht eingerichtet";
		}
		int breit = Math.abs(ecke1[0] - ecke2[0]) + 1;
		int lang = Math.abs(ecke1[2] - ecke2[2]) + 1;
		String mobs = mobsGesetzt()
				? (Math.abs(mobs1[0] - mobs2[0]) + 1) + "x" + (Math.abs(mobs1[2] - mobs2[2]) + 1)
				: "fehlt";
		return "Fläche " + breit + "x" + lang + ", Mob-Bereich " + mobs + ", " + freieFelder().size() + " freie Felder";
	}
}
