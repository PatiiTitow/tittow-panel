package com.tittow.panel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Versorgung beim Ebnen, passend zu deiner Hotbar:
 * Slot 1 Schwert/Glefe, Slot 2 Essen (Goldene Karotten), Slot 3-5 Schaufel, Spitzhacke, Axt, Slot 6 Erde,
 * Slot 7 Virtuelle Werkbank, Slot 8 Funk-Terminal, Slot 9 wird nie angefasst.
 * - Essen: Hunger unter 18, dann Baritone kurz anhalten und aus Slot 2 essen, bis wieder satt.
 * - Spitzhacke: alle 20 Sekunden ihre Fähigkeit benutzen (Rechtsklick mit Slot 4).
 * - Erde: Baritone baut nur mit Sachen aus der Hotbar. Deshalb legt die Mod Erde von oben aus dem Inventar
 *   in Slot 6 nach. (Baritone selbst darf nichts im Inventar umräumen, sonst legt es Sachen auf Slot 1 und 9.)
 */
final class Versorgung {
	static final int ESSEN_SLOT = 1;  // Slot 2
	static final int ERDE_SLOT = 5;   // Slot 6
	/** Unter diesem Hunger wird gegessen (20 = voll). Ab 18 heilt man von selbst. */
	private static final int SATT = 18;

	private int essTicks = -1;
	private int alterSlot;
	private int nachfuellWarte;

	boolean isstGerade() {
		return essTicks >= 0;
	}

	void abbrechen(Minecraft mc) {
		if (essTicks >= 0) {
			mc.options.keyUse.setDown(false);
			essTicks = -1;
		}
	}

	/** Gibt true zurück, solange gegessen wird (dann nichts anderes machen). */
	boolean essen(Minecraft mc, LocalPlayer p) {
		if (essTicks >= 0) {
			essTicks++;
			p.getInventory().setSelectedSlot(essSlot(p));
			mc.options.keyUse.setDown(true);
			boolean fertig = essTicks > 10 && !p.isUsingItem();
			if (fertig || essTicks > 80) {
				mc.options.keyUse.setDown(false);
				essTicks = -1;
				p.getInventory().setSelectedSlot(alterSlot);
				if (p.getFoodData().getFoodLevel() < SATT && essSlot(p) >= 0 && fertig) {
					essTicks = 0; // noch nicht satt: gleich noch eine
					return true;
				}
				BaritoneRahmen.befehl("resume");
			}
			return true;
		}
		if (p.getFoodData().getFoodLevel() >= SATT || mc.gui.screen() != null || essSlot(p) < 0) {
			return false;
		}
		BaritoneRahmen.befehl("pause");
		alterSlot = p.getInventory().getSelectedSlot();
		essTicks = 0;
		return true;
	}

	/** Slot 2, wenn dort Essen liegt, sonst anderes Essen in der Hotbar (nie Slot 9). -1 = nichts da. */
	private static int essSlot(LocalPlayer p) {
		if (p.getInventory().getItem(ESSEN_SLOT).has(DataComponents.FOOD)) {
			return ESSEN_SLOT;
		}
		for (int i = 0; i < 8; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(Items.GOLDEN_CARROT)) {
				return i;
			}
		}
		return -1;
	}

	static final int SPITZHACKE_SLOT = 3; // Slot 4
	/** Alle 20 Sekunden die Fähigkeit der Spitzhacke (Rechtsklick). */
	private static final int FAEHIGKEIT_TICKS = 20 * 20;
	private int faehigkeitWarte = 40;

	/**
	 * Benutzt alle 20 Sekunden die Fähigkeit der Spitzhacke: kurz Slot 4 nehmen, Rechtsklick (wie in die Luft),
	 * gleich wieder zurück auf das, was Baritone gerade in der Hand hatte.
	 */
	void spitzhackeFaehigkeit(Minecraft mc, LocalPlayer p) {
		if (--faehigkeitWarte > 0) {
			return;
		}
		if (mc.gui.screen() != null || mc.gameMode == null || p.isUsingItem()) {
			faehigkeitWarte = 20; // gleich nochmal probieren
			return;
		}
		int slot = spitzhackeSlot(p);
		faehigkeitWarte = FAEHIGKEIT_TICKS;
		if (slot < 0) {
			return;
		}
		Inventory inv = p.getInventory();
		int vorher = inv.getSelectedSlot();
		inv.setSelectedSlot(slot);
		mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
		inv.setSelectedSlot(vorher);
	}

	/** Slot 4, wenn dort eine Spitzhacke liegt, sonst eine andere Spitzhacke in der Hotbar (nie Slot 9). -1 = keine. */
	private static int spitzhackeSlot(LocalPlayer p) {
		if (istSpitzhacke(p.getInventory().getItem(SPITZHACKE_SLOT))) {
			return SPITZHACKE_SLOT;
		}
		for (int i = 0; i < 8; i++) {
			if (istSpitzhacke(p.getInventory().getItem(i))) {
				return i;
			}
		}
		return -1;
	}

	private static boolean istSpitzhacke(ItemStack s) {
		return !s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().endsWith("pickaxe");
	}

	/** Hält Slot 6 voll mit Erde aus dem oberen Inventar. Danach darf Baritone weiterbauen. */
	void erdeNachlegen(Minecraft mc, LocalPlayer p) {
		if (nachfuellWarte > 0) {
			nachfuellWarte--;
			return;
		}
		if (mc.gui.screen() != null || p.containerMenu != p.inventoryMenu || mc.gameMode == null) {
			return;
		}
		Inventory inv = p.getInventory();
		ItemStack inSlot = inv.getItem(ERDE_SLOT);
		if (inSlot.is(Items.DIRT) && inSlot.getCount() > 8) {
			return;
		}
		int beste = -1;
		for (int i = Inventory.getSelectionSize(); i < 36; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(Items.DIRT) && (beste < 0 || s.getCount() > inv.getItem(beste).getCount())) {
				beste = i;
			}
		}
		if (beste < 0 || (inSlot.is(Items.DIRT) && inv.getItem(beste).getCount() <= inSlot.getCount())) {
			return;
		}
		// tauschen: Erde nach Slot 6, was dort lag, kommt an ihren Platz oben im Inventar
		mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, beste, ERDE_SLOT, ContainerInput.SWAP, p);
		BaritoneRahmen.bauenFortsetzen(); // falls Baritone wegen fehlender Erde gewartet hat
		nachfuellWarte = 20;
	}
}
