package com.tittow.panel;

import com.tittow.panel.mixin.OptionInstanceAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** "Hell": macht alles taghell, auch in Höhlen und nachts (Helligkeit weit über 100 %). */
public final class Hell {
	private static final double HELL_WERT = 16.0;
	private static final double NORMAL_WERT = 0.5;

	private final Bereich einstellungen;
	private boolean angewendet = false;

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
	}
}
