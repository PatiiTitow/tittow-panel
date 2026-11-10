package com.tittow.panel;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLScancode;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Startpunkt der Mod: Numpad 8 öffnet das Tittow Panel, der Bot läuft jeden Tick. */
public final class TittowPanelClient implements ClientModInitializer {
	private static final Bereich BEREICH = Bereich.laden();
	private static final BotLogik BOT = new BotLogik(BEREICH);

	@Override
	public void onInitializeClient() {
		KeyMapping.Category kategorie = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(TittowPanel.MOD_ID, "main"));

		// Numpad 8 (lässt sich in den Steuerungs-Einstellungen ändern)
		KeyMapping oeffnen = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.tittowpanel.oeffnen", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_KP_8, kategorie));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (oeffnen.consumeClick()) {
				if (client.player != null && client.gui.screen == null) {
					client.gui.setScreen(new TittowScreen(BOT, BEREICH));
				}
			}
			BOT.tick(client);
		});

		// beim Verlassen der Welt den Bot ausschalten
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			if (BOT.istAktiv()) {
				BOT.aus(client, "AUS");
			}
		});
	}
}
