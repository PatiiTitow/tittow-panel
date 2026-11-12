package com.tittow.panel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Kisten-Anzeige (aus dem Patri-Autofarmer übernommen): ungeöffnete Beute-Kisten in der Umgebung leuchten grün,
 * auch durch Wände. Baritone zeichnet die Rahmen (siehe BaritoneRahmen).
 * Nicht angezeigt werden: Kisten, die du schon geöffnet hast, Lager von Spielern und die Fake-Kisten des
 * Anti-Xray-Schutzes (rundum im Stein eingeschlossen). Kistenloren zählen mit.
 */
public final class Kisten {
	/** Wie weit gesucht wird (in Chunks, höchstens die Sichtweite) und wie viele Kisten höchstens leuchten. */
	private static final int REICHWEITE_CHUNKS = 16;
	private static final int MAX_KISTEN = 48;
	/** Was Spieler um ihre Lager bauen. Steht so etwas (offen sichtbar) bei der Kiste, ist es kein Beute-Fund. */
	private static final List<String> SPIELER_BAU = List.of("_bed", "_sign", "_door", "trapdoor", "torch", "lantern", "hopper",
			"furnace", "smoker", "crafting_table", "barrel", "glass", "glass_pane", "_rail", "shulker_box", "enchanting_table", "anvil");
	private static final int MAX_GEMERKT = 2000;

	private final Bereich einstellungen;
	private List<BlockPos[]> boxen = List.of();
	/** Was um eine Kiste steht: "-" = Lager eines Spielers, sonst Beute. Je Welt gemerkt. */
	private final Map<BlockPos, String> umgebung = new HashMap<>();
	private Set<String> geoeffnet;
	private Level welt;
	private int tick;
	private int naechsteSuche;

	public Kisten(Bereich einstellungen) {
		this.einstellungen = einstellungen;
	}

	public boolean istAn() {
		return einstellungen.kisten;
	}

	public void umschalten(Minecraft mc) {
		einstellungen.kisten = !einstellungen.kisten;
		einstellungen.speichern();
		LocalPlayer p = mc.player;
		if (!einstellungen.kisten) {
			leeren();
			melden(p, "Kisten: AUS");
			return;
		}
		if (!BaritoneRahmen.vorhanden()) {
			melden(p, "Kisten: AN, aber Baritone fehlt – ohne Baritone keine Rahmen");
			return;
		}
		if (p == null || mc.level == null) {
			return;
		}
		suchen(mc);
		melden(p, "Kisten: AN – " + (boxen.isEmpty() ? "keine ungeöffneten Kisten in Reichweite"
				: boxen.size() + " ungeöffnete leuchten grün, auch durch Wände"));
	}

	/** Jeden Tick: geöffnete Kisten merken, einmal pro Sekunde neu suchen. */
	public void tick(Minecraft mc) {
		tick++;
		if (mc.player == null || mc.level == null) {
			if (!boxen.isEmpty()) {
				leeren();
			}
			welt = null;
			return;
		}
		geoeffneteMerken(mc);
		if (einstellungen.kisten && tick >= naechsteSuche) {
			suchen(mc);
		}
	}

	/** Beim Verlassen der Welt: Rahmen weg. */
	public void leeren() {
		boxen = List.of();
		BaritoneRahmen.zeigen(boxen);
	}

	// ---- Suchen --------------------------------------------------------------

	private void suchen(Minecraft mc) {
		naechsteSuche = tick + 20;
		if (mc.level != welt) {
			welt = mc.level;
			umgebung.clear();
		}
		BlockPos hier = mc.player.blockPosition();
		int r = Math.min(REICHWEITE_CHUNKS, mc.options.getEffectiveRenderDistance());
		int pcx = hier.getX() >> 4, pcz = hier.getZ() >> 4;
		List<BlockPos> kisten = new ArrayList<>();
		for (int cx = pcx - r; cx <= pcx + r; cx++) {
			for (int cz = pcz - r; cz <= pcz + r; cz++) {
				if (!mc.level.hasChunk(cx, cz)) {
					continue;
				}
				LevelChunk chunk = mc.level.getChunk(cx, cz);
				for (var be : chunk.getBlockEntities().values()) {
					if (be instanceof ChestBlockEntity) {
						kisten.add(be.getBlockPos());
					}
				}
			}
		}
		if (umgebung.size() > 5000) {
			umgebung.clear();
		}
		boxen = naechsteKisten(mc, kisten, hier);
		BaritoneRahmen.zeigen(boxen);
	}

	/** Die nächsten lohnenden Kisten als Rahmen: eine Doppelkiste in einem, dazu Kistenloren. */
	private List<BlockPos[]> naechsteKisten(Minecraft mc, List<BlockPos> kisten, BlockPos hier) {
		kisten.sort(Comparator.comparingDouble(p -> p.distSqr(hier)));
		List<BlockPos[]> ergebnis = new ArrayList<>();
		int neuAngeschaut = 0;
		String dim = dimension(mc);
		for (BlockPos p : kisten) {
			if (ergebnis.size() >= MAX_KISTEN) {
				break;
			}
			BlockState s = mc.level.getBlockState(p);
			// Nur natürliche Kisten: Kupferkisten bauen nur Spieler. Von einer Doppelkiste nur die linke Hälfte.
			if (!s.is(Blocks.CHEST) && !s.is(Blocks.TRAPPED_CHEST) || s.getValue(ChestBlock.TYPE) == ChestType.RIGHT) {
				continue;
			}
			BlockPos andere = s.getValue(ChestBlock.TYPE) == ChestType.SINGLE ? p : p.relative(ChestBlock.getConnectedDirection(s));
			if (istGeoeffnet(dim + " " + text(p)) || istGeoeffnet(dim + " " + text(andere))
					|| !offeneSeite(mc, p) && !offeneSeite(mc, andere)) {
				continue;
			}
			String art = umgebung.get(p);
			if (art == null) {
				// Höchstens 40 neue Kisten pro Sekunde genauer anschauen.
				if (neuAngeschaut++ >= 40) {
					continue;
				}
				art = umgebungVon(mc, p);
				umgebung.put(p, art);
			}
			if (!art.equals("-")) {
				ergebnis.add(new BlockPos[] {min(p, andere), max(p, andere)});
			}
		}
		for (MinecartChest lore : mc.level.getEntitiesOfClass(MinecartChest.class,
				mc.player.getBoundingBox().inflate(REICHWEITE_CHUNKS * 16), m -> m.isAlive())) {
			if (!istGeoeffnet("lore " + lore.getUUID())) {
				ergebnis.add(new BlockPos[] {lore.blockPosition(), lore.blockPosition()});
			}
		}
		ergebnis.sort(Comparator.comparingDouble(b -> b[0].distSqr(hier)));
		return ergebnis.size() > MAX_KISTEN ? List.copyOf(ergebnis.subList(0, MAX_KISTEN)) : ergebnis;
	}

	/**
	 * Was um eine Kiste steht: ein Bauwerk (z. B. "Netherfestung"), "" wenn keins bekannt ist, oder "-" für das Lager
	 * eines Spielers (mehr als 4 Kisten nah beieinander oder typische Spieler-Blöcke) – außer in Bauwerken, die kein
	 * Spieler baut. Es zählen nur Blöcke mit offener Seite, damit die Fakes des Anti-Xray-Schutzes nichts verraten.
	 */
	private String umgebungVon(Minecraft mc, BlockPos p) {
		Set<String> drumherum = new HashSet<>();
		int kisten = 0;
		boolean gebaut = false;
		for (BlockPos q : BlockPos.betweenClosed(p.offset(-5, -3, -5), p.offset(5, 3, 5))) {
			String n = name(mc.level.getBlockState(q));
			if (n.equals("air") || n.equals("cave_air") || n.equals("stone") || n.equals("deepslate") || n.equals("netherrack")
					|| !offeneSeite(mc, q)) {
				continue;
			}
			drumherum.add(n);
			if (n.equals("chest") || n.equals("trapped_chest")) {
				kisten++;
			}
			if (SPIELER_BAU.stream().anyMatch(n::endsWith)) {
				gebaut = true;
			}
		}
		String bauwerk = bauwerk(drumherum);
		return !sicheresBauwerk(bauwerk) && (gebaut || kisten > 4) ? "-" : bauwerk;
	}

	/** In welchem Bauwerk eine Kiste steht, an den Blöcken drumherum, oder "". */
	private static String bauwerk(Set<String> b) {
		if (b.contains("nether_bricks") || b.contains("nether_brick_fence")) {
			return "Netherfestung";
		}
		boolean obsidian = b.contains("crying_obsidian") || b.contains("obsidian");
		if (b.contains("gilded_blackstone") || b.contains("chiseled_polished_blackstone") || b.contains("polished_blackstone_bricks") && !obsidian) {
			return "Bastion";
		}
		if (b.contains("end_portal_frame") || b.contains("infested_stone_bricks") || b.contains("infested_mossy_stone_bricks")
				|| b.contains("infested_cracked_stone_bricks") || b.contains("infested_chiseled_stone_bricks")) {
			return "Festung";
		}
		if (b.contains("reinforced_deepslate") || b.contains("sculk_shrieker") || b.contains("sculk_catalyst")) {
			return "Antike Stadt";
		}
		// Endsiedlung und Endschiff: Purpur und Endsteinziegel (dort steht auch Buntglas, das sonst nach Spieler-Lager aussieht).
		if (b.contains("purpur_block") || b.contains("purpur_pillar") || b.contains("purpur_stairs") || b.contains("purpur_slab")
				|| b.contains("end_stone_bricks")) {
			return "Endsiedlung";
		}
		if (b.contains("trial_spawner") || b.contains("vault") || b.contains("chiseled_tuff_bricks") || b.contains("tuff_bricks")
				|| b.contains("waxed_copper_grate") || b.contains("waxed_oxidized_copper_grate")) {
			return "Prüfungskammer";
		}
		if (obsidian) {
			return "Ruinenportal";
		}
		if (b.contains("spawner") || b.contains("mossy_cobblestone")) {
			return "Verlies";
		}
		return "";
	}

	/** Bauwerke, die kein Spieler baut: Kisten dort sind Beute, auch mit Laterne oder Tür daneben. */
	private static boolean sicheresBauwerk(String b) {
		return b.equals("Netherfestung") || b.equals("Bastion") || b.equals("Festung") || b.equals("Antike Stadt")
				|| b.equals("Endsiedlung") || b.equals("Prüfungskammer");
	}

	/**
	 * Hat der Block eine offene Seite (Luft, Wasser, Pflanze, Glas …)? Der Anti-Xray-Schutz setzt seine Fakes nur dorthin,
	 * wo alle sechs Seiten zu sind. Eine Kiste daneben zählt nicht als offen (Kisten gehören zu den Fakes).
	 */
	private static boolean offeneSeite(Minecraft mc, BlockPos p) {
		for (Direction d : Direction.values()) {
			BlockState s = mc.level.getBlockState(p.relative(d));
			if (!s.canOcclude() && !(s.getBlock() instanceof AbstractChestBlock)) {
				return true;
			}
		}
		return false;
	}

	// ---- Geöffnete Kisten ----------------------------------------------------

	/** Ist ein Kistenmenü offen, zählt die Kiste (oder Kistenlore), auf die du schaust, als geöffnet – beide Hälften. */
	private void geoeffneteMerken(Minecraft mc) {
		if (!(mc.player.containerMenu instanceof ChestMenu)) {
			return;
		}
		boolean neu = false;
		if (mc.hitResult instanceof BlockHitResult treffer && treffer.getType() == HitResult.Type.BLOCK) {
			BlockPos p = treffer.getBlockPos();
			BlockState s = mc.level.getBlockState(p);
			if (!s.is(Blocks.CHEST) && !s.is(Blocks.TRAPPED_CHEST)) {
				return;
			}
			String dim = dimension(mc);
			neu = merken(dim + " " + text(p));
			if (s.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
				neu |= merken(dim + " " + text(p.relative(ChestBlock.getConnectedDirection(s))));
			}
		} else if (mc.hitResult instanceof EntityHitResult treffer && treffer.getEntity() instanceof MinecartChest lore) {
			neu = merken("lore " + lore.getUUID());
		}
		if (neu) {
			while (einstellungen.geoeffneteKisten.size() > MAX_GEMERKT) {
				geoeffnet().remove(einstellungen.geoeffneteKisten.remove(0));
			}
			einstellungen.speichern();
			naechsteSuche = 0; // sofort aus der Anzeige nehmen
		}
	}

	private Set<String> geoeffnet() {
		if (geoeffnet == null) {
			geoeffnet = new HashSet<>(einstellungen.geoeffneteKisten);
		}
		return geoeffnet;
	}

	private boolean istGeoeffnet(String schluessel) {
		return geoeffnet().contains(schluessel);
	}

	private boolean merken(String schluessel) {
		if (!geoeffnet().add(schluessel)) {
			return false;
		}
		einstellungen.geoeffneteKisten.add(schluessel);
		return true;
	}

	// ---- Kleinkram -----------------------------------------------------------

	private static String dimension(Minecraft mc) {
		return mc.level.dimension().toString();
	}

	private static String text(BlockPos p) {
		return p.getX() + " " + p.getY() + " " + p.getZ();
	}

	private static String name(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
	}

	private static BlockPos min(BlockPos a, BlockPos b) {
		return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
	}

	private static BlockPos max(BlockPos a, BlockPos b) {
		return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
	}

	private static void melden(LocalPlayer p, String text) {
		if (p != null) {
			p.sendOverlayMessage(Component.literal(text));
		}
	}
}
