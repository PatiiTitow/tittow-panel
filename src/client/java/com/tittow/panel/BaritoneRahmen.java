package com.tittow.panel;

import java.awt.Color;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lässt Baritone grüne Rahmen durch die Wände zeichnen (Baritones "Selections"), wie im Patri-Autofarmer.
 * Baritone ist keine feste Abhängigkeit: Ist es nicht installiert, zeigt die Mod einfach nichts an.
 * Deshalb geht alles über Reflection auf die öffentliche Baritone-API.
 */
final class BaritoneRahmen {
	private BaritoneRahmen() {
	}

	private static Boolean vorhanden;
	private static boolean eingestellt;
	private static Method getProvider, getPrimaryBaritone, getSelectionManager, addSelection, removeSelection, getSettings;
	private static Constructor<?> neuerPunkt;

	/**
	 * Unsere Rahmen, nach Gruppe und Ecken ("kisten x y z x y z"), damit die Rahmen des Spielers selbst bleiben
	 * und Kisten-Anzeige und Burg-Vorschau sich nicht gegenseitig löschen.
	 */
	private static final Map<String, Object> rahmen = new HashMap<>();

	static boolean vorhanden() {
		if (vorhanden == null) {
			try {
				Class<?> api = Class.forName("baritone.api.BaritoneAPI");
				getProvider = api.getMethod("getProvider");
				getSettings = api.getMethod("getSettings");
				getPrimaryBaritone = Class.forName("baritone.api.IBaritoneProvider").getMethod("getPrimaryBaritone");
				getSelectionManager = Class.forName("baritone.api.IBaritone").getMethod("getSelectionManager");
				Class<?> punkt = Class.forName("baritone.api.utils.BetterBlockPos");
				Class<?> manager = Class.forName("baritone.api.selection.ISelectionManager");
				addSelection = manager.getMethod("addSelection", punkt, punkt);
				removeSelection = manager.getMethod("removeSelection", Class.forName("baritone.api.selection.ISelection"));
				neuerPunkt = punkt.getConstructor(int.class, int.class, int.class);
				vorhanden = true;
			} catch (ReflectiveOperationException | LinkageError e) {
				vorhanden = false;
			}
		}
		return vorhanden;
	}

	/**
	 * Zeigt für eine Gruppe ("kisten", "burg") genau diese Rahmen (je Ecke min und max):
	 * neue kommen dazu, nicht mehr gewollte der Gruppe verschwinden.
	 */
	static void zeigen(String gruppe, List<BlockPos[]> gewollt) {
		if (!vorhanden()) {
			return;
		}
		try {
			einstellen();
			Object manager = getSelectionManager.invoke(getPrimaryBaritone.invoke(getProvider.invoke(null)));
			Map<String, BlockPos[]> neu = new HashMap<>();
			for (BlockPos[] b : gewollt) {
				neu.put(gruppe + " " + schluessel(b), b);
			}
			var it = rahmen.entrySet().iterator();
			while (it.hasNext()) {
				var e = it.next();
				if (e.getKey().startsWith(gruppe + " ") && !neu.containsKey(e.getKey())) {
					removeSelection.invoke(manager, e.getValue());
					it.remove();
				}
			}
			for (var e : neu.entrySet()) {
				if (!rahmen.containsKey(e.getKey())) {
					BlockPos min = e.getValue()[0], max = e.getValue()[1];
					Object auswahl = addSelection.invoke(manager,
							neuerPunkt.newInstance(min.getX(), min.getY(), min.getZ()),
							neuerPunkt.newInstance(max.getX(), max.getY(), max.getZ()));
					rahmen.put(e.getKey(), auswahl);
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			System.err.println("[Tittow Panel] Kisten-Rahmen nicht gezeigt: " + e);
		}
	}

	/** Grün, kräftig und durch Blöcke hindurch, wie beim Autofarmer. Einmal pro Spielstart. */
	private static void einstellen() {
		if (eingestellt) {
			return;
		}
		eingestellt = true;
		try {
			Object settings = getSettings.invoke(null);
			setzen(settings, "renderSelection", true);
			setzen(settings, "renderSelectionIgnoreDepth", true);
			setzen(settings, "colorSelection", Color.GREEN);
			setzen(settings, "colorSelectionPos1", Color.GREEN);
			setzen(settings, "colorSelectionPos2", Color.GREEN);
			setzen(settings, "selectionOpacity", 0.9f);
			setzen(settings, "selectionLineWidth", 3f);
		} catch (ReflectiveOperationException | RuntimeException e) {
			System.err.println("[Tittow Panel] Baritone-Einstellungen nicht gesetzt: " + e);
		}
	}

	/** Was soll an einer Weltstelle stehen? null = gehört nicht zum Bauplan (Baritone lässt es in Ruhe). */
	interface Ziel {
		BlockState an(int x, int y, int z);
	}

	/**
	 * Burg bauen: Baritone bekommt einen Bauplan-Kasten ab "ecke". Dazu gehören nur die Stellen, für die "ziel" einen
	 * Block liefert (z. B. nur eine Blocksorte in einer Schicht), dort soll genau dieser Block hin.
	 */
	static boolean planBauen(String name, BlockPos ecke, int breiteX, int hoehe, int laengeZ, Ziel ziel) {
		if (!vorhanden()) {
			return false;
		}
		try {
			Class<?> schematic = Class.forName("baritone.api.schematic.ISchematic");
			Object plan = Proxy.newProxyInstance(schematic.getClassLoader(), new Class<?>[] {schematic}, (proxy, m, a) -> {
				switch (m.getName()) {
					case "widthX":
						return breiteX;
					case "heightY":
						return hoehe;
					case "lengthZ":
						return laengeZ;
					case "size":
						return switch (((Direction.Axis) a[0])) {
							case X -> breiteX;
							case Y -> hoehe;
							default -> laengeZ;
						};
					case "inSchematic": {
						int x = (Integer) a[0], y = (Integer) a[1], z = (Integer) a[2];
						if (x < 0 || y < 0 || z < 0 || x >= breiteX || y >= hoehe || z >= laengeZ) {
							return false;
						}
						return ziel.an(ecke.getX() + x, ecke.getY() + y, ecke.getZ() + z) != null;
					}
					case "desiredState": {
						int x = (Integer) a[0], y = (Integer) a[1], z = (Integer) a[2];
						BlockState s = ziel.an(ecke.getX() + x, ecke.getY() + y, ecke.getZ() + z);
						return s != null ? s : a[3];
					}
					case "reset":
						return null;
					case "toString":
						return name;
					case "hashCode":
						return System.identityHashCode(proxy);
					case "equals":
						return proxy == a[0];
					default:
						return null;
				}
			});
			Object baritone = getPrimaryBaritone.invoke(getProvider.invoke(null));
			Object builder = Class.forName("baritone.api.IBaritone").getMethod("getBuilderProcess").invoke(baritone);
			Class.forName("baritone.api.process.IBuilderProcess").getMethod("build", String.class, schematic, Vec3i.class)
					.invoke(builder, name, plan, ecke);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			System.err.println("[Tittow Panel] Bauplan ging nicht an Baritone: " + e);
			return false;
		}
	}

	/**
	 * Kamera schön mitführen: Normalerweise läuft Baritone, ohne die Kamera zu drehen ("freeLook"), dann bleibt
	 * das Bild einfach stehen. So schaut die Kamera in die Richtung, in die der Bot läuft und baut, und dreht
	 * sich weich (über ein paar Ticks) statt ruckartig.
	 */
	static void kameraFolgen() {
		einstellung("freeLook", false);
		einstellung("blockFreeLook", false);
		einstellung("smoothLook", true);
		einstellung("smoothLookTicks", 8);
		einstellung("randomLooking", 0.0);
		einstellung("randomLooking113", 0.0);
	}

	/** Eine Baritone-Einstellung lesen (null, wenn es sie nicht gibt). */
	static Object einstellungLesen(String name) {
		if (!vorhanden()) {
			return null;
		}
		try {
			Object settings = getSettings.invoke(null);
			Object setting = settings.getClass().getField(name).get(settings);
			return setting.getClass().getField("value").get(setting);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}

	/** Eine Baritone-Einstellung setzen, z. B. allowInventory. */
	static void einstellung(String name, Object wert) {
		if (!vorhanden()) {
			return;
		}
		try {
			setzen(getSettings.invoke(null), name, wert);
		} catch (ReflectiveOperationException | RuntimeException e) {
			System.err.println("[Tittow Panel] Baritone-Einstellung " + name + " nicht gesetzt: " + e);
		}
	}

	private static void setzen(Object settings, String name, Object wert) {
		try {
			Object setting = settings.getClass().getField(name).get(settings);
			setting.getClass().getField("value").set(setting, wert);
		} catch (ReflectiveOperationException | RuntimeException e) {
			System.err.println("[Tittow Panel] Baritone-Einstellung " + name + " fehlt: " + e);
		}
	}

	/** Führt einen Baritone-Befehl aus, wie im Chat mit # davor (z. B. "sel cleararea"). */
	static boolean befehl(String text) {
		if (!vorhanden()) {
			return false;
		}
		try {
			Object baritone = getPrimaryBaritone.invoke(getProvider.invoke(null));
			Object manager = baritone.getClass().getMethod("getCommandManager").invoke(baritone);
			Object ok = Class.forName("baritone.api.command.manager.ICommandManager").getMethod("execute", String.class)
					.invoke(manager, text);
			return Boolean.TRUE.equals(ok);
		} catch (ReflectiveOperationException | RuntimeException e) {
			System.err.println("[Tittow Panel] Baritone-Befehl \"" + text + "\" ging nicht: " + e);
			return false;
		}
	}

	/** Baut oder räumt Baritone gerade (auch wenn es pausiert, weil Blöcke fehlen)? */
	static boolean bautGerade() {
		if (!vorhanden()) {
			return false;
		}
		try {
			Object baritone = getPrimaryBaritone.invoke(getProvider.invoke(null));
			Object builder = Class.forName("baritone.api.IBaritone").getMethod("getBuilderProcess").invoke(baritone);
			return Boolean.TRUE.equals(Class.forName("baritone.api.process.IBaritoneProcess").getMethod("isActive").invoke(builder));
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/**
	 * Ebnen in einem Durchgang: Baritone bekommt einen eigenen Bauplan (Kasten ab der Ecke unten),
	 * unterste Schicht = Erde, aber nur wo Luft, Wasser oder Lava ist (Löcher stopfen, fester Boden bleibt),
	 * alle Schichten darüber = Luft (freigraben). Wasser darüber lässt es in Ruhe, das kann man nicht abbauen.
	 * Baritone arbeitet beides gleichzeitig ab, je nachdem, wo es gerade ist.
	 */
	static boolean ebnenBauen(BlockPos ecke, int breiteX, int hoehe, int laengeZ) {
		if (!vorhanden()) {
			return false;
		}
		try {
			Class<?> schematic = Class.forName("baritone.api.schematic.ISchematic");
			BlockState erde = Blocks.DIRT.defaultBlockState();
			BlockState luft = Blocks.AIR.defaultBlockState();
			Object plan = Proxy.newProxyInstance(schematic.getClassLoader(), new Class<?>[] {schematic}, (proxy, m, a) -> {
				switch (m.getName()) {
					case "widthX":
						return breiteX;
					case "heightY":
						return hoehe;
					case "lengthZ":
						return laengeZ;
					case "size":
						return switch (((Direction.Axis) a[0])) {
							case X -> breiteX;
							case Y -> hoehe;
							default -> laengeZ;
						};
					case "inSchematic": {
						int x = (Integer) a[0], y = (Integer) a[1], z = (Integer) a[2];
						if (x < 0 || y < 0 || z < 0 || x >= breiteX || y >= hoehe || z >= laengeZ) {
							return false;
						}
						BlockState jetzt = (BlockState) a[3];
						if (y == 0) {
							return true; // Boden-Schicht gehört ganz dazu: fester Boden gilt als "richtig" und wird nicht abgebaut
						}
						return !jetzt.liquid(); // darüber alles weg, außer Wasser/Lava
					}
					case "desiredState": {
						if ((Integer) a[1] != 0) {
							return luft;
						}
						BlockState jetzt = (BlockState) a[3];
						// Loch, Wasser, Pflanze: Erde hin. Fester Boden (auch schon gestopfte Erde): so lassen
						return jetzt.isAir() || jetzt.liquid() || jetzt.canBeReplaced() ? erde : jetzt;
					}
					case "reset":
						return null;
					case "toString":
						return "Tittow Burg-Platz";
					case "hashCode":
						return System.identityHashCode(proxy);
					case "equals":
						return proxy == a[0];
					default:
						return null;
				}
			});
			Object baritone = getPrimaryBaritone.invoke(getProvider.invoke(null));
			Object builder = Class.forName("baritone.api.IBaritone").getMethod("getBuilderProcess").invoke(baritone);
			Class.forName("baritone.api.process.IBuilderProcess").getMethod("build", String.class, schematic, Vec3i.class)
					.invoke(builder, "Burg-Platz", plan, ecke);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			System.err.println("[Tittow Panel] Ebnen-Bauplan ging nicht an Baritone: " + e);
			return false;
		}
	}

	/** Hat Baritone beim Bauen gewartet (z. B. weil Erde fehlte), geht es leise weiter (ohne Chat-Meldung). */
	static void bauenFortsetzen() {
		if (!vorhanden()) {
			return;
		}
		try {
			Object baritone = getPrimaryBaritone.invoke(getProvider.invoke(null));
			Object builder = Class.forName("baritone.api.IBaritone").getMethod("getBuilderProcess").invoke(baritone);
			Class.forName("baritone.api.process.IBuilderProcess").getMethod("resume").invoke(builder);
		} catch (ReflectiveOperationException | RuntimeException e) {
			System.err.println("[Tittow Panel] Baritone weiterbauen ging nicht: " + e);
		}
	}

	/** Nach "sel clear" sind alle Baritone-Rahmen weg: vergessen, damit Kisten und Burg sie neu setzen. */
	static void vergessen() {
		rahmen.clear();
	}

	private static String schluessel(BlockPos[] b) {
		return b[0].getX() + " " + b[0].getY() + " " + b[0].getZ() + " " + b[1].getX() + " " + b[1].getY() + " " + b[1].getZ();
	}
}
