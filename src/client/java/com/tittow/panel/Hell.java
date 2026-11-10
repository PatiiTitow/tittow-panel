package com.tittow.panel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * "Hell": Nachtsicht, die nur bei dir auf dem Bildschirm wirkt (ohne Symbol und Partikel).
 * Nachts und in Höhlen ist alles so hell wie am Tag, tagsüber sieht es ganz normal aus,
 * weil deine eigene Helligkeits-Einstellung nicht verändert wird.
 */
public final class Hell {
	private final Bereich einstellungen;
	private boolean nachtsichtVonUns = false;

	public Hell(Bereich einstellungen) {
		this.einstellungen = einstellungen;
	}

	public boolean istAn() {
		return einstellungen.hell;
	}

	public void umschalten(Minecraft mc) {
		einstellungen.hell = !einstellungen.hell;
		einstellungen.speichern();
		tick(mc);
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal(einstellungen.hell ? "Hell: AN" : "Hell: AUS"));
		}
	}

	/** Jeden Tick prüfen, damit "Hell" auch nach Tod, Neustart oder Dimensionswechsel aktiv bleibt. */
	public void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) {
			nachtsichtVonUns = false;
			return;
		}
		if (einstellungen.hell) {
			// bei einem echten Nachtsicht-Trank nichts ändern
			if (p.getEffect(MobEffects.NIGHT_VISION) == null) {
				p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
				nachtsichtVonUns = true;
			}
		} else if (nachtsichtVonUns) {
			p.removeEffect(MobEffects.NIGHT_VISION);
			nachtsichtVonUns = false;
		}
	}
}
