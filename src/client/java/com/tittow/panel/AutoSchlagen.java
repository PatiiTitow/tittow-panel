package com.tittow.panel;

import com.tittow.panel.mixin.KeyMappingAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.EntityHitResult;

/**
 * "Auto-Schlagen": alle 0,1 Sekunden (2 Ticks) ein echter Linksklick, solange du ein Tier oder Monster anschaust.
 * Auf Blöcke schlägt er nicht (sonst baut er deine Burg ab), Spieler, Dorfbewohner und Rüstungsständer lässt er in Ruhe
 * (wie die Abwehr beim Bauen). Pausiert, solange ein Menü offen ist. An/Aus mit Numpad 7 oder im Panel.
 */
public final class AutoSchlagen {
	/** Alle 2 Ticks = alle 0,1 Sekunden. */
	private static final int ABSTAND = 2;

	private boolean an;
	private int warte;
	private long schlaege;

	public boolean istAn() {
		return an;
	}

	public void umschalten(Minecraft mc) {
		if (an) {
			aus(mc, "Auto-Schlagen: AUS (" + schlaege + " Schläge)");
		} else {
			an = true;
			warte = 0;
			schlaege = 0;
			melden(mc, "Auto-Schlagen: AN – alle 0,1 s auf das, was du anschaust. Aus mit Numpad 7.");
		}
	}

	public void aus(Minecraft mc, String text) {
		if (!an) {
			return;
		}
		an = false;
		if (text != null) {
			melden(mc, text);
		}
	}

	public void tick(Minecraft mc) {
		if (!an) {
			return;
		}
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null) {
			an = false;
			return;
		}
		if (mc.gui.screen() != null || !p.isAlive()) { // Menü offen oder tot: nicht schlagen
			return;
		}
		if (++warte < ABSTAND) {
			return;
		}
		warte = 0;
		// nur auf Tiere und Monster, die du gerade anschaust
		if (mc.hitResult instanceof EntityHitResult t && Abwehr.istGegner(p, t.getEntity())) {
			KeyMappingAccessor a = (KeyMappingAccessor) mc.options.keyAttack; // echter Linksklick
			a.tittow_setClickCount(a.tittow_getClickCount() + 1);
			schlaege++;
		}
	}

	private static void melden(Minecraft mc, String text) {
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal(text));
		}
	}
}
