package com.tittow.panel;

import com.tittow.panel.mixin.KeyMappingAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Beim Ebnen, Herstellen und Bauen: Kommt ein Mob (Monster oder Tier, auch Pump-Monster/-Tiere) näher als 2 Blöcke, hält der Bot Baritone kurz an, nimmt die Waffe aus
 * Slot 1 (Schwert bzw. Glefe), schaut ihn an und schlägt zu, sobald die Waffe aufgeladen ist. Ist kein Gegner mehr
 * in der Nähe, geht es mit dem vorherigen Werkzeug weiter. Spieler und Dorfbewohner greift er nie an.
 */
final class Abwehr {
	static final int WAFFEN_SLOT = 0; // Slot 1
	/** Ab dieser Nähe (Blöcke bis zum Gegner) wird angegriffen. */
	private static final double RADIUS = 2.0;
	/** So weit weg darf er wieder sein, bevor der Kampf vorbei ist. */
	private static final double LOSLASSEN = 3.0;

	private Entity ziel;
	private int alterSlot;
	private int ohneZiel;
	private int kampfTicks = -1;
	private int geladen;

	boolean kaempft() {
		return kampfTicks >= 0;
	}

	void abbrechen() {
		kampfTicks = -1;
		ziel = null;
	}

	/** Jeden Tick beim Ebnen. Gibt true zurück, solange gekämpft wird (dann nichts anderes machen). */
	boolean tick(Minecraft mc, LocalPlayer p) {
		if (mc.gameMode == null || mc.gui.screen() != null) {
			return kaempft();
		}
		if (kampfTicks < 0) {
			Entity naechster = naechsterGegner(mc, p, RADIUS);
			if (naechster == null) {
				return false;
			}
			BaritoneRahmen.befehl("pause");
			alterSlot = p.getInventory().getSelectedSlot();
			if (!p.getInventory().getItem(WAFFEN_SLOT).isEmpty()) {
				p.getInventory().setSelectedSlot(WAFFEN_SLOT);
			}
			ziel = naechster;
			kampfTicks = 0;
			ohneZiel = 0;
		}
		kampfTicks++;
		if (ziel == null || !ziel.isAlive() || abstand(p, ziel) > LOSLASSEN) {
			ziel = naechsterGegner(mc, p, LOSLASSEN);
		}
		if (ziel == null) {
			if (++ohneZiel > 10 || kampfTicks > 20 * 20) {
				ende(p);
			}
			return true;
		}
		ohneZiel = 0;
		if (kampfTicks > 20 * 20) { // höchstens 20 Sekunden am Stück, dann erst mal weiterarbeiten
			ende(p);
			return false;
		}
		schauAuf(p, ziel);
		float mindestLadung = p.getMainHandItem().getOrDefault(DataComponents.MINIMUM_ATTACK_CHARGE, 0f);
		if (p.getAttackStrengthScale(0.0f) >= (mindestLadung > 0f ? 1.0f : 0.9f)) {
			if (mc.hitResult instanceof EntityHitResult t && t.getEntity() == ziel) {
				KeyMappingAccessor a = (KeyMappingAccessor) mc.options.keyAttack; // normaler Linksklick
				a.tittow_setClickCount(a.tittow_getClickCount() + 1);
				geladen = 0;
			} else if (++geladen > 10) {
				mc.gameMode.attack(p, ziel); // Fadenkreuz trifft nicht (z. B. Gras davor): direkt angreifen
				geladen = 0;
			}
		}
		return true;
	}

	private void ende(LocalPlayer p) {
		p.getInventory().setSelectedSlot(alterSlot);
		BaritoneRahmen.befehl("resume");
		kampfTicks = -1;
		ziel = null;
	}

	/** Der nächste lebende Gegner (Monster) innerhalb des Abstands, sonst null. */
	private static Entity naechsterGegner(Minecraft mc, LocalPlayer p, double weite) {
		Entity bester = null;
		double besterAbstand = Double.MAX_VALUE;
		// alles, was im Weg ist: Monster, Tiere, auch die eigenen Pump-Monster und -Tiere.
		// Nie Spieler (sind keine "Mob"s), nie Dorfbewohner und Händler (AbstractVillager), nie Rüstungsständer.
		for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(weite + 1),
				e -> e instanceof Mob && !(e instanceof AbstractVillager) && e.isAlive())) {
			double d = abstand(p, e);
			if (d <= weite && d < besterAbstand) {
				bester = e;
				besterAbstand = d;
			}
		}
		return bester;
	}

	/** Abstand von deinen Augen bis zur nächsten Stelle am Gegner. */
	private static double abstand(LocalPlayer p, Entity e) {
		Vec3 auge = p.getEyePosition();
		AABB box = e.getBoundingBox();
		double x = Mth.clamp(auge.x, box.minX, box.maxX);
		double y = Mth.clamp(auge.y, box.minY, box.maxY);
		double z = Mth.clamp(auge.z, box.minZ, box.maxZ);
		return auge.distanceTo(new Vec3(x, y, z));
	}

	private static void schauAuf(LocalPlayer p, Entity e) {
		Vec3 auge = p.getEyePosition();
		Vec3 ziel = e.getBoundingBox().getCenter();
		double dx = ziel.x - auge.x, dy = ziel.y - auge.y, dz = ziel.z - auge.z;
		double waagrecht = Math.sqrt(dx * dx + dz * dz);
		p.setYRot((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f);
		p.setXRot((float) -(Mth.atan2(dy, waagrecht) * Mth.RAD_TO_DEG));
	}
}
