package com.tittow.panel;

import com.tittow.panel.mixin.OptionInstanceAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * "Hell": Nacht wie Tag. Zwei Dinge zusammen:
 * 1. Helligkeit weit über 100 %.
 * 2. Nachtsicht nur bei dir auf dem Bildschirm (ohne Symbol), damit alles voll ausgeleuchtet ist.
 */
public final class Hell {
	private static final double HELL_WERT = 16.0;
	private static final double NORMAL_WERT = 0.5;

	private final Bereich einstellungen;
	private boolean angewendet = false;
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
		anwenden(mc);
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal(einstellungen.hell ? "Hell: AN" : "Hell: AUS"));
		}
	}

	/** Jeden Tick prüfen, damit "Hell" auch nach einem Neustart oder Optionen-Menü aktiv bleibt. */
	public void tick(Minecraft mc) {
		if (einstellungen.hell || angewendet) {
			anwenden(mc);
		}
	}

	private void anwenden(Minecraft mc) {
		double soll = einstellungen.hell ? HELL_WERT : NORMAL_WERT;
		if (!einstellungen.hell && !angewendet) {
			return;
		}
		if (mc.options.gamma().get() != soll) {
			((OptionInstanceAccessor) (Object) mc.options.gamma()).tittow_setValue(soll);
		}
		angewendet = einstellungen.hell;
		nachtsicht(mc.player);
	}

	private void nachtsicht(LocalPlayer p) {
		if (p == null) {
			return;
		}
		if (einstellungen.hell) {
			MobEffectInstance jetzt = p.getEffect(MobEffects.NIGHT_VISION);
			// unendlich lang, ohne Partikel und ohne Symbol; bei echter Nachtsicht-Trank nichts ändern
			if (jetzt == null) {
				p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
				nachtsichtVonUns = true;
			}
		} else if (nachtsichtVonUns) {
			p.removeEffect(MobEffects.NIGHT_VISION);
			nachtsichtVonUns = false;
		}
	}
}
