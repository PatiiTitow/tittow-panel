package com.tittow.panel;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Materialliste der Burg (aus dem Bauplan, Datei assets/tittowpanel/burg/material.txt) und der Bericht
 * "was ist da, was fehlt" nach dem Durchzählen des Funk-Terminals.
 */
final class Material {
	private static Map<String, Map<String, Long>> liste;

	private Material() {
	}

	/** "sockel" bzw. "burg" -> (Block-Id -> Anzahl), in der Reihenfolge der Datei (die größten zuerst). */
	static Map<String, Map<String, Long>> liste() {
		if (liste != null) {
			return liste;
		}
		Map<String, Map<String, Long>> m = new LinkedHashMap<>();
		try (InputStream in = Material.class.getResourceAsStream("/assets/tittowpanel/burg/material.txt")) {
			if (in != null) {
				BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
				String zeile;
				while ((zeile = r.readLine()) != null) {
					String[] t = zeile.trim().split("\\s+");
					if (t.length == 3 && !t[0].startsWith("#")) {
						m.computeIfAbsent(t[0], k -> new LinkedHashMap<>()).put(t[1], Long.parseLong(t[2]));
					}
				}
			}
		} catch (Exception e) {
			System.err.println("[Tittow Panel] Materialliste nicht lesbar: " + e);
		}
		liste = m;
		return m;
	}

	/** Schreibt in den Chat: für Sockel und ganze Burg je Block "da / nötig" und was fehlt. */
	static void bericht(LocalPlayer p, Map<String, Long> lager) {
		Map<String, Long> da = new HashMap<>(lager);
		for (int i = 0; i < 36; i++) { // was du selbst dabei hast, zählt mit
			ItemStack s = p.getInventory().getItem(i);
			if (!s.isEmpty()) {
				da.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), (long) s.getCount(), Long::sum);
			}
		}
		Map<String, Map<String, Long>> l = liste();
		if (l.isEmpty()) {
			zeile(p, Component.literal("§cMaterialliste fehlt in der Mod."));
			return;
		}
		zeile(p, Component.literal("§d[Burg]§r Material-Check für den Sockel (Funk-Terminal + dein Inventar):"));
		for (Map.Entry<String, Map<String, Long>> teil : l.entrySet()) {
			if (!teil.getKey().equals("sockel")) {
				continue; // nur der Sockel
			}
			String name = "Sockel";
			int reicht = 0;
			long fehltSumme = 0;
			for (Map.Entry<String, Long> e : teil.getValue().entrySet()) {
				long hab = da.getOrDefault(e.getKey(), 0L);
				if (hab >= e.getValue()) {
					reicht++;
				} else {
					fehltSumme += e.getValue() - hab;
				}
			}
			zeile(p, Component.literal("§e§l" + name + ":§r§e " + reicht + " von " + teil.getValue().size() + " Sorten reichen"
					+ (fehltSumme > 0 ? ", es fehlen zusammen " + zahl(fehltSumme) + " Blöcke" : " – alles da!")));
			for (Map.Entry<String, Long> e : teil.getValue().entrySet()) {
				long noetig = e.getValue();
				long hab = da.getOrDefault(e.getKey(), 0L);
				Component block = blockName(e.getKey());
				if (hab >= noetig) {
					zeile(p, Component.literal(" §a✔ ").append(block).append(Component.literal("§a: " + zahl(hab) + " da, " + zahl(noetig) + " nötig")));
				} else {
					zeile(p, Component.literal(" §c✘ ").append(block).append(Component.literal("§7: " + zahl(hab) + " da, " + zahl(noetig)
							+ " nötig – §cfehlen " + zahl(noetig - hab))));
				}
			}
			// was nach dem Herstellen und Brennen (aus den Rohstoffen im Lager) wirklich noch fehlt
			Map<String, Long> rechnung = new HashMap<>(da);
			Map<String, Long> fehltRoh = new LinkedHashMap<>();
			for (Map.Entry<String, Long> e : teil.getValue().entrySet()) {
				Herstellen.aufloesen(e.getKey(), e.getValue(), rechnung, new HashMap<>(), new HashMap<>(), fehltRoh);
			}
			if (fehltRoh.isEmpty()) {
				zeile(p, Component.literal("§a§lMit Herstellen und Brennen reicht alles!§r §a„Sockel bauen“ kann los."));
			} else {
				zeile(p, Component.literal("§e§lAuch wenn der Bot alles herstellt und brennt, fehlen noch diese Rohstoffe:"));
				for (Map.Entry<String, Long> e : fehltRoh.entrySet()) {
					zeile(p, Component.literal(" §c✘ ").append(blockName(e.getKey())).append(Component.literal("§c: " + zahl(e.getValue()))));
				}
			}
		}
	}

	private static Component blockName(String id) {
		Identifier i = Identifier.tryParse(id);
		if (i != null) {
			Item item = BuiltInRegistries.ITEM.getValue(i);
			if (item != null) {
				return Component.translatable(item.getDescriptionId());
			}
		}
		return Component.literal(id);
	}

	private static String zahl(long n) {
		return String.format(Locale.GERMANY, "%,d", n);
	}

	private static void zeile(LocalPlayer p, Component c) {
		p.sendSystemMessage(c);
	}
}
