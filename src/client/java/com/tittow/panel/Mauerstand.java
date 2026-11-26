package com.tittow.panel;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;

/**
 * Merkt sich für die Mauer mit Türmen, welche Bauplan-Stellen schon richtig in der Welt stehen. So weiß der Bot auch bei
 * Teilen der Burg, die gerade zu weit weg (nicht geladen) sind, ob dort noch ein Loch ist, und muss nicht jedes Mal
 * rundherum laufen und nachsehen. Steht in config/tittowpanel-mauer.bin, damit es einen Neustart übersteht.
 */
final class Mauerstand {
	private static final Path DATEI = FabricLoader.getInstance().getConfigDir().resolve("tittowpanel-mauer.bin");
	private static BitSet steht = new BitSet();
	private static long burg = Long.MIN_VALUE;
	private static int w, h, l;
	private static boolean geaendert;

	private Mauerstand() {
	}

	/** Lädt den gemerkten Stand für diese Burg. Steht die Burg woanders, fängt er leer an. */
	static void fuerBurg(BlockPos tor, Bauplan plan) {
		long k = tor.asLong();
		if (burg == k && w == plan.w && h == plan.h && l == plan.l) {
			return;
		}
		speichern();
		burg = k;
		w = plan.w;
		h = plan.h;
		l = plan.l;
		steht = new BitSet();
		geaendert = false;
		try {
			if (Files.exists(DATEI)) {
				try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(DATEI)))) {
					if (in.readLong() == k && in.readInt() == w && in.readInt() == h && in.readInt() == l) {
						byte[] b = new byte[in.readInt()];
						in.readFully(b);
						steht = BitSet.valueOf(b);
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[Tittow Panel] Mauer-Stand nicht lesbar: " + e.getMessage());
		}
	}

	/** Steht diese Bauplan-Stelle laut letztem Nachsehen schon richtig? */
	static boolean steht(int x, int yy, int z) {
		return w > 0 && steht.get(stelle(x, yy, z));
	}

	/** Gerade in der Welt nachgesehen: steht dort der richtige Block oder nicht. */
	static void merken(int x, int yy, int z, boolean richtig) {
		if (w == 0) {
			return;
		}
		int i = stelle(x, yy, z);
		if (steht.get(i) != richtig) {
			steht.set(i, richtig);
			geaendert = true;
		}
	}

	static void speichern() {
		if (!geaendert || w == 0) {
			return;
		}
		try {
			Files.createDirectories(DATEI.getParent());
			try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(DATEI)))) {
				byte[] b = steht.toByteArray();
				out.writeLong(burg);
				out.writeInt(w);
				out.writeInt(h);
				out.writeInt(l);
				out.writeInt(b.length);
				out.write(b);
			}
			geaendert = false;
		} catch (Exception e) {
			System.err.println("[Tittow Panel] Mauer-Stand nicht gespeichert: " + e.getMessage());
		}
	}

	private static int stelle(int x, int yy, int z) {
		return (yy * l + z) * w + x;
	}
}
