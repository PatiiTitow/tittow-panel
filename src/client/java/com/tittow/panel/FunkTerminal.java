package com.tittow.panel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Benutzt das Funk-Terminal (Hotbar-Slot 8) beim Ebnen: Baritone kurz anhalten, Terminal öffnen, dann entweder
 * Erde herausholen oder neu aufgesammelte Sachen hineinlegen, Terminal zu, Baritone weiter.
 * Das Terminal ist ein Menü wie eine Kiste, deshalb geht alles mit normalen Inventar-Klicks.
 * Was beim Start des Ebnens im Inventar war, wird nie hineingelegt.
 */
final class FunkTerminal {
	/** Slot 8 in der Hotbar (gezählt ab 0). */
	private static final int TERMINAL_SLOT = 7;
	/** So viel Erde bleibt beim Ablegen im Inventar, für das Löcherstopfen. */
	private static final int ERDE_BEHALTEN = 640;
	/** Beim Holen nur bis zu dieser Menge Erde nehmen (weniger als beim Ablegen bleibt, sonst legt er sie gleich wieder rein). */
	private static final int ERDE_HOLEN_BIS = 512;
	/** Beim Holen immer so viele Plätze im Inventar frei lassen (für Sachen, die beim Abbauen dazukommen). */
	private static final int FREI_LASSEN = 3;
	private static final String[] WEITER = {"weiter", "nächste", "naechste", "next", "→", "»", ">>"};

	/** 0 = aus, 1 = Terminal öffnen, 2 = auf das Menü warten, 3 = Sachen nehmen/ablegen, 4 = zumachen. */
	private int schritt;
	private boolean ablegen; // false = Erde holen, true = Sachen hineinlegen
	private boolean zaehlen; // nur nachschauen: alle Seiten durchgehen und zählen, was im Lager ist
	/** Beim Zählen: Sorte (z. B. minecraft:dirt) und Anzahl im Lager. */
	private final Map<String, Long> bestand = new HashMap<>();
	private final Set<String> gezaehlteSeiten = new HashSet<>();
	private Map<String, Integer> start = Map.of();
	private int warte;
	private int versuche;
	private int seiten;
	private int klicks;
	private int alterSlot;
	private int erdeVorher; // wie viel vom Gesuchten vor dem Holen im Inventar war
	/** Beim Holen: was geholt wird und bis zu welcher Menge im Inventar. */
	private Item zielItem = Items.DIRT;
	private int zielMenge = ERDE_HOLEN_BIS;
	private int vorherBelegt;
	private int erdeBeimKlick;
	private int ohneWirkung;
	private boolean aufnehmen; // Klick-Art, falls Shift-Klick im Terminal nicht geht
	// beim Ablegen: welcher Platz zuletzt angeklickt wurde und wie viel dort lag
	private int letzterSlot = -1;
	private int letzteMenge;
	private int zurueckSlot = -1;
	private final Set<Integer> gehtNicht = new HashSet<>();
	private String ergebnis;

	boolean laeuft() {
		return schritt != 0;
	}

	/** Beim Stoppen des Ebnens: nicht weiter klicken. */
	void abbrechen() {
		schritt = 0;
		zaehlen = false;
	}

	/** Wird gerade nur gezählt (Material prüfen)? */
	boolean zaehltGerade() {
		return schritt != 0 && zaehlen;
	}

	/** Erde aus dem Terminal holen: bis etwa 8 Stacks, ein paar Plätze im Inventar bleiben frei. */
	void erdeHolen(Minecraft mc) {
		zielItem = Items.DIRT;
		zielMenge = ERDE_HOLEN_BIS;
		beginnen(mc, false, Map.of());
	}

	/** Eine bestimmte Sorte aus dem Terminal holen, bis zu "menge" Stück mehr als jetzt (ein paar Plätze bleiben frei). */
	void holen(Minecraft mc, Item item, int menge) {
		if (mc.player == null) {
			return;
		}
		zielItem = item;
		zielMenge = anzahl(mc.player, item) + menge;
		beginnen(mc, false, Map.of());
	}

	/**
	 * Vor dem Rechtsklick (Funk-Terminal, Virtuelle Werkbank): Zielt der Bot gerade auf etwas, das man öffnen kann
	 * (Ofen, Kiste, Fass, Trichter, Schild, Werkbank …), würde das aufgehen statt dem Gegenstand. Dann schaut er
	 * auf den Boden, und wenn auch da so etwas ist, dreht er sich, bis vor ihm nichts zum Öffnen ist.
	 */
	static void nichtAufKistenZielen(Minecraft mc, LocalPlayer p) {
		if (!zieltAufOeffenbares(mc, p)) {
			return; // passt so
		}
		p.setXRot(90f); // auf den Boden
		p.xRotO = 90f;
		for (int i = 0; i < 8 && zieltAufOeffenbares(mc, p); i++) {
			p.setXRot(i < 4 ? 0f : 60f);
			p.xRotO = p.getXRot();
			p.setYRot(p.getYRot() + 90f);
			p.yRotO = p.getYRot();
		}
	}

	private static boolean zieltAufOeffenbares(Minecraft mc, LocalPlayer p) {
		HitResult h = p.pick(6.0, 1.0f, false);
		if (h.getType() != HitResult.Type.BLOCK || !(h instanceof BlockHitResult b) || mc.level == null) {
			return false;
		}
		return mc.level.getBlockEntity(b.getBlockPos()) != null
				|| mc.level.getBlockState(b.getBlockPos()).getMenuProvider(mc.level, b.getBlockPos()) != null;
	}

	/** Wie viele von dieser Sorte im Inventar sind (Hotbar und oben). */
	static int anzahl(LocalPlayer p, Item item) {
		int n = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(item)) {
				n += s.getCount();
			}
		}
		return n;
	}

	/** Alles hineinlegen, was seit dem Start dazugekommen ist (startInventar = Sachen beim Start). */
	void ablegen(Minecraft mc, Map<String, Integer> startInventar) {
		beginnen(mc, true, startInventar == null ? Map.of() : startInventar);
	}

	/** Nur nachschauen: alle Seiten im Terminal durchgehen und zählen. Ergebnis danach mit bestand(). */
	void zaehlen(Minecraft mc) {
		zaehlen = true;
		bestand.clear();
		gezaehlteSeiten.clear();
		beginnen(mc, false, Map.of());
	}

	/** Was beim letzten Zählen im Lager war (Sorte -> Anzahl). */
	Map<String, Long> bestand() {
		return bestand;
	}

	private void beginnen(Minecraft mc, boolean ablegen, Map<String, Integer> startInventar) {
		LocalPlayer p = mc.player;
		if (p == null) {
			return;
		}
		if (!zaehlen) {
			BaritoneRahmen.befehl("pause"); // Baritone soll nicht weiterlaufen, während das Menü offen ist
		}
		this.ablegen = ablegen;
		this.start = startInventar;
		schritt = 1;
		warte = 10;
		versuche = 0;
		seiten = 0;
		klicks = 0;
		ohneWirkung = 0;
		aufnehmen = false;
		letzterSlot = -1;
		zurueckSlot = -1;
		gehtNicht.clear();
		alterSlot = p.getInventory().getSelectedSlot();
		erdeVorher = anzahl(p, zielItem);
		vorherBelegt = belegt(p);
		ergebnis = null;
	}

	/** Jeden Tick aufrufen, solange laeuft(). Gibt am Ende eine Meldung zurück ("OK: ..." = geklappt), sonst null. */
	String tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null) {
			schritt = 0;
			return null;
		}
		if (warte > 0) {
			warte--;
			if (schritt == 2 && warte == 0) {
				return menuNichtDa(mc);
			}
			if (schritt != 2) {
				return null;
			}
		}
		switch (schritt) {
			case 1 -> oeffnen(mc, p);
			case 2 -> {
				if (p.containerMenu != p.inventoryMenu && mc.gui.screen() instanceof AbstractContainerScreen<?>) {
					schritt = 3;
					warte = 10; // kurz warten, bis der Server die Sachen ins Menü geschickt hat
				}
			}
			case 3 -> {
				AbstractContainerMenu menu = p.containerMenu;
				if (menu == p.inventoryMenu || !(mc.gui.screen() instanceof AbstractContainerScreen<?>)) {
					schritt = 4; // Menü ist zu (z. B. selbst mit Esc zugemacht)
				} else if (zaehlen) {
					zaehlenSchritt(mc, p, menu);
				} else if (ablegen) {
					ablegenSchritt(mc, p, menu);
				} else {
					holenSchritt(mc, p, menu);
				}
			}
			case 4 -> {
				return fertig(p);
			}
			default -> schritt = 0;
		}
		return null;
	}

	private void oeffnen(Minecraft mc, LocalPlayer p) {
		if (mc.gui.screen() != null) {
			if (mc.gui.screen() instanceof AbstractContainerScreen<?>) {
				p.closeContainer();
			}
			warte = 5;
			return;
		}
		Inventory inv = p.getInventory();
		int slot = terminalSuchen(inv);
		if (slot < 0) {
			ende("Kein Funk-Terminal gefunden. Leg es in Slot 8 der Hotbar.");
			return;
		}
		if (slot >= Inventory.getSelectionSize()) {
			// liegt oben im Inventar (z. B. von Baritone verschoben): zurück auf Slot 8 tauschen
			mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, slot, TERMINAL_SLOT, ContainerInput.SWAP, p);
			warte = 5;
			return;
		}
		inv.setSelectedSlot(slot);
		nichtAufKistenZielen(mc, p); // sonst geht der Ofen oder die Kiste auf statt dem Terminal
		mc.gameMode.useItem(p, InteractionHand.MAIN_HAND); // wie Rechtsklick in die Luft
		schritt = 2;
		warte = 60;
	}

	private String menuNichtDa(Minecraft mc) {
		if (++versuche < 3) {
			schritt = 1;
			warte = 10;
			return null;
		}
		ende("Das Funk-Terminal ist nicht aufgegangen.");
		return fertig(mc.player);
	}

	// ---- Erde holen ----------------------------------------------------------

	private void holenSchritt(Minecraft mc, LocalPlayer p, AbstractContainerMenu menu) {
		int jetzt = anzahl(p, zielItem);
		if (!menu.getCarried().isEmpty()) { // hängt etwas an der Maus: in einen freien Inventar-Platz legen
			int frei = freierPlatz(menu, p);
			if (frei < 0) {
				schritt = 4;
				return;
			}
			klick(mc, p, menu, frei, ContainerInput.PICKUP);
			return;
		}
		if (klicks > 0 && !aufnehmen) {
			// hat der letzte Shift-Klick etwas gebracht? Sonst auf normales Anklicken umstellen
			ohneWirkung = jetzt > erdeBeimKlick ? 0 : ohneWirkung + 1;
			if (ohneWirkung >= 2) {
				aufnehmen = true;
			}
		}
		if (freiePlaetze(p) <= FREI_LASSEN || jetzt >= zielMenge || klicks >= 60) {
			schritt = 4; // genug Erde, oder das Inventar würde zu voll
			return;
		}
		for (Slot s : menu.slots) {
			if (s.container != p.getInventory() && s.getItem().is(zielItem)) {
				erdeBeimKlick = jetzt;
				klick(mc, p, menu, s.index, aufnehmen ? ContainerInput.PICKUP : ContainerInput.QUICK_MOVE);
				return;
			}
		}
		// auf dieser Seite keine Erde: nächste Seite suchen
		if (seiten < 30) {
			for (Slot s : menu.slots) {
				if (istWeiterKnopf(menu, p, s)) {
					seiten++;
					klick(mc, p, menu, s.index, ContainerInput.PICKUP);
					warte = 10;
					return;
				}
			}
		}
		schritt = 4;
	}

	// ---- Zählen (Material prüfen) -------------------------------------------------

	/** Zählt die aktuelle Seite, dann weiter zur nächsten. Kommt eine Seite doppelt oder gibt es keine weitere: fertig. */
	private void zaehlenSchritt(Minecraft mc, LocalPlayer p, AbstractContainerMenu menu) {
		StringBuilder sig = new StringBuilder();
		for (Slot s : menu.slots) {
			if (s.container != p.getInventory()) {
				ItemStack st = s.getItem();
				sig.append(s.index).append(':').append(st.isEmpty() ? "-" : schluessel(st) + "x" + st.getCount() + loreText(st)).append(';');
			}
		}
		if (!gezaehlteSeiten.add(sig.toString())) {
			schritt = 4; // diese Seite kennen wir schon: alles gezählt
			return;
		}
		boolean protokoll = gezaehlteSeiten.size() <= 2; // die ersten Seiten ins Log schreiben (zum Nachsehen, wie das Terminal aussieht)
		for (Slot s : menu.slots) {
			ItemStack st = s.getItem();
			if (s.container == p.getInventory() || st.isEmpty() || inKnopfReihe(menu, p, s)) { // unterste Reihe = Knöpfe, keine Lager-Sachen
				continue;
			}
			long n = menge(st);
			bestand.merge(BuiltInRegistries.ITEM.getKey(st.getItem()).toString(), n, Long::sum);
			if (protokoll) {
				System.out.println("[Tittow Panel] Terminal Seite " + gezaehlteSeiten.size() + " Platz " + s.index + ": "
						+ BuiltInRegistries.ITEM.getKey(st.getItem()) + " x" + st.getCount() + " \"" + st.getHoverName().getString()
						+ "\" Lore=" + loreText(st) + " -> " + n);
			}
		}
		if (seiten < 80) {
			for (Slot s : menu.slots) {
				if (istWeiterKnopf(menu, p, s)) {
					seiten++;
					klick(mc, p, menu, s.index, ContainerInput.PICKUP);
					warte = 20; // der Server braucht einen Moment für die nächste Seite
					return;
				}
			}
		}
		schritt = 4;
	}

	/** Alle Zeilen der Beschreibung (Lore) eines Gegenstands, mit | getrennt. */
	private static String loreText(ItemStack st) {
		ItemLore lore = st.get(DataComponents.LORE);
		if (lore == null || lore.lines().isEmpty()) {
			return "";
		}
		StringBuilder t = new StringBuilder();
		for (Component c : lore.lines()) {
			t.append('|').append(c.getString());
		}
		return t.toString();
	}

	private static final Pattern ZAHL = Pattern.compile("(\\d[\\d.,' ]*)");
	private static final String[] MENGEN_WORTE = {"anzahl", "menge", "amount", "stored", "gespeichert", "bestand", "lager",
			"stück", "stk", "count", "quantity", "insgesamt", "gesamt", "total", "vorhanden", "items"};

	/**
	 * Wie viele davon im Lager sind: Lager-Menüs zeigen oft nur einen Stapel und schreiben die echte Menge in die
	 * Beschreibung (z. B. "Menge: 1.600"). Steht dort so eine Zahl, gilt sie, sonst die Stapelgröße.
	 */
	static long menge(ItemStack st) {
		long beste = st.getCount();
		ItemLore lore = st.get(DataComponents.LORE);
		if (lore == null) {
			return beste;
		}
		for (Component c : lore.lines()) {
			String zeile = c.getString().toLowerCase(Locale.ROOT);
			boolean mengenZeile = false;
			for (String w : MENGEN_WORTE) {
				if (zeile.contains(w)) {
					mengenZeile = true;
					break;
				}
			}
			if (!mengenZeile) {
				continue;
			}
			Matcher m = ZAHL.matcher(zeile);
			while (m.find()) {
				String ziffern = m.group(1).replaceAll("[^0-9]", "");
				if (!ziffern.isEmpty() && ziffern.length() < 12) {
					beste = Math.max(beste, Long.parseLong(ziffern));
				}
			}
		}
		return beste;
	}

	private static boolean istZurueck(ItemStack s) {
		String name = s.getHoverName().getString().toLowerCase(Locale.ROOT);
		return name.contains("zurück") || name.contains("vorherige") || name.contains("back") || name.contains("previous")
				|| name.contains("←") || name.contains("«") || name.contains("<<");
	}

	// ---- Sachen ablegen --------------------------------------------------------

	private void ablegenSchritt(Minecraft mc, LocalPlayer p, AbstractContainerMenu menu) {
		// Ergebnis des letzten Klicks prüfen
		if (letzterSlot >= 0 && menu.getCarried().isEmpty()) {
			int jetzt = menu.getSlot(letzterSlot).getItem().getCount();
			if (jetzt >= letzteMenge) {
				gehtNicht.add(letzterSlot);
				if (!aufnehmen && ++ohneWirkung >= 2) {
					aufnehmen = true; // Shift-Klick geht nicht: anders probieren
					gehtNicht.clear();
				}
			} else {
				ohneWirkung = 0;
			}
			letzterSlot = -1;
		}
		ItemStack hand = menu.getCarried();
		if (!hand.isEmpty()) {
			if (zurueckSlot >= 0) {
				// an der Maus: in einen leeren Platz im Terminal legen
				int leer = leererTerminalPlatz(menu, p);
				int ziel = zurueckSlot;
				zurueckSlot = -1;
				if (leer >= 0) {
					letzterSlot = ziel;
					letzteMenge = hand.getCount();
					klick(mc, p, menu, leer, ContainerInput.PICKUP);
					return;
				}
				gehtNicht.add(ziel);
				klick(mc, p, menu, ziel, ContainerInput.PICKUP); // kein Platz: zurücklegen
				return;
			}
			// ging nicht ins Terminal: zurück ins Inventar
			int frei = freierPlatz(menu, p);
			if (frei < 0) {
				schritt = 4;
				return;
			}
			klick(mc, p, menu, frei, ContainerInput.PICKUP);
			return;
		}
		if (klicks >= 80) {
			schritt = 4;
			return;
		}
		int s = naechsterZumAblegen(menu, p);
		if (s < 0) {
			schritt = 4; // nichts mehr abzulegen
			return;
		}
		if (aufnehmen) {
			zurueckSlot = s; // erst an die Maus nehmen, im nächsten Schritt ins Terminal legen
			klick(mc, p, menu, s, ContainerInput.PICKUP);
		} else {
			letzterSlot = s;
			letzteMenge = menu.getSlot(s).getItem().getCount();
			klick(mc, p, menu, s, ContainerInput.QUICK_MOVE);
		}
	}

	/**
	 * Ein Inventar-Platz (Menü-Nummer), dessen Sachen weg dürfen: nur was seit dem Start dazugekommen ist,
	 * nie das Terminal, und etwas Erde bleibt fürs Stopfen. -1 = nichts mehr.
	 */
	private int naechsterZumAblegen(AbstractContainerMenu menu, LocalPlayer p) {
		Map<String, Integer> jetzt = inventar(p);
		for (Slot s : menu.slots) {
			// nur das obere Inventar (9-35) und Slot 6: die übrige Hotbar mit Schwert, Essen, Werkzeug, Werkbank, Terminal und Slot 9 bleibt
			if (s.container != p.getInventory() || (s.getContainerSlot() < Inventory.getSelectionSize() && s.getContainerSlot() != Versorgung.ERDE_SLOT && s.getContainerSlot() != BurgBau.GERUEST_SLOT)
					|| s.getContainerSlot() >= 36 || gehtNicht.contains(s.index)) {
				continue;
			}
			ItemStack st = s.getItem();
			if (st.isEmpty() || s.getContainerSlot() == TERMINAL_SLOT || istTerminal(st)) {
				continue;
			}
			String k = schluessel(st);
			int behalten = start.getOrDefault(k, 0);
			if (st.is(Items.DIRT)) {
				behalten = Math.max(behalten, ERDE_BEHALTEN);
			}
			if (jetzt.getOrDefault(k, 0) - st.getCount() >= behalten) {
				return s.index;
			}
		}
		return -1;
	}

	// ---- Allgemein --------------------------------------------------------------

	private void klick(Minecraft mc, LocalPlayer p, AbstractContainerMenu menu, int slot, ContainerInput art) {
		mc.gameMode.handleContainerInput(menu.containerId, slot, 0, art, p);
		klicks++;
		warte = 4; // nicht zu schnell klicken, der Server braucht einen Moment
	}

	private String fertig(LocalPlayer p) {
		if (p.containerMenu != p.inventoryMenu) {
			p.closeContainer();
		}
		p.getInventory().setSelectedSlot(alterSlot);
		schritt = 0;
		if (zaehlen) {
			zaehlen = false;
			return ergebnis != null ? ergebnis : "OK: Lager durchgezählt (" + gezaehlteSeiten.size() + " Seiten).";
		}
		BaritoneRahmen.befehl("resume");
		if (ergebnis != null) {
			return ergebnis;
		}
		if (ablegen) {
			int frei = vorherBelegt - belegt(p);
			return frei > 0 ? "OK: " + frei + " Plätze im Inventar frei gemacht (Sachen ins Funk-Terminal gelegt), es geht weiter."
					: "Konnte nichts ins Funk-Terminal legen (nur Sachen vom Start im Inventar, oder das Lager ist voll).";
		}
		int dazu = anzahl(p, zielItem) - erdeVorher;
		String name = new ItemStack(zielItem).getHoverName().getString();
		return dazu > 0 ? "OK: " + dazu + " " + name + " aus dem Funk-Terminal geholt, es geht weiter."
				: "Im Funk-Terminal war kein(e) " + name + " zu finden (oder das Inventar ist voll).";
	}

	private void ende(String text) {
		ergebnis = text;
		schritt = 4;
	}

	/** Was gerade im Inventar ist: Sorte (mit Namen) und Anzahl. Wird beim Start des Ebnens gemerkt. */
	static Map<String, Integer> inventar(LocalPlayer p) {
		Map<String, Integer> m = new HashMap<>();
		for (int i = 0; i < 36; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (!s.isEmpty()) {
				m.merge(schluessel(s), s.getCount(), Integer::sum);
			}
		}
		return m;
	}

	static String schluessel(ItemStack s) {
		return BuiltInRegistries.ITEM.getKey(s.getItem()) + "|" + s.getHoverName().getString();
	}

	/** Erde im ganzen Inventar. */
	static int erde(LocalPlayer p) {
		int n = 0;
		for (int i = 0; i < 36; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(Items.DIRT)) {
				n += s.getCount();
			}
		}
		return n;
	}

	/** Ist kein Platz im Inventar mehr frei? */
	static boolean inventarVoll(LocalPlayer p) {
		return p.getInventory().getFreeSlot() < 0;
	}

	/** Leere Plätze im Inventar (Hotbar und oben). */
	private static int freiePlaetze(LocalPlayer p) {
		return 36 - belegt(p);
	}

	private static int belegt(LocalPlayer p) {
		int n = 0;
		for (int i = 0; i < 36; i++) {
			if (!p.getInventory().getItem(i).isEmpty()) {
				n++;
			}
		}
		return n;
	}

	/** Slot 8, wenn dort etwas liegt, das wie ein Terminal heißt (oder überhaupt etwas, wenn nirgends eins ist). */
	private static int terminalSuchen(Inventory inv) {
		if (istTerminal(inv.getItem(TERMINAL_SLOT))) {
			return TERMINAL_SLOT;
		}
		for (int i = 0; i < 36; i++) {
			if (i != 8 && istTerminal(inv.getItem(i))) { // Slot 9 (Nummer 8) nie anfassen
				return i;
			}
		}
		return inv.getItem(TERMINAL_SLOT).isEmpty() ? -1 : TERMINAL_SLOT;
	}

	private static boolean istTerminal(ItemStack s) {
		if (s.isEmpty()) {
			return false;
		}
		String name = s.getHoverName().getString().toLowerCase(Locale.ROOT);
		return name.contains("terminal") || name.contains("funk");
	}

	/** Gehört der Platz zur untersten Reihe des Terminals (Knöpfe wie Suchen, Sortieren, Weiter)? */
	private static boolean inKnopfReihe(AbstractContainerMenu menu, LocalPlayer p, Slot s) {
		int oben = 0;
		for (Slot t : menu.slots) {
			if (t.container != p.getInventory()) {
				oben++;
			}
		}
		return s.container != p.getInventory() && oben > 9 && s.index >= oben - 9;
	}

	/**
	 * Ist das der Blätter-Knopf "weiter"? Nur in der Knopf-Reihe unten und nie ein gelagerter Gegenstand
	 * (sonst hält er z. B. eine Schaufel mit » im Namen für den Knopf und nimmt sie heraus).
	 */
	private static boolean istWeiterKnopf(AbstractContainerMenu menu, LocalPlayer p, Slot s) {
		ItemStack st = s.getItem();
		return !st.isEmpty() && inKnopfReihe(menu, p, s) && !loreText(st).toLowerCase(Locale.ROOT).contains("im lager") && istWeiter(st);
	}

	private static boolean istWeiter(ItemStack s) {
		String name = s.getHoverName().getString().toLowerCase(Locale.ROOT);
		for (String w : WEITER) {
			if (name.contains(w)) {
				return true;
			}
		}
		return false;
	}

	/** Ein leerer Platz oben im eigenen Inventar (Menü-Nummer, nie Hotbar), -1 wenn keiner frei ist. */
	private static int freierPlatz(AbstractContainerMenu menu, LocalPlayer p) {
		for (Slot s : menu.slots) {
			if (s.container == p.getInventory() && s.getContainerSlot() >= Inventory.getSelectionSize()
					&& s.getContainerSlot() < 36 && s.getItem().isEmpty()) {
				return s.index;
			}
		}
		return -1;
	}

	/** Ein leerer Platz im Terminal-Teil des Menüs, -1 wenn keiner frei ist. */
	private static int leererTerminalPlatz(AbstractContainerMenu menu, LocalPlayer p) {
		for (Slot s : menu.slots) {
			if (s.container != p.getInventory() && s.getItem().isEmpty()) {
				return s.index;
			}
		}
		return -1;
	}
}
