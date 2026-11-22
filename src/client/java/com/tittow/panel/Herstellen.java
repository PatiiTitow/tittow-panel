package com.tittow.panel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Sockel-Material herstellen: schaut nach, was für den Sockel fehlt, und macht es aus dem, was im Funk-Terminal liegt.
 * - Werkbank-Sachen (polierter Tiefenschiefer, Ziegel, Fliesen, polierter Andesit, Magmablock, Netherziegel-Block, Leuchtstein)
 *   stellt er in der Virtuellen Werkbank (Hotbar-Slot 7) her.
 * - Ofen-Sachen (Stein, rissige Tiefenschiefer-Ziegel, Netherziegel) bringt er zu deinen Strom-Öfen: oben in die Kisten
 *   mit dem Schild „reinlegen“, unten aus den Kisten mit „rausnehmen“ holt er das Gebrannte wieder ab.
 * Alles Fertige legt er ins Funk-Terminal. Was beim Start im Inventar war, bleibt im Inventar.
 */
final class Herstellen {
	/** Werkbank: 2x2-Muster in den Feldern oben links (Menü-Plätze 1, 2, 4, 5). Ofen: 1 rein, 1 raus. */
	record Rezept(String aus, int ausMenge, String[] muster, String ofenEin) {
		boolean ofen() {
			return ofenEin != null;
		}

		/** Zutat -> Anzahl pro Herstellung. */
		Map<String, Integer> zutaten() {
			Map<String, Integer> z = new LinkedHashMap<>();
			if (ofen()) {
				z.put(ofenEin, 1);
			} else {
				for (String m : muster) {
					z.merge(m, 1, Integer::sum);
				}
			}
			return z;
		}
	}

	private static final int[] FELDER = {1, 2, 4, 5};
	private static final Map<String, Rezept> REZEPTE = new HashMap<>();

	static {
		werkbank("polished_deepslate", 4, "cobbled_deepslate", "cobbled_deepslate", "cobbled_deepslate", "cobbled_deepslate");
		werkbank("deepslate_bricks", 4, "polished_deepslate", "polished_deepslate", "polished_deepslate", "polished_deepslate");
		werkbank("deepslate_tiles", 4, "deepslate_bricks", "deepslate_bricks", "deepslate_bricks", "deepslate_bricks");
		werkbank("polished_andesite", 4, "andesite", "andesite", "andesite", "andesite");
		werkbank("magma_block", 1, "magma_cream", "magma_cream", "magma_cream", "magma_cream");
		werkbank("nether_bricks", 1, "nether_brick", "nether_brick", "nether_brick", "nether_brick"); // Netherziegel-Block
		werkbank("glowstone", 1, "glowstone_dust", "glowstone_dust", "glowstone_dust", "glowstone_dust"); // Leuchtstein
		ofen("stone", "cobblestone");
		ofen("cracked_deepslate_bricks", "deepslate_bricks");
		ofen("nether_brick", "netherrack");
	}

	private static void werkbank(String aus, int menge, String a, String b, String c, String d) {
		String[] m = {"minecraft:" + a, "minecraft:" + b, "minecraft:" + c, "minecraft:" + d};
		REZEPTE.put("minecraft:" + aus, new Rezept("minecraft:" + aus, menge, m, null));
	}

	private static void ofen(String aus, String ein) {
		REZEPTE.put("minecraft:" + aus, new Rezept("minecraft:" + aus, 1, null, "minecraft:" + ein));
	}

	/** Stufe: Rohstoff = 0, sonst 1 + höchste Stufe der Zutaten (so wird in der richtigen Reihenfolge hergestellt). */
	private static int stufe(String id) {
		Rezept r = REZEPTE.get(id);
		if (r == null) {
			return 0;
		}
		int s = 0;
		for (String z : r.zutaten().keySet()) {
			s = Math.max(s, stufe(z));
		}
		return s + 1;
	}

	// ---- Zustand ------------------------------------------------------------------

	private enum Schritt { ZAEHLEN, OFEN_FUELLEN, CRAFTEN, OFEN_LEEREN, FERTIG }

	private enum TerminalZweck { ZAEHLEN, HOLEN, ABLEGEN }

	record Kiste(BlockPos pos, Direction seite) {
	}

	private final FunkTerminal terminal;
	private boolean an;
	private Schritt schritt;
	private TerminalZweck zweck;
	private String holtGerade;
	private int holVorher;
	private String ofenAuftrag; // welcher Ofen-Auftrag gerade in die Kisten gelegt wird
	private final Map<String, Long> craftRest = new LinkedHashMap<>();
	private final Map<String, Long> ofenRest = new LinkedHashMap<>();
	private final Map<String, Long> erwartet = new LinkedHashMap<>();
	private final Set<String> leer = new HashSet<>();      // im Terminal nicht (mehr) zu finden
	private final Set<String> aufgegeben = new HashSet<>(); // geht nicht, übersprungen
	private Map<String, Integer> start = Map.of();
	private final List<Kiste> rein = new ArrayList<>();
	private final List<Kiste> raus = new ArrayList<>();
	private BlockPos stand;
	private boolean geht;
	private int gehTicks;
	private int warte;
	private int leerenWarte;
	private int ohneFortschritt;
	private int werkbankFehler;
	private Map<String, Long> ziel = Map.of();
	private String titel = "den Sockel";
	/** Rohstoffe, die im Lager fehlen (nach dem Planen). */
	private final Map<String, Long> fehltRoh = new LinkedHashMap<>();
	private boolean fertigGemeldet;
	private String schlussHinweis = "";
	private int reinFehler;
	private int werkbankZu; // wie oft die Werkbank hintereinander nicht aufging
	private int platzFehler;
	private Werkbank werkbank;
	private KistenArbeit kiste;
	private final Map<String, Integer> vorher = new HashMap<>();

	Herstellen(FunkTerminal terminal) {
		this.terminal = terminal;
	}

	boolean laeuft() {
		return an;
	}

	/** Ist gerade ein Menü (Terminal, Werkbank, Kiste) offen bzw. wird eins bedient? Dann nicht essen/kämpfen. */
	boolean imMenue() {
		return terminal.laeuft() || werkbank != null || kiste != null;
	}

	String starten(Minecraft mc) {
		return starten(mc, Material.liste().getOrDefault("sockel", Map.of()), "den Sockel");
	}

	/** Wie starten, am Ende kommt noch "hinweis" in die Fertig-Meldung. */
	String starten(Minecraft mc, Map<String, Long> ziel, String titel, String hinweis) {
		schlussHinweis = hinweis;
		return starten(mc, ziel, titel);
	}

	/** Lief das letzte Herstellen bis zum Ende durch (nicht abgebrochen)? */
	boolean zuEndeGelaufen() {
		return fertigGemeldet;
	}

	/** Stellt her, was für "ziel" (Block-Id -> Anzahl) fehlt. titel z. B. "den Sockel" (für die Chat-Meldungen). */
	String starten(Minecraft mc, Map<String, Long> ziel, String titel) {
		this.ziel = ziel;
		this.titel = titel;
		fehltRoh.clear();
		fertigGemeldet = false;
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			return "";
		}
		an = true;
		craftRest.clear();
		ofenRest.clear();
		erwartet.clear();
		leer.clear();
		aufgegeben.clear();
		werkbank = null;
		kiste = null;
		geht = false;
		warte = 0;
		start = FunkTerminal.inventar(p);
		oefenSuchen(mc);
		// beim Laufen zu den Öfen nichts abbauen und nichts hinbauen
		BaritoneRahmen.einstellung("allowBreak", false);
		BaritoneRahmen.einstellung("allowPlace", false);
		schritt = Schritt.ZAEHLEN;
		zweck = TerminalZweck.ZAEHLEN;
		terminal.zaehlen(mc);
		return "Herstellen läuft: erst wird das Lager durchgezählt …";
	}

	void stoppen(Minecraft mc, String grund) {
		if (!an) {
			return;
		}
		an = false;
		terminal.abbrechen();
		werkbank = null;
		kiste = null;
		if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) {
			mc.player.closeContainer();
		}
		if (geht) {
			BaritoneRahmen.befehl("stop");
			geht = false;
		}
		BaritoneRahmen.einstellung("allowBreak", true);
		BaritoneRahmen.einstellung("allowPlace", true);
		if (grund != null && mc.player != null) {
			chat(mc.player, grund);
		}
	}

	// ---- Ablauf ---------------------------------------------------------------------

	void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (!an || p == null || mc.gameMode == null) {
			return;
		}
		if (terminal.laeuft()) {
			String m = terminal.tick(mc);
			if (m != null) {
				nachTerminal(mc, p, m);
			}
			return;
		}
		if (werkbank != null) {
			if (werkbank.tick(mc, p)) {
				nachWerkbank(p);
			}
			return;
		}
		if (kiste != null) {
			if (kiste.tick(mc, p)) {
				nachKiste(p);
			}
			return;
		}
		if (geht) {
			gehTicks++;
			if (amOfen(p)) {
				BaritoneRahmen.befehl("stop");
				geht = false;
			} else if (gehTicks > 20 * 90) {
				BaritoneRahmen.befehl("stop");
				geht = false;
				stoppen(mc, "Komme nicht zu den Öfen (90 Sekunden). Stell dich näher hin und starte nochmal.");
			}
			return;
		}
		if (warte > 0) {
			warte--;
			return;
		}
		switch (schritt) {
			case OFEN_FUELLEN -> ofenFuellen(mc, p);
			case CRAFTEN -> craften(mc, p);
			case OFEN_LEEREN -> ofenLeeren(mc, p);
			case FERTIG -> {
				stoppen(mc, null);
				if (aufgegeben.isEmpty()) {
					chat(p, "§aFertig mit Herstellen§r für " + titel + "." + schlussHinweis);
				} else {
					StringBuilder namen = new StringBuilder();
					for (String id : aufgegeben) {
						namen.append(namen.length() > 0 ? ", " : "").append(name(id).getString());
					}
					chat(p, "§eHerstellen beendet, aber übersprungen:§r " + namen
							+ ". Klick nochmal „Material prüfen“, dann versucht er den Rest nochmal.");
				}
				schlussHinweis = "";
				fertigGemeldet = true;
			}
			default -> {
			}
		}
	}

	private void nachTerminal(Minecraft mc, LocalPlayer p, String meldung) {
		switch (zweck) {
			case ZAEHLEN -> {
				if (!meldung.startsWith("OK")) {
					stoppen(mc, meldung);
					return;
				}
				planen(p);
			}
			case HOLEN -> {
				int jetzt = FunkTerminal.anzahl(p, item(holtGerade));
				if (jetzt <= holVorher) {
					leer.add(holtGerade); // nichts gekommen: im Lager ist keins mehr (oder kein Platz)
				}
			}
			case ABLEGEN -> {
				if (schritt == Schritt.FERTIG) {
					warte = 5;
				}
			}
		}
		warte = Math.max(warte, 5);
	}

	/** Rechnet aus, was hergestellt und gebrannt werden muss, damit alles für den Sockel da ist. */
	private void planen(LocalPlayer p) {
		Map<String, Long> lager = new HashMap<>(terminal.bestand());
		for (int i = 0; i < 36; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (!s.isEmpty()) {
				lager.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), (long) s.getCount(), Long::sum);
			}
		}
		Map<String, Long> sockel = ziel;
		for (Map.Entry<String, Long> e : sockel.entrySet()) {
			String id = e.getKey();
			long noetig = e.getValue();
			// so viel wie möglich mit dem, was da ist (halbieren, bis es ohne fehlende Rohstoffe geht)
			long lo = 0, hi = noetig;
			while (lo < hi) {
				long mitte = (lo + hi + 1) / 2;
				Map<String, Long> fehlt = new HashMap<>();
				aufloesen(id, mitte, new HashMap<>(lager), new HashMap<>(), new HashMap<>(), fehlt);
				if (fehlt.isEmpty()) {
					lo = mitte;
				} else {
					hi = mitte - 1;
				}
			}
			aufloesen(id, lo, lager, craftRest, ofenRest, new HashMap<>());
			if (lo < noetig) { // was dann noch an Rohstoffen fehlt
				aufloesen(id, noetig - lo, new HashMap<>(lager), new HashMap<>(), new HashMap<>(), fehltRoh);
			}
		}
		// Plan in den Chat
		chat(p, "Plan für " + titel + ":");
		if (craftRest.isEmpty() && ofenRest.isEmpty()) {
			chat(p, " Nichts herzustellen.");
		}
		for (Map.Entry<String, Long> e : craftRest.entrySet()) {
			Rezept r = REZEPTE.get(e.getKey());
			zeile(p, " §bWerkbank:§r ", e.getKey(), e.getValue() * r.ausMenge(), "");
		}
		for (Map.Entry<String, Long> e : ofenRest.entrySet()) {
			zeile(p, " §6Ofen:§r ", e.getKey(), e.getValue(), " (aus " + name(REZEPTE.get(e.getKey()).ofenEin()).getString() + ")");
		}
		for (Map.Entry<String, Long> e : fehltRoh.entrySet()) {
			zeile(p, " §cFehlt im Lager:§r ", e.getKey(), e.getValue(), " (bitte besorgen)");
		}
		if (!ofenRest.isEmpty() && (rein.isEmpty() || raus.isEmpty())) {
			chat(p, "§cKeine Ofen-Kisten gefunden§r (Schilder „reinlegen“ und „rausnehmen“ im Umkreis von 64 Blöcken). "
					+ "Brennen wird übersprungen, stell dich näher an die Öfen und starte nochmal.");
			for (String id : ofenRest.keySet()) {
				aufgegeben.add(id);
			}
		}
		// Reihenfolge: erst die Öfen füllen (Brennen dauert am längsten), dann die Werkbank, dann Gebranntes abholen
		schritt = craftRest.isEmpty() && ofenRest.isEmpty() ? Schritt.FERTIG : Schritt.OFEN_FUELLEN;
	}

	/** Verbraucht "n" von "id" aus dem Lager; fehlt etwas, wird es (wenn es ein Rezept gibt) aus den Zutaten hergestellt. */
	static void aufloesen(String id, long n, Map<String, Long> lager, Map<String, Long> craft, Map<String, Long> ofen,
			Map<String, Long> fehlt) {
		long nimm = Math.min(lager.getOrDefault(id, 0L), n);
		if (nimm > 0) {
			lager.merge(id, -nimm, Long::sum);
		}
		n -= nimm;
		if (n <= 0) {
			return;
		}
		Rezept r = REZEPTE.get(id);
		if (r == null) {
			fehlt.merge(id, n, Long::sum);
			return;
		}
		if (r.ofen()) {
			aufloesen(r.ofenEin(), n, lager, craft, ofen, fehlt);
			ofen.merge(id, n, Long::sum);
			return;
		}
		long mal = (n + r.ausMenge() - 1) / r.ausMenge();
		for (Map.Entry<String, Integer> z : r.zutaten().entrySet()) {
			aufloesen(z.getKey(), mal * z.getValue(), lager, craft, ofen, fehlt);
		}
		craft.merge(id, mal, Long::sum);
		long uebrig = mal * r.ausMenge() - n;
		if (uebrig > 0) {
			lager.merge(id, uebrig, Long::sum);
		}
	}

	// ---- Öfen füllen ------------------------------------------------------------------

	private void ofenFuellen(Minecraft mc, LocalPlayer p) {
		String aus = naechsterOfen();
		if (aus == null) {
			schritt = Schritt.CRAFTEN; // alles, was schon geht, ist in den Öfen: jetzt die Werkbank
			return;
		}
		Rezept r = REZEPTE.get(aus);
		Item ein = item(r.ofenEin());
		long rest = ofenRest.get(aus);
		int hab = FunkTerminal.anzahl(p, ein);
		if (hab == 0) {
			if (leer.contains(r.ofenEin())) {
				aufgeben(p, aus, name(r.ofenEin()).getString() + " ist im Lager alle");
				return;
			}
			holen(mc, p, r.ofenEin(), rest);
			return;
		}
		if (!amOfen(p)) {
			gehen(p);
			return;
		}
		vorher.put(r.ofenEin(), hab);
		ofenAuftrag = aus;
		kiste = new KistenArbeit(rein, ein, (int) Math.min(hab, rest), null);
	}

	// ---- Werkbank -------------------------------------------------------------------------

	private void craften(Minecraft mc, LocalPlayer p) {
		String aus = naechsterCraft();
		if (aus == null) { // Werkbank fertig (oder wartet auf Gebranntes): Gebranntes abholen
			ablegen(mc);
			schritt = Schritt.OFEN_LEEREN;
			leerenWarte = 0;
			ohneFortschritt = 0;
			return;
		}
		Rezept r = REZEPTE.get(aus);
		long rest = craftRest.get(aus);
		long moeglich = Long.MAX_VALUE;
		for (Map.Entry<String, Integer> z : r.zutaten().entrySet()) {
			moeglich = Math.min(moeglich, FunkTerminal.anzahl(p, item(z.getKey())) / z.getValue());
		}
		if (moeglich == 0) {
			// Zutaten holen: jede Zutat bekommt gleich viel Platz im Inventar
			int frei = freiePlaetze(p) - 3;
			int sorten = r.zutaten().size();
			int stapelJe = Math.max(1, Math.min(16, frei / sorten));
			for (Map.Entry<String, Integer> z : r.zutaten().entrySet()) {
				int hab = FunkTerminal.anzahl(p, item(z.getKey()));
				if (hab >= z.getValue()) {
					continue;
				}
				if (leer.contains(z.getKey())) {
					aufgeben(p, aus, name(z.getKey()).getString() + " ist im Lager alle");
					return;
				}
				if (frei < sorten) {
					platzMachen(mc); // erst Platz machen
					return;
				}
				long brauche = Math.min(rest * z.getValue(), (long) stapelJe * 64);
				holen(mc, p, z.getKey(), brauche);
				return;
			}
			return;
		}
		platzFehler = 0;
		vorher.put(aus, FunkTerminal.anzahl(p, item(aus)));
		werkbank = new Werkbank(r, Math.min(moeglich, rest));
	}

	private void nachWerkbank(LocalPlayer p) {
		Werkbank w = werkbank;
		werkbank = null;
		warte = 5;
		String aus = w.r.aus();
		long gemacht = (FunkTerminal.anzahl(p, item(aus)) - vorher.getOrDefault(aus, 0)) / w.r.ausMenge();
		if (w.fehler != null) {
			chat(p, w.fehler);
		}
		System.out.println("[Tittow Panel] Werkbank fertig: " + gemacht + "× " + aus + " hergestellt");
		if (w.nichtAuf && gemacht <= 0) {
			// nicht aufgegangen: das heißt nicht, dass es nicht geht. 10 Sekunden warten, dann nochmal.
			if (++werkbankZu >= 6) {
				werkbankZu = 0;
				aufgeben(p, aus, "die Virtuelle Werkbank geht nicht auf");
				return;
			}
			warte = 200;
			return;
		}
		werkbankZu = 0;
		if (gemacht <= 0) {
			if (++werkbankFehler >= 3) {
				werkbankFehler = 0;
				aufgeben(p, aus, "die Werkbank stellt es nicht her");
			}
			return;
		}
		werkbankFehler = 0;
		craftRest.put(aus, Math.max(0, craftRest.get(aus) - gemacht));
		if (naechsterOfen() != null) {
			schritt = Schritt.OFEN_FUELLEN; // z. B. Tiefenschiefer-Ziegel fertig: gleich in die Öfen (rissige Ziegel)
		}
	}

	// ---- Öfen leeren -------------------------------------------------------------------------

	private void ofenLeeren(Minecraft mc, LocalPlayer p) {
		Set<Item> ausgaben = new HashSet<>();
		for (Map.Entry<String, Long> e : erwartet.entrySet()) {
			if (e.getValue() > 0 && !aufgegeben.contains(e.getKey())) {
				ausgaben.add(item(e.getKey()));
			}
		}
		if (ausgaben.isEmpty()) {
			if (naechsterOfen() != null) {
				schritt = Schritt.OFEN_FUELLEN; // noch nicht alles in den Öfen (Kisten waren voll)
				return;
			}
			if (naechsterCraft() != null) {
				schritt = Schritt.CRAFTEN; // z. B. Netherziegel-Blöcke aus den gebrannten Netherziegeln
				return;
			}
			ablegen(mc);
			schritt = Schritt.FERTIG;
			return;
		}
		if (freiePlaetze(p) <= 3) {
			platzMachen(mc);
			return;
		}
		platzFehler = 0;
		if (!amOfen(p)) {
			gehen(p);
			return;
		}
		if (leerenWarte > 0) {
			leerenWarte--;
			return;
		}
		for (Item i : ausgaben) {
			String id = BuiltInRegistries.ITEM.getKey(i).toString();
			vorher.put(id, FunkTerminal.anzahl(p, i));
		}
		kiste = new KistenArbeit(raus, null, 0, ausgaben);
	}

	private void nachKiste(LocalPlayer p) {
		KistenArbeit k = kiste;
		kiste = null;
		warte = 5;
		if (k.fehler != null) {
			chat(p, k.fehler);
		}
		if (k.reinItem != null) { // Öfen gefüllt
			String ein = BuiltInRegistries.ITEM.getKey(k.reinItem).toString();
			long weg = vorher.getOrDefault(ein, 0) - FunkTerminal.anzahl(p, k.reinItem);
			String aus = ofenAuftrag;
			if (aus == null) {
				return;
			}
			if (weg <= 0) {
				if (++reinFehler >= 4) {
					reinFehler = 0;
					aufgeben(p, aus, "in die „reinlegen“-Kisten geht nichts hinein");
					return;
				}
				chat(p, "Die „reinlegen“-Kisten sind voll. Ich warte, bis die Öfen etwas weggebrannt haben.");
				schritt = Schritt.CRAFTEN; // inzwischen in der Werkbank weitermachen, danach wird geleert
				return;
			}
			reinFehler = 0;
			ofenRest.put(aus, Math.max(0, ofenRest.get(aus) - weg));
			erwartet.merge(aus, weg, Long::sum);
			chat(p, weg + " " + name(ein).getString() + " in die Öfen gelegt.");
			return;
		}
		// Öfen geleert
		long zusammen = 0;
		for (Item i : k.rausItems) {
			String id = BuiltInRegistries.ITEM.getKey(i).toString();
			long neu = FunkTerminal.anzahl(p, i) - vorher.getOrDefault(id, 0);
			if (neu > 0) {
				erwartet.put(id, Math.max(0, erwartet.getOrDefault(id, 0L) - neu));
				zusammen += neu;
			}
		}
		if (zusammen > 0) {
			ohneFortschritt = 0;
			leerenWarte = 100; // 5 Sekunden, dann wieder nachsehen
			if (naechsterOfen() != null) {
				schritt = Schritt.OFEN_FUELLEN; // jetzt ist wieder Platz in den Öfen
			}
		} else {
			leerenWarte = 200; // 10 Sekunden
			if (++ohneFortschritt >= 18) { // 3 Minuten nichts Neues
				chat(p, "Aus den Öfen kommt seit 3 Minuten nichts mehr. Was noch fehlt, bitte selbst nachsehen.");
				for (Map.Entry<String, Long> e : erwartet.entrySet()) {
					e.setValue(0L);
				}
				for (Map.Entry<String, Long> e : ofenRest.entrySet()) {
					e.setValue(0L);
				}
				ohneFortschritt = 0;
			}
		}
	}

	// ---- Hilfen ------------------------------------------------------------------------------------

	/** Ist für "id" noch etwas im Ofen (noch reinzulegen oder noch abzuholen)? */
	private boolean imOfen(String id) {
		return !aufgegeben.contains(id) && (ofenRest.getOrDefault(id, 0L) > 0 || erwartet.getOrDefault(id, 0L) > 0);
	}

	/** Muss für diesen Werkbank-Auftrag erst noch etwas gebrannt werden (auch über Zwischenstufen)? */
	private boolean wartetAufOfen(String id) {
		Rezept r = REZEPTE.get(id);
		if (r == null || r.ofen()) {
			return false;
		}
		for (String z : r.zutaten().keySet()) {
			if (imOfen(z)) {
				return true;
			}
			if (craftRest.getOrDefault(z, 0L) > 0 && !aufgegeben.contains(z) && wartetAufOfen(z)) {
				return true;
			}
		}
		return false;
	}

	/** Ofen-Auftrag, dessen Material schon fertig ist (nicht erst in der Werkbank oder im Ofen gemacht werden muss). */
	private String naechsterOfen() {
		for (Map.Entry<String, Long> e : ofenRest.entrySet()) {
			if (e.getValue() <= 0 || aufgegeben.contains(e.getKey())) {
				continue;
			}
			String ein = REZEPTE.get(e.getKey()).ofenEin();
			if (craftRest.getOrDefault(ein, 0L) > 0 && !aufgegeben.contains(ein) || imOfen(ein)) {
				continue; // Material dafür muss erst noch gemacht werden
			}
			return e.getKey();
		}
		return null;
	}

	/**
	 * Werkbank-Auftrag, der nicht auf den Ofen warten muss. Zuerst das, was danach noch in den Ofen kommt
	 * (z. B. Tiefenschiefer-Ziegel für die rissigen Ziegel), damit die Öfen schnell wieder zu tun haben.
	 */
	private String naechsterCraft() {
		String sonst = null;
		for (Map.Entry<String, Long> e : craftRest.entrySet()) {
			String id = e.getKey();
			if (e.getValue() <= 0 || aufgegeben.contains(id) || wartetAufOfen(id)) {
				continue;
			}
			if (fuerOfen(id)) {
				return id;
			}
			if (sonst == null) {
				sonst = id;
			}
		}
		return sonst;
	}

	/** Wird "id" (auch über Zwischenstufen) für einen noch offenen Ofen-Auftrag gebraucht? */
	private boolean fuerOfen(String id) {
		for (Map.Entry<String, Long> e : ofenRest.entrySet()) {
			if (e.getValue() > 0 && !aufgegeben.contains(e.getKey()) && braucht(REZEPTE.get(e.getKey()).ofenEin(), id)) {
				return true;
			}
		}
		return false;
	}

	/** Steckt "id" in "ziel" drin (ziel selbst oder eine Zutat davon, über die Werkbank)? */
	private static boolean braucht(String ziel, String id) {
		if (ziel.equals(id)) {
			return true;
		}
		Rezept r = REZEPTE.get(ziel);
		if (r == null || r.ofen()) {
			return false;
		}
		for (String z : r.zutaten().keySet()) {
			if (braucht(z, id)) {
				return true;
			}
		}
		return false;
	}

	private void aufgeben(LocalPlayer p, String id, String grund) {
		aufgegeben.add(id);
		chat(p, "§c" + name(id).getString() + " wird übersprungen:§r " + grund + ".");
	}

	private void holen(Minecraft mc, LocalPlayer p, String id, long menge) {
		if (freiePlaetze(p) <= 4) {
			platzMachen(mc); // Inventar fast voll: erst ablegen, sonst kommt nichts heraus und er denkt, das Lager ist leer
			return;
		}
		zweck = TerminalZweck.HOLEN;
		holtGerade = id;
		holVorher = FunkTerminal.anzahl(p, item(id));
		terminal.holen(mc, item(id), (int) Math.min(menge, 64L * 27));
	}

	private void ablegen(Minecraft mc) {
		zweck = TerminalZweck.ABLEGEN;
		terminal.ablegen(mc, start);
	}

	/** Platz machen, weil das Inventar zu voll ist. Hilft das mehrmals nicht, wird angehalten. */
	private void platzMachen(Minecraft mc) {
		if (++platzFehler > 3) {
			stoppen(mc, "Das Inventar ist zu voll (nur Sachen vom Start drin). Mach ein paar Plätze frei und starte nochmal.");
			return;
		}
		ablegen(mc);
	}

	private void gehen(LocalPlayer p) {
		if (stand == null) {
			return;
		}
		BaritoneRahmen.befehl("goto " + stand.getX() + " " + stand.getY() + " " + stand.getZ());
		geht = true;
		gehTicks = 0;
	}

	/** Sind alle Ofen-Kisten von hier aus zu erreichen? */
	private boolean amOfen(LocalPlayer p) {
		if (rein.isEmpty() && raus.isEmpty()) {
			return true;
		}
		Vec3 auge = p.getEyePosition();
		for (Kiste k : rein) {
			if (auge.distanceTo(Vec3.atCenterOf(k.pos())) > 4.8) {
				return false;
			}
		}
		for (Kiste k : raus) {
			if (auge.distanceTo(Vec3.atCenterOf(k.pos())) > 4.8) {
				return false;
			}
		}
		return true;
	}

	/** Sucht Schilder mit „reinlegen“ bzw. „rausnehmen“ und die Kiste, an der das Schild hängt. */
	private void oefenSuchen(Minecraft mc) {
		rein.clear();
		raus.clear();
		stand = null;
		LocalPlayer p = mc.player;
		int pcx = p.getBlockX() >> 4, pcz = p.getBlockZ() >> 4;
		Direction standSeite = null;
		for (int cx = pcx - 4; cx <= pcx + 4; cx++) {
			for (int cz = pcz - 4; cz <= pcz + 4; cz++) {
				if (!mc.level.hasChunk(cx, cz)) {
					continue;
				}
				LevelChunk chunk = mc.level.getChunk(cx, cz);
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (!(be instanceof SignBlockEntity schild)) {
						continue;
					}
					String text = (text(schild, SignTextSlot.FRONT) + " " + text(schild, SignTextSlot.BACK)).toLowerCase(Locale.ROOT);
					boolean istRein = text.contains("reinleg") || text.contains("rein legen");
					boolean istRaus = text.contains("rausnehm") || text.contains("raus nehm");
					if (!istRein && !istRaus) {
						continue;
					}
					BlockState st = mc.level.getBlockState(be.getBlockPos());
					Direction vorn = st.getBlock() instanceof WallSignBlock ? st.getValue(WallSignBlock.FACING) : Direction.UP;
					BlockPos kiste = kisteAm(mc, be.getBlockPos(), vorn);
					if (kiste == null) {
						continue;
					}
					Kiste k = new Kiste(kiste, vorn == Direction.UP ? Direction.UP : vorn);
					if (istRein && !enthaelt(rein, kiste)) {
						rein.add(k);
					} else if (istRaus && !enthaelt(raus, kiste)) {
						raus.add(k);
						standSeite = vorn.getAxis().isHorizontal() ? vorn : standSeite;
					}
				}
			}
		}
		if (!raus.isEmpty()) {
			BlockPos mitte = raus.get(raus.size() / 2).pos();
			stand = mitte.relative(standSeite != null ? standSeite : Direction.NORTH, 2);
		}
		chat(p, "Ofen-Kisten gefunden: " + rein.size() + "× „reinlegen“, " + raus.size() + "× „rausnehmen“.");
	}

	private static boolean enthaelt(List<Kiste> l, BlockPos pos) {
		for (Kiste k : l) {
			if (k.pos().equals(pos)) {
				return true;
			}
		}
		return false;
	}

	/** Die Kiste, an der das Schild hängt (dahinter), sonst eine Kiste direkt daneben. */
	private static BlockPos kisteAm(Minecraft mc, BlockPos schild, Direction vorn) {
		if (vorn.getAxis().isHorizontal()) {
			BlockPos hinten = schild.relative(vorn.getOpposite());
			if (mc.level.getBlockEntity(hinten) instanceof Container) {
				return hinten;
			}
		}
		for (Direction d : Direction.values()) {
			BlockPos n = schild.relative(d);
			if (mc.level.getBlockEntity(n) instanceof Container) {
				return n;
			}
		}
		return null;
	}

	private static String text(SignBlockEntity s, SignTextSlot seite) {
		StringBuilder t = new StringBuilder();
		for (Component c : s.getText(seite).getMessages(false)) {
			t.append(c.getString()).append(' ');
		}
		return t.toString();
	}

	static Item item(String id) {
		return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
	}

	private static Component name(String id) {
		return new ItemStack(item(id)).getHoverName();
	}

	private static int freiePlaetze(LocalPlayer p) {
		int n = 0;
		for (int i = Inventory.getSelectionSize(); i < 36; i++) {
			if (p.getInventory().getItem(i).isEmpty()) {
				n++;
			}
		}
		return n;
	}

	private static void zeile(LocalPlayer p, String vorne, String id, long menge, String hinten) {
		p.sendSystemMessage(Component.literal("§d[Burg]§r" + vorne).append(name(id))
				.append(Component.literal(": " + String.format(Locale.GERMANY, "%,d", menge) + hinten)));
	}

	private static void chat(LocalPlayer p, String text) {
		p.sendSystemMessage(Component.literal("§d[Burg]§r " + text));
	}

	private static boolean menueOffen(Minecraft mc, LocalPlayer p) {
		return p.containerMenu != p.inventoryMenu && mc.gui.screen() instanceof AbstractContainerScreen<?>;
	}

	/** Ein Platz im oberen Inventar (Menü-Nummer) mit dieser Sorte, der größte Stapel zuerst. -1 = keiner. */
	private static int inventarPlatz(AbstractContainerMenu menu, LocalPlayer p, Item item) {
		int beste = -1, besteMenge = 0;
		for (Slot s : menu.slots) {
			int c = s.getContainerSlot();
			if (s.container == p.getInventory() && (c >= Inventory.getSelectionSize() && c < 36 || c == Versorgung.ERDE_SLOT || c == BurgBau.GERUEST_SLOT)
					&& s.getItem().is(item) && s.getItem().getCount() > besteMenge) {
				beste = s.index;
				besteMenge = s.getItem().getCount();
			}
		}
		return beste;
	}

	private static int leererPlatz(AbstractContainerMenu menu, LocalPlayer p) {
		for (Slot s : menu.slots) {
			int c = s.getContainerSlot();
			if (s.container == p.getInventory() && c >= Inventory.getSelectionSize() && c < 36 && s.getItem().isEmpty()) {
				return s.index;
			}
		}
		return -1;
	}

	private static void klick(Minecraft mc, LocalPlayer p, AbstractContainerMenu menu, int slot, ContainerInput art) {
		mc.gameMode.handleContainerInput(menu.containerId, slot, 0, art, p);
	}

	// ---- Virtuelle Werkbank ------------------------------------------------------------------

	/** Öffnet die Virtuelle Werkbank (Slot 7), legt die Zutaten ins 2x2-Feld und nimmt mit Shift-Klick alles heraus. */
	private static final class Werkbank {
		static final int WERKBANK_SLOT = 6; // Slot 7
		final Rezept r;
		final long mal;
		String fehler;
		boolean nichtAuf; // gar nicht aufgegangen: kein Grund aufzugeben, später nochmal
		private int schritt; // 0 öffnen, 1 warten, 2 füllen/herstellen, 3 leeren, 4 zu
		private int warte;
		private int versuche;
		private int alterSlot = -1;
		private int ausVorher = -1;
		private int runden;
		private int quelle = -1;

		Werkbank(Rezept r, long mal) {
			this.r = r;
			this.mal = mal;
		}

		/** true = fertig. */
		boolean tick(Minecraft mc, LocalPlayer p) {
			if (warte > 0) {
				warte--;
				if (schritt == 1 && warte == 0) {
					System.out.println("[Tittow Panel] Werkbank: nicht aufgegangen (Versuch " + (versuche + 1) + "), offen ist: "
							+ (p.containerMenu == p.inventoryMenu ? "nichts" : p.containerMenu.getClass().getSimpleName()
									+ " mit " + p.containerMenu.slots.size() + " Plätzen"));
					if (++versuche >= 5) {
						fehler = "Die Virtuelle Werkbank (Slot 7) geht gerade nicht auf – ich versuche es gleich nochmal.";
						nichtAuf = true;
						return zu(p);
					}
					schritt = 0;
					warte = 20; // kurz warten, manche Sachen haben eine Abklingzeit
					return false;
				}
				if (schritt != 1) {
					return false;
				}
			}
			switch (schritt) {
				case 0 -> {
					if (mc.gui.screen() != null) {
						if (p.containerMenu != p.inventoryMenu) {
							p.closeContainer();
						}
						warte = 5;
						return false;
					}
					if (alterSlot < 0) {
						alterSlot = p.getInventory().getSelectedSlot();
					}
					if (p.getInventory().getItem(WERKBANK_SLOT).isEmpty()) {
						fehler = "In Slot 7 liegt keine Virtuelle Werkbank.";
						return zu(p);
					}
					p.getInventory().setSelectedSlot(WERKBANK_SLOT);
					FunkTerminal.nichtAufKistenZielen(mc, p); // nicht auf Ofen, Kiste usw. zielen, sonst geht das auf statt der Werkbank
					mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
					schritt = 1;
					warte = 60;
				}
				case 1 -> {
					if (istWerkbank(p.containerMenu) && menueOffen(mc, p)) {
						schritt = 2;
						warte = 8;
						ausVorher = FunkTerminal.anzahl(p, item(r.aus()));
						System.out.println("[Tittow Panel] Werkbank offen (" + p.containerMenu.getClass().getSimpleName() + ", "
								+ p.containerMenu.slots.size() + " Plätze), stelle " + mal + "× " + r.aus() + " her");
					}
				}
				case 2 -> {
					AbstractContainerMenu menu = p.containerMenu;
					if (!istWerkbank(menu)) {
						System.out.println("[Tittow Panel] Werkbank: Menü ist plötzlich zu");
						return zu(p);
					}
					// hängt etwas an der Maus? zurück an seinen Platz bzw. in einen leeren Platz
					if (!menu.getCarried().isEmpty()) {
						int ziel = quelle >= 0 && menu.getSlot(quelle).getItem().isEmpty() ? quelle : leererPlatz(menu, p);
						quelle = -1;
						if (ziel < 0) {
							schritt = 3;
							return false;
						}
						klick(mc, p, menu, ziel, ContainerInput.PICKUP);
						warte = 3;
						return false;
					}
					long gemacht = (FunkTerminal.anzahl(p, item(r.aus())) - ausVorher) / r.ausMenge();
					if (gemacht >= mal || runden > 40) { // Platz ist kein Problem: was ins Feld kommt, macht im Inventar Platz für das Ergebnis
						schritt = 3;
						return false;
					}
					// Felder auffüllen: je Zutat einen Stapel an die Maus nehmen und GLEICHMÄSSIG auf ihre Felder verteilen
					// (wie Ziehen mit gedrückter Maustaste). Sonst landet alles im ersten Feld und die anderen bleiben leer.
					long sollJeFeld = Math.min(64, mal - gemacht);
					for (Item zutat : zutatenItems()) {
						List<Integer> felder = felderFuer(zutat);
						boolean leeresFeld = false, mehr = false;
						for (int feld : felder) {
							ItemStack drin = menu.getSlot(feld).getItem();
							leeresFeld |= drin.isEmpty();
							mehr |= drin.isEmpty() || drin.is(zutat) && drin.getCount() < sollJeFeld;
						}
						if (!mehr) {
							continue;
						}
						int q = inventarPlatz(menu, p, zutat);
						if (q < 0) {
							if (leeresFeld) {
								System.out.println("[Tittow Panel] Werkbank: keine " + BuiltInRegistries.ITEM.getKey(zutat)
										+ " mehr im Inventar für alle Felder");
								schritt = 3; // Zutat alle
								return false;
							}
							continue; // nicht ganz voll, aber es geht trotzdem
						}
						quelle = q;
						klick(mc, p, menu, q, ContainerInput.PICKUP); // Stapel an die Maus
						int id = menu.containerId;
						mc.gameMode.handleContainerInput(id, -999, 0, ContainerInput.QUICK_CRAFT, p); // Verteilen beginnen
						for (int feld : felder) {
							mc.gameMode.handleContainerInput(id, feld, 1, ContainerInput.QUICK_CRAFT, p); // über das Feld ziehen
						}
						mc.gameMode.handleContainerInput(id, -999, 2, ContainerInput.QUICK_CRAFT, p); // loslassen: gleichmäßig verteilt
						warte = 4;
						return false;
					}
					// alle 4 Felder belegt: herstellen (Shift-Klick auf das Ergebnis)
					klick(mc, p, menu, 0, ContainerInput.QUICK_MOVE);
					runden++;
					warte = 6;
				}
				case 3 -> {
					AbstractContainerMenu menu = p.containerMenu;
					if (istWerkbank(menu)) {
						if (!menu.getCarried().isEmpty()) {
							int ziel = leererPlatz(menu, p);
							if (ziel >= 0) {
								klick(mc, p, menu, ziel, ContainerInput.PICKUP);
								warte = 3;
								return false;
							}
						}
						for (int feld = 1; feld <= 9; feld++) {
							if (!menu.getSlot(feld).getItem().isEmpty()) {
								klick(mc, p, menu, feld, ContainerInput.QUICK_MOVE); // Rest zurück ins Inventar
								warte = 3;
								return false;
							}
						}
					}
					return zu(p);
				}
				default -> {
					return zu(p);
				}
			}
			return false;
		}

		/** Die verschiedenen Zutaten des Rezepts (meist nur eine, z. B. 4× Andesit). */
		private List<Item> zutatenItems() {
			List<Item> l = new ArrayList<>();
			for (String id : r.muster()) {
				Item i = item(id);
				if (!l.contains(i)) {
					l.add(i);
				}
			}
			return l;
		}

		/** Die Felder (Menü-Plätze), in die diese Zutat gehört. */
		private List<Integer> felderFuer(Item zutat) {
			List<Integer> l = new ArrayList<>();
			for (int k = 0; k < 4; k++) {
				if (item(r.muster()[k]) == zutat) {
					l.add(FELDER[k]);
				}
			}
			return l;
		}

		/** Die Werkbank: ein Werkbank-Menü (Ergebnis + 3x3-Feld + Inventar = 46 Plätze). */
		private static boolean istWerkbank(AbstractContainerMenu menu) {
			return menu instanceof CraftingMenu || menu.slots.size() == 46;
		}

		private boolean zu(LocalPlayer p) {
			if (p.containerMenu != p.inventoryMenu) {
				p.closeContainer();
			}
			if (alterSlot >= 0) {
				p.getInventory().setSelectedSlot(alterSlot);
			}
			return true;
		}
	}

	// ---- Ofen-Kisten -----------------------------------------------------------------------

	/** Geht der Reihe nach an die Kisten: reinlegen (gleichmäßig verteilt) oder Gebranntes herausnehmen. */
	private static final class KistenArbeit {
		final List<Kiste> kisten;
		final Item reinItem;     // != null: hineinlegen
		final Set<Item> rausItems; // != null: herausnehmen
		String fehler;
		private int menge;       // beim Reinlegen: so viele insgesamt
		private int idx;
		private int schritt;     // 0 öffnen, 1 warten, 2 arbeiten
		private int warte;
		private int versuche;
		private int proKiste;
		private int inDieseKiste;
		private int vorKlick = -1;
		private int alterSlot = -1;

		KistenArbeit(List<Kiste> kisten, Item reinItem, int menge, Set<Item> rausItems) {
			this.kisten = kisten;
			this.reinItem = reinItem;
			this.menge = menge;
			this.rausItems = rausItems;
			this.proKiste = kisten.isEmpty() ? 0 : (menge + kisten.size() - 1) / kisten.size();
		}

		boolean tick(Minecraft mc, LocalPlayer p) {
			if (warte > 0) {
				warte--;
				if (schritt == 1 && warte == 0) {
					if (++versuche >= 3) {
						fehler = "Die Kiste bei " + kisten.get(idx).pos().toShortString() + " geht nicht auf, übersprungen.";
						return naechsteKiste(p);
					}
					schritt = 0;
				}
				if (schritt != 1) {
					return false;
				}
			}
			if (idx >= kisten.size()) {
				return fertig(p);
			}
			Kiste k = kisten.get(idx);
			switch (schritt) {
				case 0 -> {
					if (mc.gui.screen() != null) {
						if (p.containerMenu != p.inventoryMenu) {
							p.closeContainer();
						}
						warte = 5;
						return false;
					}
					if (alterSlot < 0) {
						alterSlot = p.getInventory().getSelectedSlot();
					}
					p.getInventory().setSelectedSlot(0); // Schwert in der Hand, damit nichts anderes benutzt wird
					Vec3 mitte = Vec3.atCenterOf(k.pos()).add(k.seite().getStepX() * 0.5, k.seite().getStepY() * 0.5, k.seite().getStepZ() * 0.5);
					mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(mitte, k.seite(), k.pos(), false));
					schritt = 1;
					warte = 40;
				}
				case 1 -> {
					if (menueOffen(mc, p)) {
						schritt = 2;
						warte = 6;
						inDieseKiste = 0;
						vorKlick = -1;
					}
				}
				case 2 -> {
					AbstractContainerMenu menu = p.containerMenu;
					if (!menueOffen(mc, p)) {
						return naechsteKiste(p);
					}
					if (reinItem != null) {
						int jetzt = FunkTerminal.anzahl(p, reinItem);
						if (vorKlick >= 0) {
							int weg = vorKlick - jetzt;
							vorKlick = -1;
							if (weg <= 0) {
								return naechsteKiste(p); // Kiste voll
							}
							inDieseKiste += weg;
							menge -= weg;
						}
						if (menge <= 0 || inDieseKiste >= proKiste && idx < kisten.size() - 1) {
							return naechsteKiste(p);
						}
						int q = inventarPlatz(menu, p, reinItem);
						if (q < 0) {
							return fertig(p);
						}
						vorKlick = jetzt;
						klick(mc, p, menu, q, ContainerInput.QUICK_MOVE);
						warte = 4;
					} else {
						if (freiePlaetze(p) <= 2) {
							return fertig(p);
						}
						for (Slot s : menu.slots) {
							if (s.container != p.getInventory() && !s.getItem().isEmpty() && rausItems.contains(s.getItem().getItem())) {
								klick(mc, p, menu, s.index, ContainerInput.QUICK_MOVE);
								warte = 4;
								return false;
							}
						}
						return naechsteKiste(p);
					}
				}
				default -> {
					return fertig(p);
				}
			}
			return false;
		}

		private boolean naechsteKiste(LocalPlayer p) {
			if (p.containerMenu != p.inventoryMenu) {
				p.closeContainer();
			}
			idx++;
			schritt = 0;
			versuche = 0;
			warte = 4;
			if (idx >= kisten.size()) {
				return fertig(p);
			}
			// beim Reinlegen: den Rest gleichmäßig auf die übrigen Kisten verteilen
			if (reinItem != null) {
				int uebrig = kisten.size() - idx;
				proKiste = (menge + uebrig - 1) / uebrig;
			}
			return false;
		}

		private boolean fertig(LocalPlayer p) {
			if (p.containerMenu != p.inventoryMenu) {
				p.closeContainer();
			}
			if (alterSlot >= 0) {
				p.getInventory().setSelectedSlot(alterSlot);
			}
			return true;
		}
	}
}
