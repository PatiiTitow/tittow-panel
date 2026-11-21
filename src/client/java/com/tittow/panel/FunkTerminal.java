package com.tittow.panel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
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
	private Map<String, Integer> start = Map.of();
	private int warte;
	private int versuche;
	private int seiten;
	private int klicks;
	private int alterSlot;
	private int erdeVorher;
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
	}

	/** Erde aus dem Terminal holen: bis etwa 8 Stacks, ein paar Plätze im Inventar bleiben frei. */
	void erdeHolen(Minecraft mc) {
		beginnen(mc, false, Map.of());
	}

	/** Alles hineinlegen, was seit dem Start dazugekommen ist (startInventar = Sachen beim Start). */
	void ablegen(Minecraft mc, Map<String, Integer> startInventar) {
		beginnen(mc, true, startInventar == null ? Map.of() : startInventar);
	}

	private void beginnen(Minecraft mc, boolean ablegen, Map<String, Integer> startInventar) {
		LocalPlayer p = mc.player;
		if (p == null) {
			return;
		}
		BaritoneRahmen.befehl("pause"); // Baritone soll nicht weiterlaufen, während das Menü offen ist
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
		erdeVorher = erde(p);
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
		int jetzt = erde(p);
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
		if (freiePlaetze(p) <= FREI_LASSEN || jetzt >= ERDE_HOLEN_BIS || klicks >= 60) {
			schritt = 4; // genug Erde, oder das Inventar würde zu voll
			return;
		}
		for (Slot s : menu.slots) {
			if (s.container != p.getInventory() && s.getItem().is(Items.DIRT)) {
				erdeBeimKlick = jetzt;
				klick(mc, p, menu, s.index, aufnehmen ? ContainerInput.PICKUP : ContainerInput.QUICK_MOVE);
				return;
			}
		}
		// auf dieser Seite keine Erde: nächste Seite suchen
		if (seiten < 30) {
			for (Slot s : menu.slots) {
				if (s.container != p.getInventory() && !s.getItem().isEmpty() && istWeiter(s.getItem())) {
					seiten++;
					klick(mc, p, menu, s.index, ContainerInput.PICKUP);
					warte = 10;
					return;
				}
			}
		}
		schritt = 4;
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
			// nur das obere Inventar (9-35): die Hotbar mit Schwert, Essen, Werkzeug, Erde, Werkbank, Terminal und Slot 9 bleibt
			if (s.container != p.getInventory() || s.getContainerSlot() < Inventory.getSelectionSize()
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
		BaritoneRahmen.befehl("resume");
		schritt = 0;
		if (ergebnis != null) {
			return ergebnis;
		}
		if (ablegen) {
			int frei = vorherBelegt - belegt(p);
			return frei > 0 ? "OK: " + frei + " Plätze im Inventar frei gemacht (Sachen ins Funk-Terminal gelegt), es geht weiter."
					: "Konnte nichts ins Funk-Terminal legen (nur Sachen vom Start im Inventar, oder das Lager ist voll).";
		}
		int dazu = erde(p) - erdeVorher;
		return dazu > 0 ? "OK: " + dazu + " Erde aus dem Funk-Terminal geholt, es geht weiter."
				: "Im Funk-Terminal war keine Erde zu finden (oder das Inventar ist voll).";
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

	private static String schluessel(ItemStack s) {
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
