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
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
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

	// Farm-Modus: am Rand der Mob-Stelle stehen und einfach zuschlagen
	private final List<int[]> mobSaeulen = new ArrayList<>(); // x/z der Mob-Stelle (Trichter, Mob-Bereich oder gesperrte Ecke)
	private double mobBoden = 0;            // Höhe, auf der die Mobs stehen
	private String stelleArt = "keine";
	private int stelleSuchenTicks = 0;
	private Vec3 farmZiel = null;           // wohin er gerade schlägt
	private Entity farmMob = null;          // Mob, dem er gerade mit der Maus folgt
	private int farmSchlaege = 8;           // Schläge bis zur nächsten kleinen Entscheidung
	private int farmPause = 0;              // kurze Pause, in der er nicht schlägt
	private float ladungNoetig = 0.95f;

	// Beute (Power Orbs, Scrolls, Enderperlen) bei vollem Inventar zu den Mobs werfen
	private final Map<String, Integer> startMenge = new HashMap<>();
	private Vec3 letzteMobStelle = null;   // wo er zuletzt gekämpft hat
	private int wurfPruefen = 0;
	private int wurfWarten = -1;           // Ticks bis zum Losgehen, -1 = nichts geplant
	private int wurfPhase = 0;             // 0 = nichts, 1 = zum Rand gehen, 2 = werfen
	private Vec3 wurfPunkt = null;         // Mitte der Trichter (null = keine Trichter gefunden, dann zu den Mobs)
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
		wurfPhase = 0;
		wurfWarten = -1;
		letzteMobStelle = null;
		stelleSuchenTicks = 0;
		farmZiel = null;
		farmMob = null;
		farmPause = 0;
		farmSchlaege = 6 + zufall.nextInt(10);
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

		// Weiß er, wo die Mobs spawnen (Trichter, Mob-Bereich oder gesperrte Ecke)? Dann einfach dort zuschlagen.
		if (--stelleSuchenTicks <= 0) {
			stelleSuchenTicks = 600; // alle 30 Sekunden neu nachsehen
			mobStelleBestimmen(mc, p);
		}
		if (!mobSaeulen.isEmpty()) {
			farmen(mc, p);
			return;
		}

		// Sonst wie früher: herumlaufen und Monster in der Nähe suchen
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
				if (d < zuNahDistanz && sichtbarerPunkt(mc, p, e) != null) { // zu nah für den Speer
					zuNah = e;
					zuNahDistanz = d;
				}
				continue;
			}
			// nur Mobs, die er wirklich sieht: nicht durch die Decke, den Boden oder eine Wand
			if (d <= weit - 0.25 && d < besteDistanz && sichtbarerPunkt(mc, p, e) != null) {
				bester = e;
				besteDistanz = d;
			}
		}
		return bester;
	}

	/**
	 * Eine Stelle am Körper des Mobs, die er wirklich sieht (kein Block dazwischen).
	 * Erst der normale Zielpunkt, sonst etwas höher oder tiefer am Körper. Null, wenn er den Mob nicht sieht.
	 */
	private Vec3 sichtbarerPunkt(Minecraft mc, LocalPlayer p, Entity e) {
		Vec3 auge = p.getEyePosition();
		Vec3 wunsch = zielPunkt(p, e);
		if (frei(mc, p, auge, wunsch)) {
			return wunsch;
		}
		AABB b = e.getBoundingBox();
		double h = b.maxY - b.minY;
		Vec3 m = b.getCenter();
		for (double anteil : new double[] {0.3, 0.45, 0.6}) {
			Vec3 v = new Vec3(m.x, b.minY + h * anteil, m.z);
			if (frei(mc, p, auge, v)) {
				return v;
			}
		}
		return null;
	}

	/** Kein Block zwischen den beiden Punkten (so wie das Fadenkreuz im Spiel Blöcke trifft)? */
	private static boolean frei(Minecraft mc, LocalPlayer p, Vec3 von, Vec3 bis) {
		return mc.level.clip(new ClipContext(von, bis, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p))
				.getType() == HitResult.Type.MISS;
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
			letzteMobStelle = feind.position(); // merken, wo die Mobs stehen (zum Werfen)
			reaktionTicks = 3 + zufall.nextInt(7); // 150 bis 450 ms Reaktionszeit
			neuenZielpunkt();
		}
		// auf den Mob schauen (nicht immer genau dieselbe Stelle), und zwar auf eine Stelle, die er sieht
		Vec3 sichtbar = sichtbarerPunkt(mc, p, feind);
		schauAuf(p, sichtbar != null ? sichtbar : zielPunkt(p, feind));

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

	// ---- Farm-Modus: am Rand stehen und einfach zuschlagen -----------------

	/**
	 * Wo die Mobs spawnen: 1. der Mob-Bereich, wenn gesetzt, 2. die Trichter (z. B. 3x3 mit Teppich),
	 * 3. die gesperrten Felder in der Karte, die an die Lauf-Fläche grenzen (z. B. die Ecke im L).
	 */
	private void mobStelleBestimmen(Minecraft mc, LocalPlayer p) {
		mobSaeulen.clear();
		stelleArt = "keine";
		if (bereich.mobsGesetzt()) {
			for (int x = Math.min(bereich.mobs1[0], bereich.mobs2[0]); x <= Math.max(bereich.mobs1[0], bereich.mobs2[0]); x++) {
				for (int z = Math.min(bereich.mobs1[2], bereich.mobs2[2]); z <= Math.max(bereich.mobs1[2], bereich.mobs2[2]); z++) {
					mobSaeulen.add(new int[] {x, z});
				}
			}
			mobBoden = Math.min(bereich.mobs1[1], bereich.mobs2[1]);
			stelleArt = "Mob-Bereich";
			return;
		}
		List<BlockPos> trichter = trichterSuchen(mc, p);
		if (!trichter.isEmpty()) {
			int oben = Integer.MIN_VALUE;
			for (BlockPos t : trichter) {
				if (mobSaeulen.stream().noneMatch(s -> s[0] == t.getX() && s[1] == t.getZ())) {
					mobSaeulen.add(new int[] {t.getX(), t.getZ()});
				}
				oben = Math.max(oben, t.getY() + 1);
			}
			mobBoden = oben;
			stelleArt = trichter.size() + " Trichter";
			return;
		}
		if (bereich.flaecheGesetzt()) {
			int[] g = bereich.grenzen();
			for (int x = g[0]; x <= g[1]; x++) {
				for (int z = g[2]; z <= g[3]; z++) {
					if (!bereich.erlaubt(x, z) && (bereich.erlaubt(x + 1, z) || bereich.erlaubt(x - 1, z)
							|| bereich.erlaubt(x, z + 1) || bereich.erlaubt(x, z - 1))) {
						mobSaeulen.add(new int[] {x, z});
					}
				}
			}
			if (!mobSaeulen.isEmpty()) {
				mobBoden = p.getBlockY();
				stelleArt = "gesperrte Ecke";
			}
		}
	}

	/** Für das Panel: wo er hinschlägt. */
	public String stelleText(Minecraft mc) {
		if (mc.player != null && mc.level != null) {
			mobStelleBestimmen(mc, mc.player);
		}
		return mobSaeulen.isEmpty() ? "Schlag-Stelle: keine, sucht Mobs" : "Schlag-Stelle: " + stelleArt;
	}

	/** Freie Felder direkt am Rand der Mob-Stelle (ein Feld außen herum). Mit Ecken oder nur die Seiten. */
	private List<Bereich.Feld> ringFelder(boolean mitEcken) {
		List<Bereich.Feld> ring = new ArrayList<>();
		for (Bereich.Feld f : bereich.freieFelder()) {
			boolean seite = false;
			boolean ecke = false;
			boolean drauf = false;
			for (int[] s : mobSaeulen) {
				int ax = Math.abs(s[0] - f.x());
				int az = Math.abs(s[1] - f.z());
				if (ax == 0 && az == 0) {
					drauf = true;
					break;
				}
				if (ax + az == 1) {
					seite = true;
				} else if (ax == 1 && az == 1) {
					ecke = true;
				}
			}
			if (!drauf && (seite || (mitEcken && ecke))) {
				ring.add(f);
			}
		}
		return ring;
	}

	private static Bereich.Feld naechstes(List<Bereich.Feld> felder, LocalPlayer p) {
		Bereich.Feld bestes = null;
		double beste = Double.MAX_VALUE;
		for (Bereich.Feld f : felder) {
			double d = Math.pow(f.x() + 0.5 - p.getX(), 2) + Math.pow(f.z() + 0.5 - p.getZ(), 2);
			if (d < beste) {
				bestes = f;
				beste = d;
			}
		}
		return bestes;
	}

	/**
	 * Steht am Rand der Mob-Stelle und schlägt einfach immer wieder hinein, wie ein Mensch:
	 * schaut hin, wartet, bis die Waffe geladen ist, klickt mit leicht unregelmäßigem Takt,
	 * bewegt die Maus ein bisschen, macht ab und zu Pausen, schaut kurz weg oder geht ein Feld weiter.
	 */
	private void farmen(Minecraft mc, LocalPlayer p) {
		Options o = mc.options;
		Bereich.Feld hier = new Bereich.Feld(Mth.floor(p.getX()), Mth.floor(p.getZ()));

		// unterwegs zu einem anderen Feld am Rand
		if (!weg.isEmpty()) {
			laufTicks++;
			int s = schritt(o, p);
			if (s != 0 || laufTicks > 120) {
				o.keyUp.setDown(false);
				weg.clear();
				farmPause = 4 + zufall.nextInt(10); // kurz ankommen
			}
			return;
		}

		// nicht am Rand? Dann zum nächsten Feld am Rand gehen (am liebsten an eine Seite)
		List<Bereich.Feld> ring = ringFelder(true);
		if (!ring.isEmpty() && !ring.contains(hier)) {
			List<Bereich.Feld> seiten = ringFelder(false);
			Bereich.Feld ziel = naechstes(seiten.isEmpty() ? ring : seiten, p);
			List<Bereich.Feld> w = bereich.weg(hier, ziel);
			if (w != null && !w.isEmpty()) {
				weg = new ArrayList<>(w);
				laufTicks = 0;
				return;
			}
			// nicht erreichbar: dann eben von hier aus schlagen
		}

		// Pause: nicht schlagen, nur ein bisschen schauen
		if (farmPause > 0) {
			farmPause--;
			return;
		}

		// zielen (folgt einem Mob, wenn er gerade einem folgt, sonst eine Stelle über den Trichtern)
		if (farmZiel == null || (farmMob != null && !farmMob.isAlive())) {
			neuesFarmZiel(mc, p);
		}
		Vec3 punkt = farmZiel;
		if (farmMob != null) {
			Vec3 sp = sichtbarerPunkt(mc, p, farmMob);
			if (sp == null) {
				neuesFarmZiel(mc, p);
				punkt = farmZiel;
			} else {
				punkt = sp;
			}
		}
		schauAuf(p, punkt);

		// schlagen
		if (schlagPause > 0) {
			schlagPause--;
			return;
		}
		float mindestLadung = p.getMainHandItem().getOrDefault(DataComponents.MINIMUM_ATTACK_CHARGE, 0f);
		if (p.getAttackStrengthScale(0.0f) < (mindestLadung > 0f ? 1.0f : ladungNoetig)) {
			return; // Waffe lädt noch
		}
		if (winkelAbstand(p.getYRot(), wunschYaw) > 8 || Math.abs(p.getXRot() - Mth.clamp(wunschPitch, -25f, 60f)) > 8) {
			return; // Maus ist noch unterwegs
		}
		if (!darfKlicken(mc, p)) {
			neuesFarmZiel(mc, p); // da ist etwas, das er nicht schlagen soll: woanders hinzielen
			schlagPause = 2 + zufall.nextInt(4);
			return;
		}
		klick(o.keyAttack);
		if (mc.hitResult instanceof EntityHitResult treffer) {
			geschlagen.add(treffer.getEntity());
		}

		// menschlicher Takt nach dem Tempo der Waffe: meist kurz nach dem Aufladen, manchmal etwas später
		int takt = Math.round(p.getCurrentItemAttackStrengthDelay() * 0.85f);
		schlagPause = takt + (zufall.nextDouble() < 0.08 ? 8 + zufall.nextInt(18) : zufall.nextInt(5));
		ladungNoetig = 0.9f + zufall.nextFloat() * 0.1f;
		neuenZielpunkt();
		if (zufall.nextDouble() < 0.3) {
			neuesFarmZiel(mc, p); // Maus wandert ein bisschen
		}
		if (--farmSchlaege <= 0) {
			farmEntscheiden(p, hier);
		}
	}

	/** Nach ein paar Schlägen: weiterschlagen, kurze Pause, längere Pause, wegschauen oder ein Feld weiter gehen. */
	private void farmEntscheiden(LocalPlayer p, Bereich.Feld hier) {
		farmSchlaege = 6 + zufall.nextInt(16);
		double w = zufall.nextDouble();
		if (w < 0.05) {
			farmPause = 120 + zufall.nextInt(120); // selten 6 bis 12 Sekunden Pause
		} else if (w < 0.22) {
			farmPause = 20 + zufall.nextInt(60);   // kurze Pause, 1 bis 4 Sekunden
		} else if (w < 0.40) {
			// ein anderes Feld am Rand, nicht zu weit weg
			List<Bereich.Feld> nah = new ArrayList<>();
			for (Bereich.Feld f : ringFelder(true)) {
				if (!f.equals(hier) && Math.abs(f.x() - hier.x()) <= 2 && Math.abs(f.z() - hier.z()) <= 2) {
					nah.add(f);
				}
			}
			if (!nah.isEmpty()) {
				List<Bereich.Feld> w2 = bereich.weg(hier, nah.get(zufall.nextInt(nah.size())));
				if (w2 != null && !w2.isEmpty()) {
					weg = new ArrayList<>(w2);
					laufTicks = 0;
				}
			}
		}
		if (farmPause > 0 && zufall.nextDouble() < 0.5) {
			// in der Pause kurz zur Seite schauen
			wunschYaw = p.getYRot() + (zufall.nextBoolean() ? 1 : -1) * (20 + zufall.nextFloat() * 40);
			wunschPitch = 5 + zufall.nextFloat() * 20;
			farmZiel = null; // danach neu hinschauen
		}
	}

	/**
	 * Neuer Punkt zum Hinschlagen: meistens ein Mob, den er gut sieht (wie ein Mensch, der auf einen Enderman zielt),
	 * sonst irgendeine Stelle über der Mob-Stelle, die mit der Waffe erreichbar ist, in Körperhöhe.
	 */
	private void neuesFarmZiel(Minecraft mc, LocalPlayer p) {
		farmMob = null;
		if (zufall.nextDouble() < 0.7) {
			Entity m = naechsterFeind(mc, p);
			if (m != null) {
				farmMob = m;
				farmZiel = m.position();
				return;
			}
		}
		AttackRange r = reichweite(p);
		double nah = r.effectiveMinRange(p);
		double weit = r.effectiveMaxRange(p);
		List<int[]> passend = new ArrayList<>();
		int[] naechste = null;
		double naechsteD = Double.MAX_VALUE;
		for (int[] s : mobSaeulen) {
			double d = Math.sqrt(Math.pow(s[0] + 0.5 - p.getX(), 2) + Math.pow(s[1] + 0.5 - p.getZ(), 2));
			if (d >= nah + 0.3 && d <= weit - 0.3) {
				passend.add(s);
			}
			if (d < naechsteD) {
				naechste = s;
				naechsteD = d;
			}
		}
		int[] s = passend.isEmpty() ? naechste : passend.get(zufall.nextInt(passend.size()));
		farmZiel = new Vec3(s[0] + 0.25 + zufall.nextDouble() * 0.5,
				mobBoden + 0.9 + zufall.nextDouble() * 0.7,
				s[1] + 0.25 + zufall.nextDouble() * 0.5);
	}

	/**
	 * Darf er jetzt klicken? Nie auf Spieler, Tiere, Dorfbewohner, Rahmen usw., nur auf Monster.
	 * In die Luft ist ok. Auf einen Block nur, wenn der davon nicht kaputtgeht (z. B. keine Fackel).
	 */
	private static boolean darfKlicken(Minecraft mc, LocalPlayer p) {
		HitResult h = mc.hitResult;
		if (h instanceof EntityHitResult t) {
			return t.getEntity().getType().getCategory() == MobCategory.MONSTER;
		}
		if (h instanceof BlockHitResult b && h.getType() == HitResult.Type.BLOCK) {
			return mc.level.getBlockState(b.getBlockPos()).getDestroyProgress(p, mc.level, b.getBlockPos()) < 0.5f;
		}
		return true;
	}

	// ---- Beute zu den Mobs werfen ------------------------------------------

	/** Power Orbs, Scrolls (z. B. "Scroll of ...") und Enderperlen. Nie Waffen, Rüstung oder Essen. */
	private static boolean istWurfsache(ItemStack s) {
		if (s.isEmpty() || s.isDamageableItem() || s.has(DataComponents.FOOD)) {
			return false;
		}
		if (BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().equals("ender_pearl")) {
			return true;
		}
		String name = s.getHoverName().getString().toLowerCase(Locale.ROOT);
		return name.contains("power orb") || name.contains("scroll")
				|| name.contains("enderperle") || name.contains("ender pearl");
	}

	private static boolean inventarVoll(LocalPlayer p) {
		for (int i = 0; i < 36; i++) {
			if (p.getInventory().getItem(i).isEmpty()) {
				return false;
			}
		}
		return true;
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

	/**
	 * Wo die Mobs stehen (Fußhöhe): Mitte vom Mob-Bereich, sonst die Gruppe um das nächste Monster,
	 * sonst die Stelle, an der er zuletzt gekämpft hat.
	 */
	private Vec3 mobStelle(Minecraft mc, LocalPlayer p) {
		double[] mitte = bereich.mobMitte();
		if (mitte != null) {
			return new Vec3(mitte[0], mitte[1], mitte[2]);
		}
		List<Entity> monster = mc.level.getEntities(p, p.getBoundingBox().inflate(12),
				e -> e instanceof LivingEntity && e.isAlive() && e.getType().getCategory() == MobCategory.MONSTER
						&& sichtbarerPunkt(mc, p, e) != null); // keine Mobs hinter der Decke
		Entity naechstes = null;
		double beste = Double.MAX_VALUE;
		for (Entity e : monster) {
			if (e.distanceToSqr(p) < beste) {
				naechstes = e;
				beste = e.distanceToSqr(p);
			}
		}
		if (naechstes == null) {
			return letzteMobStelle;
		}
		double sx = 0, sy = 0, sz = 0;
		int n = 0;
		for (Entity e : monster) {
			if (e.distanceToSqr(naechstes) < 9) { // alle bis 3 Blöcke um das nächste Monster
				sx += e.getX();
				sy += e.getY();
				sz += e.getZ();
				n++;
			}
		}
		return new Vec3(sx / n, sy / n, sz / n);
	}

	/** Das freie Feld am Rand, das am nächsten an den Mobs liegt. */
	private Bereich.Feld randFeld(Vec3 mobs) {
		List<Bereich.Feld> kandidaten = bereich.felderAmMob();
		if (kandidaten.isEmpty()) {
			kandidaten = bereich.freieFelder();
		}
		Bereich.Feld bestes = null;
		double beste = Double.MAX_VALUE;
		for (Bereich.Feld f : kandidaten) {
			double dx = f.x() + 0.5 - mobs.x;
			double dz = f.z() + 0.5 - mobs.z;
			if (dx * dx + dz * dz < beste) {
				bestes = f;
				beste = dx * dx + dz * dz;
			}
		}
		return bestes;
	}

	/**
	 * Sucht die Trichter unter den Mobs: Trichter bis 8 Blöcke um dich, über denen kein ganzer Block ist
	 * (Teppich darauf ist in Ordnung, Trichter unter einem Boden zählen nicht). Nimmt die zusammenhängende Gruppe um den nächsten Trichter,
	 * z. B. deine 3x3 Trichter.
	 */
	private List<BlockPos> trichterSuchen(Minecraft mc, LocalPlayer p) {
		BlockPos fuss = p.blockPosition();
		List<BlockPos> alle = new ArrayList<>();
		for (int dx = -8; dx <= 8; dx++) {
			for (int dz = -8; dz <= 8; dz++) {
				for (int dy = -3; dy <= 1; dy++) {
					BlockPos pos = fuss.offset(dx, dy, dz);
					// wie im Spiel: der Trichter holt Sachen von oben, solange darüber kein ganzer Block ist (Teppich ist ok)
					// Trichter unter einer Kiste, einem Fass oder einem anderen Trichter zählen nicht
					if (mc.level.getBlockState(pos).is(Blocks.HOPPER)
							&& !mc.level.getBlockState(pos.above()).isCollisionShapeFullBlock(mc.level, pos.above())
							&& !mc.level.getBlockState(pos.above()).hasBlockEntity()) {
						alle.add(pos);
					}
				}
			}
		}
		if (alle.isEmpty()) {
			return alle;
		}
		BlockPos start = alle.get(0);
		for (BlockPos t : alle) {
			if (t.distSqr(fuss) < start.distSqr(fuss)) {
				start = t;
			}
		}
		// alle Trichter, die mit dem nächsten zusammenhängen
		List<BlockPos> gruppe = new ArrayList<>();
		gruppe.add(start);
		for (int i = 0; i < gruppe.size(); i++) {
			BlockPos g = gruppe.get(i);
			for (BlockPos t : alle) {
				if (!gruppe.contains(t) && Math.abs(t.getX() - g.getX()) <= 1
						&& Math.abs(t.getZ() - g.getZ()) <= 1 && Math.abs(t.getY() - g.getY()) <= 1) {
					gruppe.add(t);
				}
			}
		}
		return gruppe;
	}

	/**
	 * Freies Feld direkt neben den Trichtern (ein Feld außen herum), das am nächsten bei dir ist.
	 * Felder an einer Seite sind besser als die schrägen Ecken. Null, wenn keins frei ist.
	 */
	private Bereich.Feld feldAmTrichter(List<BlockPos> trichter, LocalPlayer p) {
		Bereich.Feld bestes = null;
		double beste = Double.MAX_VALUE;
		for (Bereich.Feld f : bereich.freieFelder()) {
			boolean seite = false;
			boolean ecke = false;
			for (BlockPos t : trichter) {
				int ax = Math.abs(t.getX() - f.x());
				int az = Math.abs(t.getZ() - f.z());
				if (ax == 0 && az == 0) {
					seite = ecke = false;
					break; // auf dem Trichter selbst steht er nicht
				}
				if (ax + az == 1) {
					seite = true;
				} else if (ax == 1 && az == 1) {
					ecke = true;
				}
			}
			if (!seite && !ecke) {
				continue;
			}
			double dx = f.x() + 0.5 - p.getX();
			double dz = f.z() + 0.5 - p.getZ();
			double d = dx * dx + dz * dz + (seite ? 0 : 100); // Ecken nur, wenn keine Seite frei ist
			if (d < beste) {
				bestes = f;
				beste = d;
			}
		}
		return bestes;
	}

	/** Wohin er zielt: auf die Mitte der Trichter, etwas über der Oberkante, damit es flach hineinfliegt. */
	private static Vec3 trichterMitte(List<BlockPos> trichter, Bereich.Feld stand) {
		double sx = 0, sz = 0;
		int oben = Integer.MIN_VALUE;
		for (BlockPos t : trichter) {
			sx += t.getX() + 0.5;
			sz += t.getZ() + 0.5;
			oben = Math.max(oben, t.getY() + 1);
		}
		double mx = sx / trichter.size();
		double mz = sz / trichter.size();
		double weite = Math.sqrt(Math.pow(mx - (stand.x() + 0.5), 2) + Math.pow(mz - (stand.z() + 0.5), 2));
		return new Vec3(mx, oben + Mth.clamp(weite * 0.4, 0.2, 1.0), mz);
	}

	private void wurfBeenden() {
		wurfPhase = 0;
		pauseTicks = 10 + zufall.nextInt(20);
		schlaegeBisLaufen = 3 + zufall.nextInt(6);
	}

	/**
	 * Ist das Inventar voll und hat er beim Farmen Power Orbs, Scrolls oder Enderperlen aufgesammelt,
	 * geht er an den Rand zu den Mobs und wirft sie dort hinein, damit der Trichter sie einsammelt.
	 * Gibt true zurück, solange er damit beschäftigt ist.
	 */
	private boolean werfen(Minecraft mc, LocalPlayer p) {
		if (!bereich.werfen || mc.gameMode == null) {
			wurfPhase = 0;
			wurfWarten = -1;
			return false;
		}
		Options o = mc.options;
		if (wurfPhase == 0) {
			if (wurfWarten < 0) {
				if (++wurfPruefen >= 20) { // einmal pro Sekunde nachsehen
					wurfPruefen = 0;
					if (inventarVoll(p) && !ueberschuss(p).isEmpty()) {
						wurfWarten = 20 + zufall.nextInt(60); // nicht sofort, sondern nach 1 bis 4 Sekunden
					}
				}
				return false;
			}
			if (--wurfWarten > 0) {
				return false;
			}
			wurfWarten = -1;
			// Am liebsten direkt neben die Trichter stellen; ohne Trichter an den Rand zu den Mobs.
			Bereich.Feld rand = null;
			wurfPunkt = null;
			List<BlockPos> trichter = trichterSuchen(mc, p);
			if (!trichter.isEmpty()) {
				rand = feldAmTrichter(trichter, p);
				if (rand != null) {
					wurfPunkt = trichterMitte(trichter, rand);
				}
			}
			if (rand == null) {
				Vec3 mobs = mobStelle(mc, p);
				rand = mobs == null ? null : randFeld(mobs);
			}
			if (rand == null) {
				return false; // weiß nicht, wo die Mobs sind: gleich nochmal versuchen
			}
			// Weg zum Rand planen
			rueckTicks = 0;
			o.keyDown.setDown(false);
			o.keyLeft.setDown(false);
			o.keyRight.setDown(false);
			weg.clear();
			Bereich.Feld hier = new Bereich.Feld(Mth.floor(p.getX()), Mth.floor(p.getZ()));
			if (!hier.equals(rand)) {
				List<Bereich.Feld> w = bereich.weg(hier, rand);
				if (w != null) {
					weg = new ArrayList<>(w);
				}
			}
			wurfPhase = 1;
			wurfTicks = 0;
			wurfRuhig = 0;
			wurfPause = 0;
			neuenZielpunkt();
		}

		wurfTicks++;
		if (wurfPhase == 1) {
			// zum Rand gehen (höchstens 10 Sekunden, dann wirft er von da, wo er steht)
			if (weg.isEmpty() || wurfTicks > 200 || schritt(o, p) != 0) {
				o.keyUp.setDown(false);
				weg.clear();
				wurfPhase = 2;
				wurfTicks = 0;
			}
			return true;
		}

		// Phase 2: in die Trichter (oder die Mob-Stelle) werfen
		Vec3 punkt = wurfPunkt;
		if (punkt == null) {
			Vec3 mobs = mobStelle(mc, p);
			punkt = mobs == null ? null : new Vec3(mobs.x, mobs.y + 1.0, mobs.z);
		}
		Map<String, Integer> mehr = ueberschuss(p);
		if (punkt == null || mehr.isEmpty() || wurfTicks > 400) {
			wurfBeenden();
			return false;
		}
		o.keyUp.setDown(false);
		schauAuf(p, new Vec3(punkt.x + zielVersatzX, punkt.y, punkt.z + zielVersatzZ));
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
			wurfPause = ganzerStapel ? 6 + zufall.nextInt(10) : 3 + zufall.nextInt(4);
			return true;
		}
		wurfBeenden();
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

		int s = schritt(o, p);
		if (s == 1) {
			ankommen();
		} else if (s == 2) {
			pauseTicks = 5;
		} else if (laufTicks > 120) { // hängt fest
			o.keyUp.setDown(false);
			weg.clear();
		}
	}

	/** Ein Tick auf dem geplanten Weg. 0 = unterwegs, 1 = angekommen, 2 = abgebrochen (nächster Schritt wäre verboten). */
	private int schritt(Options o, LocalPlayer p) {
		Bereich.Feld naechstes = weg.get(0);
		double dx = naechstes.x() + 0.5 - p.getX();
		double dz = naechstes.z() + 0.5 - p.getZ();
		if (Math.sqrt(dx * dx + dz * dz) < 0.3) {
			weg.remove(0);
			if (weg.isEmpty()) {
				o.keyUp.setDown(false);
				return 1;
			}
			return 0;
		}

		wunschYaw = yawZu(dx, dz);
		if (zufall.nextDouble() < 0.02) {
			wunschPitch = 10 + zufall.nextFloat() * 20;
		}

		// Sicherheit: würde der nächste Schritt auf ein verbotenes Feld führen? Dann sofort stehen bleiben.
		Vec3 v = p.getDeltaMovement();
		if (!bereich.erlaubt(Mth.floor(p.getX() + v.x * 3), Mth.floor(p.getZ() + v.z * 3))) {
			o.keyUp.setDown(false);
			weg.clear();
			return 2;
		}

		// erst losgehen, wenn er ungefähr in die richtige Richtung schaut
		o.keyUp.setDown(winkelAbstand(p.getYRot(), wunschYaw) < 30);
		return 0;
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
