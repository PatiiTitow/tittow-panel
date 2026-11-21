package com.tittow.panel;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Holt Erde aus dem Funk-Terminal (Hotbar-Slot 8): Baritone kurz anhalten, Terminal öffnen, alle Erde
 * herausnehmen, die ins Inventar passt (auch über mehrere Seiten), Terminal zu, Baritone weiter.
 * Das Terminal ist ein Menü wie eine Kiste, deshalb geht es mit normalen Inventar-Klicks.
 */
final class ErdeHolen {
	/** Slot 8 in der Hotbar (gezählt ab 0). */
	private static final int TERMINAL_SLOT = 7;
	private static final String[] WEITER = {"weiter", "nächste", "naechste", "next", "→", "»", ">>"};

	/** 0 = aus, 1 = Terminal öffnen, 2 = auf das Menü warten, 3 = Erde nehmen, 4 = zumachen. */
	private int schritt;
	private int warte;
	private int versuche;
	private int seiten;
	private int klicks;
	private int alterSlot;
	private int erdeVorher;
	private int erdeBeimKlick;
	private int ohneWirkung;
	private boolean aufnehmen; // Klick-Art, falls Shift-Klick im Terminal nicht geht
	private String ergebnis;

	boolean laeuft() {
		return schritt != 0;
	}

	/** Beim Stoppen des Ebnens: nicht weiter klicken. */
	void abbrechen() {
		schritt = 0;
	}

	void starten(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) {
			return;
		}
		BaritoneRahmen.befehl("pause"); // Baritone soll nicht weiterlaufen, während das Menü offen ist
		schritt = 1;
		warte = 10;
		versuche = 0;
		seiten = 0;
		klicks = 0;
		ohneWirkung = 0;
		aufnehmen = false;
		alterSlot = p.getInventory().getSelectedSlot();
		erdeVorher = erde(p);
		ergebnis = null;
	}

	/** Jeden Tick aufrufen, solange laeuft(). Gibt am Ende eine Meldung zurück, sonst null. */
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
			case 3 -> nehmen(mc, p);
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

	private void nehmen(Minecraft mc, LocalPlayer p) {
		AbstractContainerMenu menu = p.containerMenu;
		if (menu == p.inventoryMenu || !(mc.gui.screen() instanceof AbstractContainerScreen<?>)) {
			schritt = 4; // Menü ist zu (z. B. selbst mit Esc zugemacht)
			return;
		}
		int jetzt = erde(p);
		ItemStack hand = menu.getCarried();
		if (!hand.isEmpty()) { // hängt etwas an der Maus: in einen freien Inventar-Platz legen
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
		if (p.getInventory().getFreeSlot() < 0 || klicks >= 60) {
			schritt = 4; // Inventar voll
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
		int dazu = erde(p) - erdeVorher;
		return dazu > 0 ? "OK: " + dazu + " Erde aus dem Funk-Terminal geholt, es geht weiter."
				: "Im Funk-Terminal war keine Erde zu finden (oder das Inventar ist voll).";
	}

	private void ende(String text) {
		ergebnis = text;
		schritt = 4;
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

	/** Slot 8, wenn dort etwas liegt, das wie ein Terminal heißt (oder überhaupt etwas, wenn nirgends eins ist). */
	private static int terminalSuchen(Inventory inv) {
		if (istTerminal(inv.getItem(TERMINAL_SLOT))) {
			return TERMINAL_SLOT;
		}
		for (int i = 0; i < 36; i++) {
			if (istTerminal(inv.getItem(i))) {
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

	/** Ein leerer Platz im eigenen Inventar (Menü-Nummer), -1 wenn keiner frei ist. */
	private static int freierPlatz(AbstractContainerMenu menu, LocalPlayer p) {
		for (Slot s : menu.slots) {
			if (s.container == p.getInventory() && s.getContainerSlot() < 36 && s.getItem().isEmpty()) {
				return s.index;
			}
		}
		return -1;
	}
}
