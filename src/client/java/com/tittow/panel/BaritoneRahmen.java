package com.tittow.panel;

import java.awt.Color;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;

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

	/** Unsere Rahmen, nach ihren Ecken ("x y z x y z"), damit die Rahmen des Spielers selbst bleiben. */
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

	/** Zeigt genau diese Rahmen (je Ecke min und max): neue kommen dazu, nicht mehr gewollte verschwinden. */
	static void zeigen(List<BlockPos[]> gewollt) {
		if (!vorhanden()) {
			return;
		}
		try {
			einstellen();
			Object manager = getSelectionManager.invoke(getPrimaryBaritone.invoke(getProvider.invoke(null)));
			Map<String, BlockPos[]> neu = new HashMap<>();
			for (BlockPos[] b : gewollt) {
				neu.put(schluessel(b), b);
			}
			var it = rahmen.entrySet().iterator();
			while (it.hasNext()) {
				var e = it.next();
				if (!neu.containsKey(e.getKey())) {
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

	private static void setzen(Object settings, String name, Object wert) {
		try {
			Object setting = settings.getClass().getField(name).get(settings);
			setting.getClass().getField("value").set(setting, wert);
		} catch (ReflectiveOperationException | RuntimeException e) {
			System.err.println("[Tittow Panel] Baritone-Einstellung " + name + " fehlt: " + e);
		}
	}

	private static String schluessel(BlockPos[] b) {
		return b[0].getX() + " " + b[0].getY() + " " + b[0].getZ() + " " + b[1].getX() + " " + b[1].getY() + " " + b[1].getZ();
	}
}
