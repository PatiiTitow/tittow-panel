package com.tittow.panel;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** Das Fenster "Tittow Panel", geht mit Numpad 8 auf. */
public final class TittowScreen extends Screen {
	private static final int WEISS = 0xFFFFFFFF;
	private static final int GRAU = 0xFFAAAAAA;
	private static final int LILA = 0xFF9B8CFF;
	private static final int GRUEN = 0xFF55FF77;
	private static final int ROT = 0xFFFF6666;

	private final BotLogik bot;
	private final Bereich bereich;
	private Button botKnopf;
	private String meldung = "";

	public TittowScreen(BotLogik bot, Bereich bereich) {
		super(Component.literal("Tittow Panel"));
		this.bot = bot;
		this.bereich = bereich;
	}

	@Override
	protected void init() {
		int mitte = this.width / 2;
		int y = this.height / 2 - 50;

		this.botKnopf = this.addRenderableWidget(Button.builder(botText(), b -> {
			bot.umschalten(this.minecraft);
			if (bot.istAktiv()) {
				this.onClose(); // Fenster zu, damit der Bot sofort loslegt
			} else {
				b.setMessage(botText());
			}
		}).pos(mitte - 100, y).size(200, 20).build());

		y += 32;
		this.addRenderableWidget(Button.builder(Component.literal("Ecke 1 hier"), b -> setzen("ecke1"))
				.pos(mitte - 100, y).size(98, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Ecke 2 hier"), b -> setzen("ecke2"))
				.pos(mitte + 2, y).size(98, 20).build());
		y += 24;
		this.addRenderableWidget(Button.builder(Component.literal("Mob-Ecke 1 hier"), b -> setzen("mobs1"))
				.pos(mitte - 100, y).size(98, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Mob-Ecke 2 hier"), b -> setzen("mobs2"))
				.pos(mitte + 2, y).size(98, 20).build());
		y += 24;
		this.addRenderableWidget(Button.builder(Component.literal("Fläche löschen"), b -> {
			bereich.ecke1 = bereich.ecke2 = bereich.mobs1 = bereich.mobs2 = null;
			bereich.speichern();
			meldung = "Fläche gelöscht.";
		}).pos(mitte - 100, y).size(98, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> this.onClose())
				.pos(mitte + 2, y).size(98, 20).build());
	}

	private Component botText() {
		return Component.literal(bot.istAktiv() ? "Bot: AN  (klicken zum Ausschalten)" : "Bot: AUS  (klicken zum Einschalten)");
	}

	private void setzen(String welche) {
		LocalPlayer p = this.minecraft.player;
		if (p == null) {
			return;
		}
		int[] pos = {p.getBlockX(), p.getBlockY(), p.getBlockZ()};
		switch (welche) {
			case "ecke1" -> bereich.ecke1 = pos;
			case "ecke2" -> bereich.ecke2 = pos;
			case "mobs1" -> bereich.mobs1 = pos;
			default -> bereich.mobs2 = pos;
		}
		bereich.speichern();
		meldung = switch (welche) {
			case "ecke1" -> "Ecke 1";
			case "ecke2" -> "Ecke 2";
			case "mobs1" -> "Mob-Ecke 1";
			default -> "Mob-Ecke 2";
		} + " gesetzt: " + pos[0] + " " + pos[1] + " " + pos[2];
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		this.extractBackground(graphics, mouseX, mouseY, delta);
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		int mitte = this.width / 2;
		int y = this.height / 2 - 100;
		graphics.centeredText(this.font, "Tittow Panel", mitte, y, LILA);
		graphics.centeredText(this.font, bot.istAktiv() ? "Status: AN" : "Status: AUS", mitte, y + 14, bot.istAktiv() ? GRUEN : ROT);
		graphics.centeredText(this.font, bereich.beschreibung(), mitte, y + 28, WEISS);
		graphics.centeredText(this.font, "Kills: " + bot.kills(), mitte, y + 40, GRAU);
		if (!meldung.isEmpty()) {
			graphics.centeredText(this.font, meldung, mitte, this.height / 2 + 74, GRUEN);
		}
		graphics.centeredText(this.font, "Stell dich auf ein Feld und drück den passenden Knopf. Nur für die eigene Welt!",
				mitte, this.height / 2 + 90, GRAU);
	}

	@Override
	public boolean isPauseScreen() {
		return false; // im Einzelspieler läuft die Welt weiter
	}
}
