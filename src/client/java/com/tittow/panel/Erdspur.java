package com.tittow.panel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Merkt sich genau, wo der Bot beim Mauerbau selbst Erde hingesetzt hat, auch außerhalb der Burg.
 * So kann er am Ende sein ganzes Gerüst abbauen, ohne natürliche Erde (Hügel) anzufassen.
 * Steht in config/tittowpanel-geruest.txt, damit es einen Neustart übersteht.
 */
public final class Erdspur {
	private static final Path DATEI = FabricLoader.getInstance().getConfigDir().resolve("tittowpanel-geruest.txt");
	private static final Set<Long> BLOECKE = new HashSet<>();
	private static long burg = Long.MIN_VALUE;
	private static boolean aktiv, geaendert;
	private static BlockPos angeklickt, daneben; // die zwei Stellen, an denen der neue Block landen kann
	private static boolean angeklicktErde, danebenErde;

	private Erdspur() {
	}

	/** Lädt die gemerkte Erde für diese Burg. Steht die Burg woanders, fängt die Liste neu an. */
	static void fuerBurg(BlockPos tor) {
		long k = tor.asLong();
		if (burg == k) {
			return;
		}
		burg = k;
		BLOECKE.clear();
		try {
			if (Files.exists(DATEI)) {
				List<String> zeilen = Files.readAllLines(DATEI);
				if (!zeilen.isEmpty() && zeilen.get(0).trim().equals(Long.toString(k))) {
					for (int i = 1; i < zeilen.size(); i++) {
						BLOECKE.add(Long.parseLong(zeilen.get(i).trim()));
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[Tittow Panel] Gerüst-Liste nicht lesbar: " + e.getMessage());
		}
	}

	static void aktiv(boolean an) {
		aktiv = an;
		if (!an) {
			speichern();
		}
	}

	static void speichern() {
		if (!geaendert) {
			return;
		}
		try {
			List<String> zeilen = new ArrayList<>();
			zeilen.add(Long.toString(burg));
			for (long b : BLOECKE) {
				zeilen.add(Long.toString(b));
			}
			Files.createDirectories(DATEI.getParent());
			Files.write(DATEI, zeilen);
			geaendert = false;
		} catch (Exception e) {
			System.err.println("[Tittow Panel] Gerüst-Liste nicht gespeichert: " + e.getMessage());
		}
	}

	static boolean enthaelt(BlockPos pos) {
		return BLOECKE.contains(pos.asLong());
	}

	static List<BlockPos> alle() {
		List<BlockPos> l = new ArrayList<>(BLOECKE.size());
		for (long b : BLOECKE) {
			l.add(BlockPos.of(b));
		}
		return l;
	}

	/** Vergisst Stellen, an denen keine Erde mehr steht (nur in geladenen Chunks, sonst weiß man es nicht). */
	static void aufraeumen(ClientLevel level) {
		if (BLOECKE.removeIf(b -> {
			BlockPos p = BlockPos.of(b);
			return level.hasChunk(p.getX() >> 4, p.getZ() >> 4) && !level.getBlockState(p).is(Blocks.DIRT);
		})) {
			geaendert = true;
		}
	}

	/** Vor jedem Rechtsklick auf einen Block: Wird gerade Erde gesetzt? Dann merken, was an den zwei Stellen war. */
	public static void vorSetzen(LocalPlayer p, InteractionHand hand, BlockHitResult treffer) {
		angeklickt = null;
		ClientLevel level = Minecraft.getInstance().level;
		if (!aktiv || level == null || !p.getItemInHand(hand).is(Items.DIRT)) {
			return;
		}
		angeklickt = treffer.getBlockPos();
		daneben = angeklickt.relative(treffer.getDirection());
		angeklicktErde = level.getBlockState(angeklickt).is(Blocks.DIRT);
		danebenErde = level.getBlockState(daneben).is(Blocks.DIRT);
	}

	/** Nach dem Rechtsklick: Ist dort jetzt neue Erde? Dann gehört sie zum Gerüst. */
	public static void nachSetzen() {
		ClientLevel level = Minecraft.getInstance().level;
		if (angeklickt == null || level == null) {
			return;
		}
		if (!danebenErde && level.getBlockState(daneben).is(Blocks.DIRT)) {
			geaendert |= BLOECKE.add(daneben.asLong());
		} else if (!angeklicktErde && level.getBlockState(angeklickt).is(Blocks.DIRT)) {
			geaendert |= BLOECKE.add(angeklickt.asLong()); // z. B. hohes Gras wurde ersetzt
		}
		angeklickt = null;
	}
}
