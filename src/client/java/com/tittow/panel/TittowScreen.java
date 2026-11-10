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
	private final Hell hell;
	private Button botKnopf;
	private String meldung = "";

	public TittowScreen(BotLogik bot, Bereich bereich, Hell hell) {
		super(Component.literal("Tittow Panel"));
		this.bot = bot;
		this.bereich = bereich;
		this.hell = hell;
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
		this.addRenderableWidget(Button.builder(Component.literal("+ Ecke hier"), b -> eckeHinzufuegen())
				.pos(mitte - 100, y).size(98, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Letzte Ecke weg"), b -> {
			if (!bereich.ecken.isEmpty()) {
				bereich.ecken.remove(bereich.ecken.size() - 1);
				bereich.speichern();
				meldung = "Letzte Ecke entfernt, noch " + bereich.ecken.size() + ".";
			}
		}).pos(mitte + 2, y).size(98, 20).build());
		y += 24;
		this.addRenderableWidget(Button.builder(Component.literal("Mob-Ecke 1 hier"), b -> setzen("mobs1"))
				.pos(mitte - 100, y).size(98, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Mob-Ecke 2 hier"), b -> setzen("mobs2"))
				.pos(mitte + 2, y).size(98, 20).build());
		y += 24;
		this.addRenderableWidget(Button.builder(Component.literal("Fläche löschen"), b -> {
			bereich.ecken.clear();
			bereich.ecke1 = bereich.ecke2 = bereich.mobs1 = bereich.mobs2 = null;
			bereich.speichern();
			meldung = "Fläche gelöscht.";
		}).pos(mitte - 100, y).size(98, 20).build());
		this.addRenderableWidget(Button.builder(hellText(), b -> {
			hell.umschalten(this.minecraft);
			b.setMessage(hellText());
		}).pos(mitte + 2, y).size(98, 20).build());
		y += 24;
		this.addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> this.onClose())
				.pos(mitte - 100, y).size(200, 20).build());
	}

	private Component hellText() {
		return Component.literal(hell.istAn() ? "Hell: AN" : "Hell: AUS");
	}

	private Component botText() {
		return Component.literal(bot.istAktiv() ? "Bot: AN  (klicken zum Ausschalten)" : "Bot: AUS  (klicken zum Einschalten)");
	}

	private void eckeHinzufuegen() {
		LocalPlayer p = this.minecraft.player;
		if (p == null) {
			return;
		}
		bereich.ecken.add(new int[] {p.getBlockX(), p.getBlockY(), p.getBlockZ()});
		bereich.ecke1 = bereich.ecke2 = null; // alte Rechteck-Fläche nicht mehr nutzen
		bereich.speichern();
		meldung = "Ecke " + bereich.ecken.size() + " gesetzt: " + p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ();
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
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		int mitte = this.width / 2;
		int y = this.height / 2 - 100;
		graphics.centeredText(this.font, "Tittow Panel", mitte, y, LILA);
		graphics.centeredText(this.font, bot.istAktiv() ? "Status: AN" : "Status: AUS", mitte, y + 14, bot.istAktiv() ? GRUEN : ROT);
		graphics.centeredText(this.font, bereich.beschreibung(), mitte, y + 28, WEISS);
		graphics.centeredText(this.font, "Kills: " + bot.kills(), mitte, y + 40, GRAU);
		if (!meldung.isEmpty()) {
			graphics.centeredText(this.font, meldung, mitte, this.height / 2 + 98, GRUEN);
		}
		graphics.centeredText(this.font, "Ecken in beliebiger Reihenfolge setzen (L = 6 Ecken). Nur für die eigene Welt!",
				mitte, this.height / 2 + 112, GRAU);
		karteZeichnen(graphics, mitte + 112, this.height / 2 - 50);
	}

	/** Kleine Karte zur Kontrolle: grün = Lauf-Fläche, rot = Mob-Bereich, gelb = du. */
	private void karteZeichnen(GuiGraphicsExtractor graphics, int links, int oben) {
		if (!bereich.flaecheGesetzt()) {
			return;
		}
		int[] g = bereich.grenzen();
		int breit = g[1] - g[0] + 1;
		int lang = g[3] - g[2] + 1;
		int feld = Math.max(2, Math.min(12, 96 / Math.max(breit, lang)));
		graphics.text(this.font, "Karte (Norden oben)", links, oben - 12, GRAU, true);
		graphics.fill(links - 1, oben - 1, links + breit * feld + 1, oben + lang * feld + 1, 0xFF000000);
		LocalPlayer p = this.minecraft.player;
		for (int x = g[0]; x <= g[1]; x++) {
			for (int z = g[2]; z <= g[3]; z++) {
				int farbe;
				if (p != null && p.getBlockX() == x && p.getBlockZ() == z) {
					farbe = 0xFFFFE04A;          // du
				} else if (bereich.imMobBereich(x, z)) {
					farbe = 0xFFD94040;          // Mob-Bereich
				} else if (bereich.erlaubt(x, z)) {
					farbe = 0xFF3FBF5F;          // Lauf-Fläche
				} else {
					farbe = 0xFF303338;          // gesperrt
				}
				int sx = links + (x - g[0]) * feld;
				int sz = oben + (z - g[2]) * feld;
				graphics.fill(sx, sz, sx + feld - 1, sz + feld - 1, farbe);
			}
		}
		graphics.text(this.font, "grün = laufen", links, oben + lang * feld + 4, 0xFF3FBF5F, true);
		graphics.text(this.font, "grau/rot = gesperrt", links, oben + lang * feld + 14, GRAU, true);
	}

	@Override
	public boolean isPauseScreen() {
		return false; // im Einzelspieler läuft die Welt weiter
	}
}
