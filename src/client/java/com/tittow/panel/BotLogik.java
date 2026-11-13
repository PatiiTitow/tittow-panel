package com.tittow.panel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.tittow.panel.mixin.KeyMappingAccessor;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Der Bot: steuert die eigene Spielfigur wie ein Mensch.
 * Er drückt dafür nur die normalen Tasten (Laufen, Springen, Schleichen, Linksklick, Rechtsklick)
 * und bewegt die Kamera. Läuft einmal pro Tick (20x pro Sekunde).
 */
public final class BotLogik {
	// ---- Einstellungen ----
	// Die Schlagreichweite kommt von der Waffe in der Hand (Schwert bis 3 Blöcke, Speer 2 bis 4,5 Blöcke).
	private static final double NAH_AM_MOB_ANTEIL = 0.6;   // wie oft er Felder direkt am Mob-Bereich wählt
	private static final int HUNGER_GRENZE = 14;           // ab hier wird gegessen
	private static final String[] WAFFEN = {               // beste zuerst
		"netherite_spear", "diamond_spear", "iron_spear", "copper_spear", "golden_spear", "stone_spear", "wooden_spear",
		"netherite_sword", "diamond_sword", "iron_sword", "stone_sword", "golden_sword", "copper_sword", "wooden_sword",
		"netherite_axe", "diamond_axe", "iron_axe", "stone_axe", "golden_axe", "copper_axe", "wooden_axe"
	};

	private final Random zufall = new Random();
	private final Bereich bereich;

	private boolean aktiv = false;
	private int kills = 0;

	// Laufen
	private List<Bereich.Feld> weg = new ArrayList<>();
	private Bereich.Feld letztesZiel = null;
	private int pauseTicks = 0;
	private int anhaltenBeiTick = -1;
	private int laufTicks = 0;
	private int tickZaehler = 0;

	// Schlagen
	private Entity ziel = null;
	private int reaktionTicks = 0;
	private int schlagPause = 0;
	private int schlaegeBisLaufen = 4;
	private double zielVersatzX = 0;
	private double zielVersatzY = -0.2; // Höhe des Zielpunkts relativ zur eigenen Augenhöhe
	private double zielVersatzZ = 0;
	private final Set<Entity> geschlagen = new HashSet<>();
	private int ohneKampfTicks = 0;      // Schutz, falls er nach dem Laufen nicht wieder ins Schlagen kommt

	// Speer: Mob zu nah -> einen Schritt zurück
	private Entity zuNah = null;
	private int rueckTicks = 0;

	// Beute (Enderperlen, Schriftrollen, Augen) zu den Mobs werfen
	private final Map<String, Integer> startMenge = new HashMap<>();
	private int wurfPruefen = 0;
	private int wurfWarten = -1;
	private boolean wirft = false;
	private int wurfTicks = 0;
	private int wurfRuhig = 0;
	private int wurfPause = 0;

	// Kamera (weiches Umschauen)
	private float wunschYaw = Float.NaN;
	private float wunschPitch = 15f;
	private float letzterWunschYaw = Float.NaN;
	private float ueberschuss = 0f;          // kleines "Überschießen" wie bei einer echten Maus
	private int ueberschussTicks = 0;

	// Kleinigkeiten
	private int springenTicks = 0;
	private int schleichenTicks = 0;

	// Essen
	private int essTicks = -1;

	public BotLogik(Bereich bereich) {
		this.bereich = bereich;
	}

	public boolean istAktiv() {
		return aktiv;
	}

	public int kills() {
		return kills;
	}

	public void umschalten(Minecraft mc) {
		if (aktiv) {
			aus(mc, "AUS");
		} else {
			an(mc);
		}
	}

	public void an(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) {
			return;
		}
		if (!bereich.flaecheGesetzt()) {
			p.sendOverlayMessage(Component.literal("Tittow Bot: Erst die Fläche im Tittow Panel einrichten!"));
			return;
		}
		if (!bereich.erlaubt(Mth.floor(p.getX()), Mth.floor(p.getZ()))) {
			p.sendOverlayMessage(Component.literal("Tittow Bot: Stell dich zuerst auf die freie Fläche!"));
			return;
		}
		aktiv = true;
		weg.clear();
		pauseTicks = 10;
		ziel = null;
		essTicks = -1;
		rueckTicks = 0;
		ohneKampfTicks = 0;
		wirft = false;
		wurfWarten = -1;
		startMenge.clear();
		startMenge.putAll(wurfsachenZaehlen(p)); // was du schon dabei hast, bleibt bei dir
		wunschYaw = p.getYRot();
		waffeNehmen(p);
		p.sendOverlayMessage(Component.literal("Tittow Bot: AN"));
	}

	public void aus(Minecraft mc, String grund) {
		aktiv = false;
		tastenLoslassen(mc.options);
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal("Tittow Bot: " + grund));
		}
	}

	// =====================================================================

	public void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (!aktiv) {
			return;
		}
		if (p == null || mc.level == null) {
			aktiv = false; // Welt verlassen
			return;
		}
		if (p.isDeadOrDying()) {
			aus(mc, "AUS (gestorben)");
			return;
		}
		if (mc.gui.screen() != null) {
			tastenLoslassen(mc.options); // Pause, solange ein Menü offen ist
			return;
		}
		tickZaehler++;
		killsZaehlen();

		// Kamera jede Runde weich nachführen
		kameraBewegen(p);

		if (essen(mc, p)) {
			return;
		}

		kleinigkeiten(mc.options);

		if (werfen(mc, p)) {
			return;
		}
		if (zurueckweichen(p, mc.options)) {
			return;
		}

		// Kommt er nach dem Laufen nicht an (blockiert, festgehangen), schlägt er nach 5 s trotzdem wieder zu.
		if (schlaegeBisLaufen <= 0) {
			if (++ohneKampfTicks > 100) {
				schlaegeBisLaufen = 2 + zufall.nextInt(4);
				ohneKampfTicks = 0;
			}
		} else {
			ohneKampfTicks = 0;
		}

		Entity feind = naechsterFeind(mc, p);
		if (feind != null && schlaegeBisLaufen > 0) {
			mc.options.keyUp.setDown(false); // zum Schlagen stehen bleiben
			weg.clear();
			kaempfen(mc, p, feind);
			return;
		}
		if (feind == null && zuNah != null && schlaegeBisLaufen > 0 && zurueckStarten(p, mc.options, zuNah)) {
			return; // Speer: Mob zu nah, erst einen Schritt Abstand nehmen
		}
		ziel = null;
		laufen(mc, p);
	}

	// ---- Schlagen ----------------------------------------------------------

	/** Reichweite der Waffe in der Hand (Speer: 2 bis 4,5 Blöcke, sonst 0 bis 3). */
	private static AttackRange reichweite(LocalPlayer p) {
		AttackRange r = p.getMainHandItem().get(DataComponents.ATTACK_RANGE);
		return r != null ? r : AttackRange.defaultFor(p);
	}

	/**
	 * Nächstes Monster, das die Waffe auch wirklich trifft.
	 * Gemessen wird wie im Spiel: vom Auge bis zur nächsten Stelle am Mob.
	 * Steht ein Mob für den Speer zu nah, merkt er sich den in {@link #zuNah}.
	 */
	private Entity naechsterFeind(Minecraft mc, LocalPlayer p) {
		AttackRange r = reichweite(p);
		double nah = r.effectiveMinRange(p);
		double weit = r.effectiveMaxRange(p);
		Vec3 auge = p.getEyePosition();
		AABB box = p.getBoundingBox().inflate(weit + 1);
		Entity bester = null;
		double besteDistanz = Double.MAX_VALUE;
		zuNah = null;
		double zuNahDistanz = Double.MAX_VALUE;
		for (Entity e : mc.level.getEntities(p, box, e -> e instanceof LivingEntity)) {
			if (!e.isAlive() || e.getType().getCategory() != MobCategory.MONSTER) {
				continue; // nur Monster: niemals Spieler, Tiere, Dorfbewohner oder Golems
			}
			double d = Math.sqrt(e.getBoundingBox().distanceToSqr(auge));
			if (nah > 0 && d < nah + 0.2) {
				if (d < zuNahDistanz) { // zu nah für den Speer
					zuNah = e;
					zuNahDistanz = d;
				}
				continue;
			}
			if (d <= weit - 0.25 && d < besteDistanz) {
				bester = e;
				besteDistanz = d;
			}
		}
		return bester;
	}

	/**
	 * Wohin er beim Schlagen schaut: auf den Körper, ungefähr auf Augenhöhe,
	 * aber nie höher als 60 % vom Mob (also nie auf den Kopf) und nie tiefer als 25 %.
	 */
	private Vec3 zielPunkt(LocalPlayer p, Entity e) {
		AABB b = e.getBoundingBox();
		double h = b.maxY - b.minY;
		double y = Mth.clamp(p.getEyeY() + zielVersatzY, b.minY + h * 0.25, b.minY + h * 0.6);
		Vec3 m = b.getCenter();
		return new Vec3(m.x + zielVersatzX, y, m.z + zielVersatzZ);
	}

	private void kaempfen(Minecraft mc, LocalPlayer p, Entity feind) {
		if (feind != ziel) {
			ziel = feind;
			reaktionTicks = 3 + zufall.nextInt(7); // 150 bis 450 ms Reaktionszeit
			neuenZielpunkt();
		}
		// auf den Mob schauen (nicht immer genau dieselbe Stelle)
		schauAuf(p, zielPunkt(p, feind));

		if (reaktionTicks > 0) {
			reaktionTicks--;
			return;
		}
		if (schlagPause > 0) {
			schlagPause--;
			return;
		}
		// Der Speer schlägt nur ganz aufgeladen zu (sonst passiert beim Klick gar nichts), das Schwert ab 90 %.
		float mindestLadung = p.getMainHandItem().getOrDefault(DataComponents.MINIMUM_ATTACK_CHARGE, 0f);
		if (p.getAttackStrengthScale(0.0f) < (mindestLadung > 0f ? 1.0f : 0.9f)) {
			return; // Waffe lädt noch auf
		}
		boolean zielt = mc.hitResult instanceof EntityHitResult treffer && treffer.getEntity() == feind;
		if (!zielt && !Float.isNaN(wunschYaw) && winkelAbstand(p.getYRot(), wunschYaw) > 6) {
			return; // Kamera dreht noch
		}

		double w = zufall.nextDouble();
		if (w < 0.07) {
			// daneben schlagen: kurz woanders hin schauen und in die Luft hauen
			wunschYaw += (zufall.nextBoolean() ? 1 : -1) * (5 + zufall.nextFloat() * 6);
			wunschPitch -= 2 + zufall.nextFloat() * 4;
			klick(mc.options.keyAttack);
		} else if (w > 0.04 + 0.07) {
			klick(mc.options.keyAttack); // echter Linksklick
			geschlagen.add(feind);
		}
		// sonst: ganz selten einfach nicht schlagen

		schlagPause = zufall.nextDouble() < 0.1 ? 20 + zufall.nextInt(15) : 12 + zufall.nextInt(8); // 620-950 ms, manchmal länger
		neuenZielpunkt();
		if (--schlaegeBisLaufen <= 0) {
			pauseTicks = 0; // nach ein paar Schlägen ein Stück laufen
		}
	}

	private void neuenZielpunkt() {
		zielVersatzX = (zufall.nextDouble() - 0.5) * 0.25;
		zielVersatzZ = (zufall.nextDouble() - 0.5) * 0.25;
		zielVersatzY = -0.45 + zufall.nextDouble() * 0.4; // etwas unter Augenhöhe, nicht nach oben
	}

	// ---- Speer: Abstand halten ----------------------------------------------

	/**
	 * Ein Mob steht zu nah für den Speer: kurz rückwärts (oder schräg/seitlich) gehen,
	 * aber nur, wenn die Fläche dort weitergeht. Gibt true zurück, wenn er ausweicht.
	 */
	private boolean zurueckStarten(LocalPlayer p, Options o, Entity naher) {
		schauAuf(p, zielPunkt(p, naher));
		double rad = Math.toRadians(p.getYRot());
		double vorX = -Math.sin(rad), vorZ = Math.cos(rad);
		double linksX = Math.cos(rad), linksZ = Math.sin(rad);
		double wegX = p.getX() - naher.getX(), wegZ = p.getZ() - naher.getZ(); // vom Mob weg
		// {zurück, links, rechts}; die schrägen und seitlichen Richtungen in zufälliger Reihenfolge
		boolean l = zufall.nextBoolean();
		int[][] tasten = {{1, 0, 0}, {1, l ? 1 : 0, l ? 0 : 1}, {1, l ? 0 : 1, l ? 1 : 0}, {0, l ? 1 : 0, l ? 0 : 1}, {0, l ? 0 : 1, l ? 1 : 0}};
		for (int[] t : tasten) {
			double dx = -vorX * t[0] + linksX * (t[1] - t[2]);
			double dz = -vorZ * t[0] + linksZ * (t[1] - t[2]);
			double len = Math.sqrt(dx * dx + dz * dz);
			dx /= len;
			dz /= len;
			if (dx * wegX + dz * wegZ <= 0) {
				continue; // würde nicht weiter weg führen
			}
			if (bereich.erlaubt(Mth.floor(p.getX() + dx * 0.5), Mth.floor(p.getZ() + dz * 0.5))
					&& bereich.erlaubt(Mth.floor(p.getX() + dx * 1.0), Mth.floor(p.getZ() + dz * 1.0))) {
				o.keyUp.setDown(false);
				weg.clear();
				o.keyDown.setDown(t[0] == 1);
				o.keyLeft.setDown(t[1] == 1);
				o.keyRight.setDown(t[2] == 1);
				rueckTicks = 4 + zufall.nextInt(5);
				return true;
			}
		}
		return false;
	}

	/** Läuft gerade ein Rückwärtsschritt? Dann weitermachen, bis er fertig ist oder die Fläche endet. */
	private boolean zurueckweichen(LocalPlayer p, Options o) {
		if (rueckTicks <= 0) {
			return false;
		}
		rueckTicks--;
		Vec3 v = p.getDeltaMovement();
		boolean sicher = bereich.erlaubt(Mth.floor(p.getX() + v.x * 3), Mth.floor(p.getZ() + v.z * 3));
		if (rueckTicks == 0 || !sicher) {
			rueckTicks = 0;
			o.keyDown.setDown(false);
			o.keyLeft.setDown(false);
			o.keyRight.setDown(false);
			return false;
		}
		if (zuNah != null && zuNah.isAlive()) {
			schauAuf(p, zielPunkt(p, zuNah)); // dabei weiter auf den Mob schauen
		}
		return true;
	}

	private void killsZaehlen() {
		geschlagen.removeIf(e -> {
			if (!e.isAlive()) {
				kills++;
				return true;
			}
			return e.isRemoved();
		});
	}

	private static boolean istWaffe(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		for (String w : WAFFEN) {
			if (w.equals(name)) {
				return true;
			}
		}
		return false;
	}

	private void waffeNehmen(LocalPlayer p) {
		// Hält er schon eine Waffe (z. B. deinen Speer)? Dann bleibt die in der Hand.
		if (istWaffe(p.getInventory().getItem(p.getInventory().getSelectedSlot()))) {
			return;
		}
		for (String name : WAFFEN) {
			for (int slot = 0; slot < 9; slot++) {
				ItemStack stack = p.getInventory().getItem(slot);
				if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals(name)) {
					p.getInventory().setSelectedSlot(slot);
					return;
				}
			}
		}
	}

	// ---- Essen -------------------------------------------------------------

	/** Gibt true zurück, solange gerade gegessen wird. */
	private boolean essen(Minecraft mc, LocalPlayer p) {
		if (essTicks >= 0) {
			essTicks++;
			mc.options.keyUse.setDown(true);
			boolean fertig = essTicks > 10 && !p.isUsingItem();
			if (fertig || essTicks > 80) {
				mc.options.keyUse.setDown(false);
				essTicks = -1;
				waffeNehmen(p);
			}
			return true;
		}
		if (p.getFoodData().getFoodLevel() >= HUNGER_GRENZE) {
			return false;
		}
		for (int slot = 0; slot < 9; slot++) {
			ItemStack stack = p.getInventory().getItem(slot);
			String name = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			if (!stack.isEmpty() && stack.has(DataComponents.FOOD)
					&& !name.equals("rotten_flesh") && !name.equals("spider_eye") && !name.equals("poisonous_potato")) {
				tastenLoslassen(mc.options);
				p.getInventory().setSelectedSlot(slot);
				essTicks = 0;
				return true;
			}
		}
		return false;
	}

	// ---- Beute zu den Mobs werfen ------------------------------------------

	/** Enderperlen, Enderaugen und alles mit "Perle", "Schriftrolle" oder "Auge" im Namen. Nie Waffen, Rüstung oder Essen. */
	private static boolean istWurfsache(ItemStack s) {
		if (s.isEmpty() || s.isDamageableItem() || s.has(DataComponents.FOOD)) {
			return false;
		}
		String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
		if (id.equals("ender_pearl") || id.equals("ender_eye")) {
			return true;
		}
		String name = s.getHoverName().getString().toLowerCase(Locale.ROOT);
		return name.contains("perle") || name.contains("schriftrolle") || name.contains("auge");
	}

	private static String schluessel(ItemStack s) {
		return BuiltInRegistries.ITEM.getKey(s.getItem()) + "|" + s.getHoverName().getString();
	}

	private static Map<String, Integer> wurfsachenZaehlen(LocalPlayer p) {
		Map<String, Integer> menge = new HashMap<>();
		for (int i = 0; i < 36; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (istWurfsache(s)) {
				menge.merge(schluessel(s), s.getCount(), Integer::sum);
			}
		}
		return menge;
	}

	/** Was seit dem Einschalten dazugekommen ist (nur das wird geworfen). */
	private Map<String, Integer> ueberschuss(LocalPlayer p) {
		Map<String, Integer> jetzt = wurfsachenZaehlen(p);
		startMenge.replaceAll((k, v) -> Math.min(v, jetzt.getOrDefault(k, 0)));
		Map<String, Integer> mehr = new HashMap<>();
		for (Map.Entry<String, Integer> e : jetzt.entrySet()) {
			int zuviel = e.getValue() - startMenge.getOrDefault(e.getKey(), 0);
			if (zuviel > 0) {
				mehr.put(e.getKey(), zuviel);
			}
		}
		return mehr;
	}

	/** Wohin geworfen wird: in den Mob-Bereich, sonst dahin, wo die Monster stehen. */
	private Vec3 wurfZiel(Minecraft mc, LocalPlayer p) {
		double[] mitte = bereich.mobMitte();
		if (mitte != null) {
			return new Vec3(mitte[0], mitte[1] + 1.0, mitte[2]);
		}
		Entity naechstes = null;
		double beste = Double.MAX_VALUE;
		for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(10), e -> e instanceof LivingEntity)) {
			if (e.isAlive() && e.getType().getCategory() == MobCategory.MONSTER && e.distanceToSqr(p) < beste) {
				naechstes = e;
				beste = e.distanceToSqr(p);
			}
		}
		return naechstes == null ? null : zielPunkt(p, naechstes);
	}

	/**
	 * Hat er beim Farmen Enderperlen, Schriftrollen oder Augen aufgesammelt, schaut er nach ein paar Sekunden
	 * zu den Mobs und wirft sie dort hin, damit der Trichter sie einsammelt. Gibt true zurück, solange er wirft.
	 */
	private boolean werfen(Minecraft mc, LocalPlayer p) {
		if (!bereich.werfen || mc.gameMode == null) {
			wirft = false;
			wurfWarten = -1;
			return false;
		}
		if (!wirft) {
			if (wurfWarten < 0) {
				if (++wurfPruefen >= 20) { // einmal pro Sekunde nachsehen
					wurfPruefen = 0;
					if (!ueberschuss(p).isEmpty()) {
						wurfWarten = 40 + zufall.nextInt(100); // nicht sofort, sondern nach 2 bis 7 Sekunden
					}
				}
				return false;
			}
			if (--wurfWarten > 0) {
				return false;
			}
			wurfWarten = -1;
			wirft = true;
			wurfTicks = 0;
			wurfRuhig = 0;
			wurfPause = 0;
			neuenZielpunkt();
		}

		Vec3 punkt = wurfZiel(mc, p);
		Map<String, Integer> mehr = ueberschuss(p);
		if (punkt == null || mehr.isEmpty() || ++wurfTicks > 200) {
			wirft = false; // nichts mehr zu werfen oder keine Mobs zu sehen
			return false;
		}
		mc.options.keyUp.setDown(false); // zum Werfen stehen bleiben
		weg.clear();
		schauAuf(p, punkt);
		boolean ruhig = winkelAbstand(p.getYRot(), wunschYaw) < 5
				&& Math.abs(p.getXRot() - Mth.clamp(wunschPitch, -25f, 60f)) < 5;
		wurfRuhig = ruhig ? wurfRuhig + 1 : 0;
		if (wurfPause > 0) {
			wurfPause--;
			return true;
		}
		if (wurfRuhig < 3) {
			return true; // erst richtig hinschauen
		}
		for (int i = 0; i < 36; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (!istWurfsache(s)) {
				continue;
			}
			int zuviel = mehr.getOrDefault(schluessel(s), 0);
			if (zuviel <= 0) {
				continue;
			}
			boolean ganzerStapel = s.getCount() <= zuviel;
			int menueSlot = i < 9 ? 36 + i : i; // Hotbar liegt im Inventar-Menü auf 36 bis 44
			mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, menueSlot, ganzerStapel ? 1 : 0, ContainerInput.THROW, p);
			wurfPause = 5 + zufall.nextInt(10);
			return true;
		}
		wirft = false;
		return false;
	}

	// ---- Laufen ------------------------------------------------------------

	private void laufen(Minecraft mc, LocalPlayer p) {
		Options o = mc.options;
		Bereich.Feld hier = new Bereich.Feld(Mth.floor(p.getX()), Mth.floor(p.getZ()));

		if (weg.isEmpty()) {
			o.keyUp.setDown(false);
			if (pauseTicks > 0) {
				pauseTicks--;
				if (zufall.nextDouble() < 0.012) { // seltener umschauen
					umschauen(p);
				}
				return;
			}
			neuenWegPlanen(hier);
			return;
		}

		// unterwegs ab und zu kurz stehen bleiben
		laufTicks++;
		if (laufTicks == anhaltenBeiTick) {
			o.keyUp.setDown(false);
			weg.clear();
			pauseTicks = 6 + zufall.nextInt(14);
			return;
		}

		Bereich.Feld naechstes = weg.get(0);
		double zx = naechstes.x() + 0.5;
		double zz = naechstes.z() + 0.5;
		double dx = zx - p.getX();
		double dz = zz - p.getZ();
		double abstand = Math.sqrt(dx * dx + dz * dz);

		if (abstand < 0.3) {
			weg.remove(0);
			if (weg.isEmpty()) {
				o.keyUp.setDown(false);
				ankommen();
			}
			return;
		}

		wunschYaw = yawZu(dx, dz);
		if (zufall.nextDouble() < 0.02) {
			wunschPitch = 10 + zufall.nextFloat() * 20;
		}

		// Sicherheit: würde der nächste Schritt auf ein verbotenes Feld führen? Dann sofort stehen bleiben.
		Vec3 v = p.getDeltaMovement();
		int vorX = Mth.floor(p.getX() + v.x * 3);
		int vorZ = Mth.floor(p.getZ() + v.z * 3);
		if (!bereich.erlaubt(vorX, vorZ)) {
			o.keyUp.setDown(false);
			weg.clear();
			pauseTicks = 5;
			return;
		}

		// erst losgehen, wenn er ungefähr in die richtige Richtung schaut
		o.keyUp.setDown(winkelAbstand(p.getYRot(), wunschYaw) < 30);

		if (laufTicks > 120) { // hängt fest
			o.keyUp.setDown(false);
			weg.clear();
		}
	}

	private void neuenWegPlanen(Bereich.Feld hier) {
		List<Bereich.Feld> felder = bereich.freieFelder();
		if (felder.isEmpty()) {
			return;
		}
		List<Bereich.Feld> auswahl;
		List<Bereich.Feld> amMob = bereich.felderAmMob();
		if (zufall.nextDouble() < 0.3) {
			// nur ein kleiner Schritt zum Nachbarfeld
			auswahl = new ArrayList<>();
			for (Bereich.Feld f : felder) {
				if (Math.abs(f.x() - hier.x()) <= 1 && Math.abs(f.z() - hier.z()) <= 1 && !f.equals(hier)) {
					auswahl.add(f);
				}
			}
		} else if (!amMob.isEmpty() && zufall.nextDouble() < NAH_AM_MOB_ANTEIL) {
			auswahl = new ArrayList<>(amMob);
		} else {
			auswahl = new ArrayList<>(felder);
		}
		auswahl.remove(hier);
		if (letztesZiel != null && auswahl.size() > 1) {
			auswahl.remove(letztesZiel);
		}
		if (auswahl.isEmpty()) {
			auswahl = felder;
		}
		Bereich.Feld ziel = auswahl.get(zufall.nextInt(auswahl.size()));
		List<Bereich.Feld> neuerWeg = bereich.weg(hier, ziel);
		if (neuerWeg == null || neuerWeg.isEmpty()) {
			pauseTicks = 10;
			return;
		}
		letztesZiel = ziel;
		weg = new ArrayList<>(neuerWeg);
		laufTicks = 0;
		anhaltenBeiTick = zufall.nextDouble() < 0.15 ? 6 + zufall.nextInt(12) : -1;
	}

	private void ankommen() {
		// unterschiedlich lange Pause: meist 0,4 bis 2,5 s, manchmal 3 bis 7 s
		pauseTicks = zufall.nextDouble() < 0.12 ? 60 + zufall.nextInt(80) : 8 + zufall.nextInt(42);
		schlaegeBisLaufen = 3 + zufall.nextInt(6);
		if (zufall.nextDouble() < 0.35) {
			umschauen(Minecraft.getInstance().player);
		}
	}

	// ---- Kamera ------------------------------------------------------------

	private void umschauen(LocalPlayer p) {
		if (p == null) {
			return;
		}
		double[] mitte = bereich.mobMitte();
		if (mitte != null && zufall.nextDouble() < 0.7) {
			double dx = mitte[0] + (zufall.nextDouble() - 0.5) * 2 - p.getX();
			double dz = mitte[2] + (zufall.nextDouble() - 0.5) * 2 - p.getZ();
			wunschYaw = yawZu(dx, dz);
			wunschPitch = 12 + zufall.nextFloat() * 18;
		} else {
			wunschYaw = p.getYRot() + (zufall.nextFloat() - 0.5f) * 50; // nur ein Stück zur Seite
			wunschPitch = 8 + zufall.nextFloat() * 20;                  // nicht an die Decke schauen
		}
	}

	private void schauAuf(LocalPlayer p, Vec3 punkt) {
		Vec3 auge = p.getEyePosition();
		double dx = punkt.x - auge.x;
		double dy = punkt.y - auge.y;
		double dz = punkt.z - auge.z;
		wunschYaw = yawZu(dx, dz);
		wunschPitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
	}

	/** Dreht die Kamera weich und leicht unregelmäßig zum Wunschwinkel. */
	private void kameraBewegen(LocalPlayer p) {
		if (Float.isNaN(wunschYaw)) {
			wunschYaw = p.getYRot();
		}
		float yaw = p.getYRot();
		// Bei einer großen Drehung schießt die "Maus" manchmal ein paar Grad übers Ziel hinaus
		// und korrigiert dann zurück, wie ein Mensch.
		if (Float.isNaN(letzterWunschYaw) || winkelAbstand(letzterWunschYaw, wunschYaw) > 15) {
			float weite = Math.abs(Mth.wrapDegrees(wunschYaw - yaw));
			if (weite > 20 && zufall.nextDouble() < 0.35) {
				ueberschuss = Math.signum(Mth.wrapDegrees(wunschYaw - yaw)) * (1.5f + zufall.nextFloat() * Math.min(4f, weite * 0.08f));
				ueberschussTicks = 3 + zufall.nextInt(4);
			}
		}
		letzterWunschYaw = wunschYaw;
		float ziel = wunschYaw;
		if (ueberschussTicks > 0) {
			ziel += ueberschuss;
			ueberschussTicks--;
		}
		float diff = Mth.wrapDegrees(ziel - yaw);
		float tempo = 0.14f + zufall.nextFloat() * 0.12f;          // 14 bis 26 % pro Tick: ruhig und weich
		float schritt = Mth.clamp(diff * tempo, -14f, 14f);
		schritt += (zufall.nextFloat() - 0.5f) * 0.3f;              // ganz leichtes Zittern wie mit der Maus
		p.setYRot(yaw + schritt);

		float pitch = p.getXRot();
		float pDiff = Mth.clamp(wunschPitch, -25f, 60f) - pitch;   // nie steil nach oben
		p.setXRot(Mth.clamp(pitch + pDiff * tempo + (zufall.nextFloat() - 0.5f) * 0.2f, -90f, 90f));
	}

	private static float yawZu(double dx, double dz) {
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	private static float winkelAbstand(float a, float b) {
		return Math.abs(Mth.wrapDegrees(a - b));
	}

	// ---- Tasten ------------------------------------------------------------

	private void kleinigkeiten(Options o) {
		if (springenTicks > 0) {
			if (--springenTicks == 0) {
				o.keyJump.setDown(false);
			}
		} else if (zufall.nextDouble() < 0.002) { // etwa alle 25 Sekunden mal springen
			o.keyJump.setDown(true);
			springenTicks = 3 + zufall.nextInt(5);
		}
		if (schleichenTicks > 0) {
			if (--schleichenTicks == 0) {
				o.keyShift.setDown(false);
			}
		} else if (zufall.nextDouble() < 0.0015) { // ab und zu kurz schleichen
			o.keyShift.setDown(true);
			schleichenTicks = 6 + zufall.nextInt(18);
		}
	}

	private static void klick(KeyMapping taste) {
		KeyMappingAccessor a = (KeyMappingAccessor) taste;
		a.tittow_setClickCount(a.tittow_getClickCount() + 1);
	}

	private static void tastenLoslassen(Options o) {
		o.keyUp.setDown(false);
		o.keyDown.setDown(false);
		o.keyLeft.setDown(false);
		o.keyRight.setDown(false);
		o.keyJump.setDown(false);
		o.keyShift.setDown(false);
		o.keyUse.setDown(false);
	}
}
