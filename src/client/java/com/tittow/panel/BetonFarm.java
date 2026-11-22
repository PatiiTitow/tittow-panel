package com.tittow.panel;

import com.tittow.panel.mixin.KeyMappingAccessor;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * "Beton-Farm": macht immer wieder Linksklick (Block abbauen, die Taste bleibt gedrückt, bis der Block weg ist)
 * und gleich danach einen Rechtsklick (Block setzen). Wie beim echten Klicken: Was in der Hand ist, wird benutzt
 * (Spitzhacke in der Haupthand, Trockenbeton in der Zweithand geht auch).
 * Pausiert, solange ein Menü offen ist. An/Aus mit Numpad 9 oder im Panel.
 */
public final class BetonFarm {
	private static final int MAX_ABBAU = 20 * 6; // länger als 6 Sekunden an einem Block: aufgeben, trotzdem Rechtsklick

	private boolean an;
	private boolean links; // gerade beim Abbauen (Linksklick gehalten)
	private BlockPos block; // der Block, der gerade abgebaut wird
	private int abbauTicks;
	private int warte;
	private long runden;

	public boolean istAn() {
		return an;
	}

	public void umschalten(Minecraft mc) {
		if (an) {
			aus(mc, "Beton-Farm: AUS (" + runden + " Runden)");
		} else {
			an = true;
			links = false;
			warte = 10;
			runden = 0;
			melden(mc, "Beton-Farm: AN – schau auf den Block. Aus mit Numpad 9.");
		}
	}

	public void aus(Minecraft mc, String text) {
		if (!an) {
			return;
		}
		an = false;
		loslassen(mc);
		if (text != null) {
			melden(mc, text);
		}
	}

	public void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (!an) {
			return;
		}
		if (p == null || mc.gameMode == null) {
			an = false;
			return;
		}
		if (mc.gui.screen() != null || !p.isAlive()) { // Menü offen oder tot: nichts klicken
			loslassen(mc);
			links = false;
			return;
		}
		// Werkzeug schonen: kurz vor dem Kaputtgehen aufhören
		ItemStack hand = p.getMainHandItem();
		if (hand.isDamageableItem() && hand.getMaxDamage() - hand.getDamageValue() <= 10) {
			aus(mc, "Beton-Farm: AUS – dein Werkzeug ist fast kaputt.");
			return;
		}
		if (warte > 0) {
			warte--;
			return;
		}
		if (links) {
			abbauTicks++;
			boolean weg = mc.level.getBlockState(block).isAir() || !zieltAuf(mc, block);
			if (weg || abbauTicks > MAX_ABBAU) {
				mc.options.keyAttack.setDown(false);
				links = false;
				klick(mc.options.keyUse); // gleich danach Rechtsklick
				runden++;
				warte = 2;
			}
			return;
		}
		// neue Runde: Linksklick (gedrückt halten, bis der Block weg ist)
		if (mc.hitResult instanceof BlockHitResult t && t.getType() == HitResult.Type.BLOCK
				&& !mc.level.getBlockState(t.getBlockPos()).isAir()) {
			block = t.getBlockPos();
			abbauTicks = 0;
			links = true;
			klick(mc.options.keyAttack);
			mc.options.keyAttack.setDown(true);
		} else { // nichts zum Abbauen da: nur Rechtsklick
			klick(mc.options.keyUse);
			warte = 4;
		}
	}

	private static boolean zieltAuf(Minecraft mc, BlockPos pos) {
		return mc.hitResult instanceof BlockHitResult t && t.getType() == HitResult.Type.BLOCK && t.getBlockPos().equals(pos);
	}

	private void loslassen(Minecraft mc) {
		if (links) {
			mc.options.keyAttack.setDown(false);
		}
	}

	private static void klick(KeyMapping taste) {
		KeyMappingAccessor a = (KeyMappingAccessor) taste;
		a.tittow_setClickCount(a.tittow_getClickCount() + 1);
	}

	private static void melden(Minecraft mc, String text) {
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal(text));
		}
	}
}
