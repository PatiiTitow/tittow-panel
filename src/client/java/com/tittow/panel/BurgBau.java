package com.tittow.panel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Burg bauen, Abschnitt für Abschnitt (erst der Sockel, dann je 6 Blöcke Höhe):
 * 1. Vorbereiten: nachsehen, was im Abschnitt noch fehlt, und alles herstellen und brennen (Herstellen).
 * 2. Prüfen: Lager durchzählen. Fehlt noch etwas, hält er an und sagt, was fehlt.
 * 3. Bauen: Schicht für Schicht, in jeder Schicht eine Blocksorte nach der anderen. Die Sorte liegt in Slot 6
 *    (Versorgung legt nach), fehlt sie, holt er sie aus dem Funk-Terminal. Baritone baut nur diese Sorte.
 * Erst mal wird nur der Sockel (Abschnitt 1) gebaut, danach ist Schluss. "Sockel weiter bauen" macht nach einem Stopp weiter.
 */
final class BurgBau {
	private enum Schritt { VORBEREITEN, PRUEFEN, BAUEN, AUFRAEUMEN }

	private enum Zweck { KEINS, ZAEHLEN, HOLEN, HOLEN_ERDE, ABLEGEN }

	/** Slot 5 (die Axt): solange gebaut wird, liegt dort Erde zum Hochbauen. */
	static final int GERUEST_SLOT = 4;
	/** So weit über dem Abschnitt wird am Ende noch nach Gerüst-Erde gesucht. */
	private static final int GERUEST_HOEHE = 6;

	private final FunkTerminal terminal;
	private final Herstellen herstellen;
	private final Versorgung versorgung;
	private final Bereich einstellungen;

	private boolean an;
	private Schritt schritt;
	private Zweck zweck = Zweck.KEINS;
	private Bauplan plan;
	private BlockPos tor;
	private Direction vorne;
	private int von, bis;      // Höhen des Abschnitts
	private int y;             // aktuelle Schicht
	private List<Integer> sorten = List.of();
	private int sorteNr;       // Stelle in "sorten"
	private int index;         // Paletten-Nummer der Sorte, die gerade gebaut wird
	private Item item;
	private boolean baut;      // Baritone hat den Auftrag bekommen
	private int versuch;
	private int warte;
	private int aufpassTicks, letzterRest, stillstand;
	private int holVorher;
	private boolean geholt;    // für diese Sorte schon aus dem Terminal geholt (nicht endlos nochmal)
	private boolean lagerLeer; // im Terminal ist von der Sorte nichts mehr
	private boolean erdeGeholt; // Gerüst-Erde schon geholt (nicht endlos nochmal)
	private Item werkzeugIn5;   // was vor dem Bauen in Slot 5 lag (kommt am Ende zurück)
	private int passStartRest;  // wie viele von der Sorte beim Start des Auftrags fehlten
	private boolean fortschrittInRunde; // hat die aktuelle Runde durch die Sorten etwas gebracht?
	private int runde;          // wievielte Runde in dieser Schicht
	private int nachholRunde;   // wie oft er am Schluss schon nachgeholt hat, was übersprungen wurde
	private Vec3 letztePos;     // wo er beim letzten Nachsehen stand (läuft er noch?)
	private long gesamt;          // Blöcke im ganzen Abschnitt (Sockel)
	private long fertigBeimStart;  // davon fertig, als das Bauen (diesmal) losging
	private long startZeit;        // wann das Bauen (diesmal) losging
	private long naechsteMeldung = Long.MAX_VALUE; // wann die nächste Fortschritts-Meldung kommt
	private static final long MELDUNG_ALLE = 5 * 60 * 1000L; // alle 5 Minuten
	private Map<String, Integer> start = Map.of(); // Sachen vom Start, die im Inventar bleiben (ohne Baumaterial)
	private int platzFehler;    // wie oft hintereinander das Platzmachen nicht geholfen hat
	private final java.util.Set<Integer> leer = new java.util.HashSet<>(); // Sorten, die im Terminal wirklich alle sind
	private Object altWegwerf;  // Baritones Liste der "Wegwerf-Blöcke", wird am Ende zurückgestellt

	BurgBau(FunkTerminal terminal, Herstellen herstellen, Versorgung versorgung, Bereich einstellungen) {
		this.terminal = terminal;
		this.herstellen = herstellen;
		this.versorgung = versorgung;
		this.einstellungen = einstellungen;
	}

	boolean laeuft() {
		return an;
	}

	boolean imMenue() {
		return terminal.laeuft() || herstellen.imMenue();
	}

	/** Name des Abschnitts für den Chat, z. B. "den Sockel" oder "Abschnitt 3 (Höhe 10–15)". */
	static String name(int nr, int[] a) {
		return nr == 0 ? "den Sockel" : "Abschnitt " + (nr + 1) + " (Höhe " + a[0] + "–" + a[1] + ")";
	}

	String starten(Minecraft mc, BlockPos tor, Direction vorne) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			return "";
		}
		plan = Bauplan.laden();
		if (plan == null) {
			return "Der Bauplan fehlt in der Mod.";
		}
		List<int[]> abschnitte = plan.abschnitte();
		int nr = einstellungen.burgBauAbschnitt;
		if (nr >= 1) { // erst mal wird nur der Sockel gebaut
			return "Der Sockel ist schon fertig gebaut.";
		}
		this.tor = tor;
		this.vorne = vorne;
		von = abschnitte.get(nr)[0];
		bis = abschnitte.get(nr)[1];
		an = true;
		einstellungen.burgBauen = true;
		einstellungen.speichern();
		// Baumaterial, das noch vom letzten Mal im Inventar liegt, zählt nicht als "eigene Sachen": das kommt
		// beim Ablegen mit ins Terminal, sonst wird das Inventar mit der Zeit voll und er kann nichts mehr holen.
		start = new HashMap<>(FunkTerminal.inventar(p));
		for (BlockState s : plan.zustaende) {
			start.remove(FunkTerminal.schluessel(new ItemStack(s.getBlock().asItem())));
		}
		zweck = Zweck.KEINS;
		baut = false;
		warte = 0;
		Map<String, Long> noch = nochZuBauen(mc);
		chat(p, "Sockel bauen: erst wird alles dafür hergestellt und gebrannt, dann gebaut.");
		if (noch.isEmpty()) {
			abschnittFertig(mc);
			return "";
		}
		schritt = Schritt.VORBEREITEN;
		herstellen.starten(mc, noch, name(nr, abschnitte.get(nr)));
		return "Sockel bauen läuft.";
	}

	void stoppen(Minecraft mc, String grund) {
		if (!an) {
			return;
		}
		an = false;
		naechsteMeldung = Long.MAX_VALUE; // keine Fortschritts-Meldungen mehr
		herstellen.stoppen(mc, null);
		terminal.abbrechen();
		BaritoneRahmen.befehl("stop");
		einstellungenZurueck();
		werkzeugZurueck(mc, mc.player); // Axt wieder in Slot 5
		if (grund != null && mc.player != null) {
			chat(mc.player, grund);
		}
	}

	void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (!an || p == null || mc.level == null) {
			return;
		}
		if (schritt == Schritt.VORBEREITEN) {
			if (herstellen.laeuft()) {
				herstellen.tick(mc);
				return;
			}
			if (!herstellen.zuEndeGelaufen()) {
				stoppen(mc, "Herstellen wurde abgebrochen – Bauen angehalten. „Sockel weiter bauen“ startet neu.");
				return;
			}
			schritt = Schritt.PRUEFEN;
			zweck = Zweck.ZAEHLEN;
			terminal.zaehlen(mc);
			return;
		}
		if (terminal.laeuft()) {
			String m = terminal.tick(mc);
			if (m != null) {
				nachTerminal(mc, p, m);
			}
			return;
		}
		if ((schritt == Schritt.BAUEN || schritt == Schritt.AUFRAEUMEN) && System.currentTimeMillis() >= naechsteMeldung) {
			fortschrittMelden(mc, p); // alle 5 Minuten: wie viel Prozent fertig, wie lange noch
		}
		if (warte > 0) {
			warte--;
			return;
		}
		if (schritt == Schritt.BAUEN) {
			bauen(mc, p);
		} else if (schritt == Schritt.AUFRAEUMEN) {
			aufraeumen(mc, p);
		}
	}

	private void nachTerminal(Minecraft mc, LocalPlayer p, String meldung) {
		Zweck z = zweck;
		zweck = Zweck.KEINS;
		warte = 5;
		if (z == Zweck.ZAEHLEN) {
			if (!meldung.startsWith("OK")) {
				stoppen(mc, meldung);
				return;
			}
			pruefen(mc, p);
		} else if (z == Zweck.HOLEN_ERDE) {
			// Erde fürs Gerüst geholt (oder keine da): weiter wie gehabt
		} else if (z == Zweck.HOLEN) {
			geholt = true;
			int jetzt = FunkTerminal.anzahl(p, item);
			if (jetzt > holVorher) {
				platzFehler = 0;
				return;
			}
			lagerLeer = true; // nichts mehr gekommen: nicht ständig nochmal versuchen
			if (jetzt > 0) {
				return; // ein Rest ist noch da, den verbaut er erst
			}
			if (FunkTerminal.freiePlaetze(p) <= 5) {
				// kein Platz im Inventar: erst ablegen, dann nochmal holen
				geholt = false;
				lagerLeer = false;
				platzMachen(mc);
				return;
			}
			// im Terminal ist wirklich nichts mehr davon: diese Sorte auslassen und mit den anderen weiterbauen
			leer.add(index);
			chat(p, "§e" + new ItemStack(item).getHoverName().getString() + " ist im Funk-Terminal alle§r – er baut mit den anderen Sorten weiter.");
			if (baut) {
				BaritoneRahmen.befehl("stop");
				baut = false;
			}
			sorteNr++;
			geholt = false;
			warte = 10;
		}
	}

	/** Nach dem Herstellen: ist jetzt alles für den Abschnitt da? Sonst anhalten und sagen, was fehlt. */
	private void pruefen(Minecraft mc, LocalPlayer p) {
		Map<String, Long> da = new HashMap<>(terminal.bestand());
		for (int i = 0; i < 36; i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (!s.isEmpty()) {
				da.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString(), (long) s.getCount(), Long::sum);
			}
		}
		Map<String, Long> fehlt = new LinkedHashMap<>();
		for (Map.Entry<String, Long> e : nochZuBauen(mc).entrySet()) {
			long hab = da.getOrDefault(e.getKey(), 0L);
			if (hab < e.getValue()) {
				fehlt.put(e.getKey(), e.getValue() - hab);
			}
		}
		if (!fehlt.isEmpty()) {
			chat(p, "§cFür den Sockel fehlt noch Material:§r");
			for (Map.Entry<String, Long> e : fehlt.entrySet()) {
				p.sendSystemMessage(Component.literal(" §c✘ ").append(new ItemStack(Herstellen.item(e.getKey())).getHoverName())
						.append(Component.literal("§7: fehlen " + String.format(Locale.GERMANY, "%,d", e.getValue()))));
			}
			stoppen(mc, "Bauen angehalten. Besorg das Material (oder die Rohstoffe dafür) und klick „Sockel weiter bauen“.");
			return;
		}
		chat(p, "§aAlles da.§r Jetzt wird gebaut, Schicht für Schicht.");
		// Baritone: nichts abbauen (sonst reißt es Fertiges wieder ein), keine eigenen Blöcke zum Hochbauen benutzen
		BaritoneRahmen.einstellung("allowInventory", false);
		BaritoneRahmen.kameraFolgen(); // Kamera schaut, wohin er läuft und baut
		BaritoneRahmen.einstellung("allowBreak", false);
		BaritoneRahmen.einstellung("allowPlace", true);
		BaritoneRahmen.einstellung("buildInLayers", false);
		BaritoneRahmen.einstellung("okIfWater", false);
		if (altWegwerf == null) {
			altWegwerf = BaritoneRahmen.einstellungLesen("acceptableThrowawayItems");
		}
		// zum Hochbauen (Gerüst) nur Erde benutzen. Die liegt in Slot 5, die Axt kommt solange nach oben ins Inventar.
		BaritoneRahmen.einstellung("acceptableThrowawayItems", new ArrayList<Item>(List.of(Items.DIRT)));
		if (enthaeltLava()) {
			chat(p, "Hinweis: Die Lava im Brunnen setzt der Bot nicht, die kommt am Ende per Eimer.");
		}
		schritt = Schritt.BAUEN;
		fortschrittStarten(mc, p);
		// fertige Schichten überspringt er von selbst (er schaut in der Welt nach, was schon steht)
		nachholRunde = 0;
		y = von;
		if (einstellungen.burgBauSchicht > von) {
			chat(p, "Er macht da weiter, wo er aufgehört hat (zuletzt Höhe " + einstellungen.burgBauSchicht + "). Was schon steht, überspringt er.");
		}
		sorteNr = 0;
		sorten = plan.sortenInSchicht(y);
		baut = false;
		// alles vom letzten Mal vergessen (sonst holt er für die erste Sorte nichts und Baritone hat kein Material)
		geholt = false;
		lagerLeer = false;
		erdeGeholt = false;
		platzFehler = 0;
		leer.clear();
	}

	private boolean enthaeltLava() {
		for (int yy = von; yy <= bis; yy++) {
			for (int z = 0; z < plan.l; z++) {
				for (int x = 0; x < plan.w; x++) {
					if (plan.ids[plan.index(x, yy, z)].equals("minecraft:lava")) {
						return true;
					}
				}
			}
		}
		return false;
	}

	// ---- Bauen ---------------------------------------------------------------------------

	private void bauen(Minecraft mc, LocalPlayer p) {
		if (baut) {
			versorgung.nachlegen(mc, p, item);
			if (BaritoneRahmen.bautGerade()) {
				int hab = FunkTerminal.anzahl(p, item);
				if (FunkTerminal.inventarVoll(p)) {
					zweck = Zweck.ABLEGEN;
					terminal.ablegen(mc, behalten(item)); // das Baumaterial bleibt, die Gerüst-Erde auch
					return;
				}
				if (aufpassTicks % 40 == 0 && !geruestErde(mc, p)) {
					return;
				}
				if (hab < 16 && !lagerLeer && ++aufpassTicks % 40 == 0 && rest(mc) > hab) {
					holen(mc, p);
					return;
				}
				aufpassen(mc);
				return;
			}
			// Baritone ist fertig (oder kommt nicht weiter): weiter mit der nächsten Sorte. Was von dieser Sorte noch
			// fehlt, kommt in der nächsten Runde dran (dann haben die Blöcke oft schon Nachbarn, an die er sie setzen kann).
			baut = false;
			if (rest(mc) < passStartRest) {
				fortschrittInRunde = true;
			}
			sorteNr++;
			geholt = false;
			warte = 10;
			if (FunkTerminal.anzahl(p, item) > 0) {
				// Sorte fertig: was übrig ist, zurück ins Funk-Terminal (die nächste Sorte holt er sich frisch heraus)
				zweck = Zweck.ABLEGEN;
				terminal.ablegen(mc, behalten(null));
			}
			return;
		}
		// nächste Sorte bzw. nächste Schicht
		while (true) {
			if (sorteNr >= sorten.size()) {
				// Runde durch. Fehlt in der Schicht noch etwas und hat die Runde etwas gebracht: nochmal von vorne.
				int offen = schichtRest(mc);
				if (offen > 0 && fortschrittInRunde) {
					sorteNr = 0;
					runde++;
					fortschrittInRunde = false;
					continue;
				}
				if (offen > 0) {
					chat(p, "In Höhe " + y + " fehlen noch " + offen + " Blöcke, da kommt er gerade nicht hin – die holt er am Schluss nach.");
				}
				runde = 0;
				fortschrittInRunde = false;
				y++;
				einstellungen.burgBauSchicht = y; // merken, wo er gerade ist
				einstellungen.speichern();
				if (y > bis) {
					// oben angekommen: was übersprungen wurde, jetzt nachholen (bis zu 3 Durchgänge von unten nach oben)
					long offenGesamt = 0;
					for (long n : nochZuBauen(mc).values()) {
						offenGesamt += n;
					}
					if (offenGesamt > 0 && nachholRunde < 3) {
						nachholRunde++;
						chat(p, "Jetzt macht er fertig, was er übersprungen hat (" + offenGesamt + " Blöcke, Durchgang " + nachholRunde + " von 3).");
						y = von;
						sorten = plan.sortenInSchicht(y);
						sorteNr = 0;
						runde = 0;
						fortschrittInRunde = false;
						continue;
					}
					if (offenGesamt > 0 && !leer.isEmpty()) {
						StringBuilder namen = new StringBuilder();
						for (int i : leer) {
							namen.append(namen.length() > 0 ? ", " : "").append(new ItemStack(plan.zustaende[i].getBlock().asItem()).getHoverName().getString());
						}
						chat(p, "§c" + offenGesamt + " Blöcke fehlen noch.§r Im Funk-Terminal war alle: " + namen
								+ ". Besorg mehr und klick „Sockel weiter bauen“.");
					} else if (offenGesamt > 0) {
						chat(p, "§c" + offenGesamt + " Blöcke gehen auch nach 3 Nachhol-Durchgängen nicht§r – die bitte selbst setzen.");
					}
					aufraeumenStarten(mc, p); // erst das Erd-Gerüst wieder weg, dann ist der Abschnitt fertig
					return;
				}
				sorten = plan.sortenInSchicht(y);
				sorteNr = 0;
				continue;
			}
			index = sorten.get(sorteNr);
			item = plan.zustaende[index].getBlock().asItem();
			if (!leer.contains(index) && rest(mc) > 0) {
				break;
			}
			sorteNr++;
		}
		int rest = rest(mc);
		int hab = FunkTerminal.anzahl(p, item);
		if (hab < Math.min(rest, 64) && !geholt) {
			holen(mc, p);
			return;
		}
		if (!geruestErde(mc, p)) {
			return; // erst Erde fürs Gerüst holen
		}
		geholt = false;
		lagerLeer = false;
		versorgung.nachlegen(mc, p, item);
		if (!passStarten()) {
			stoppen(mc, "Baritone hat den Bauplan nicht angenommen.");
			return;
		}
		baut = true;
		aufpassTicks = 0;
		stillstand = 0;
		letzterRest = rest;
		passStartRest = rest;
		letztePos = p.position();
		warte = 40; // Baritone braucht einen Moment
		if (sorteNr == 0 && runde == 0) {
			chat(p, "Höhe " + y + " (Y " + (tor.getY() + y) + "): " + sorten.size() + " Blocksorte(n).");
		}
	}

	// ---- Erde fürs Gerüst (Hochbauen) ----------------------------------------------------------

	/**
	 * Hält Slot 5 voll mit Erde, damit Baritone sich damit hochbauen kann. Was dort lag (die Axt), kommt solange
	 * nach oben ins Inventar und am Ende wieder zurück. false = es wird gerade Erde aus dem Funk-Terminal geholt.
	 */
	private boolean geruestErde(Minecraft mc, LocalPlayer p) {
		Inventory inv = p.getInventory();
		ItemStack in5 = inv.getItem(GERUEST_SLOT);
		if (in5.is(Items.DIRT) && in5.getCount() >= 16) {
			return true;
		}
		if (werkzeugIn5 == null && !in5.isEmpty() && !in5.is(Items.DIRT)) {
			werkzeugIn5 = in5.getItem(); // merken, damit es am Ende zurück nach Slot 5 kommt
		}
		int beste = -1;
		for (int i = Inventory.getSelectionSize(); i < 36; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(Items.DIRT) && (beste < 0 || s.getCount() > inv.getItem(beste).getCount())) {
				beste = i;
			}
		}
		if (beste < 0 || in5.is(Items.DIRT) && inv.getItem(beste).getCount() <= in5.getCount()) {
			if (!erdeGeholt) { // keine Erde oben im Inventar: welche aus dem Funk-Terminal holen
				erdeGeholt = true;
				zweck = Zweck.HOLEN_ERDE;
				terminal.holen(mc, Items.DIRT, 128);
				return false;
			}
			return true; // keine Erde mehr da: dann eben ohne Gerüst
		}
		if (mc.gui.screen() != null || p.containerMenu != p.inventoryMenu || mc.gameMode == null) {
			return true;
		}
		mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, beste, GERUEST_SLOT, ContainerInput.SWAP, p);
		erdeGeholt = false;
		return true;
	}

	/** Die Axt (oder was in Slot 5 lag) wieder zurück nach Slot 5. */
	private void werkzeugZurueck(Minecraft mc, LocalPlayer p) {
		if (werkzeugIn5 == null || p == null || mc.gameMode == null) {
			return;
		}
		Inventory inv = p.getInventory();
		if (!inv.getItem(GERUEST_SLOT).is(werkzeugIn5)) {
			for (int i = Inventory.getSelectionSize(); i < 36; i++) {
				if (inv.getItem(i).is(werkzeugIn5)) {
					if (p.containerMenu != p.inventoryMenu) {
						p.closeContainer();
					}
					mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, i, GERUEST_SLOT, ContainerInput.SWAP, p);
					break;
				}
			}
		}
		werkzeugIn5 = null;
	}

	// ---- Gerüst wieder abbauen ---------------------------------------------------------------------

	/** Nach der letzten Schicht: Erd-Blöcke, die nicht zum Bauplan gehören (Gerüst), wieder abbauen. */
	private void aufraeumenStarten(Minecraft mc, LocalPlayer p) {
		int n = geruestBloecke(mc);
		if (n == 0) {
			abschnittFertig(mc);
			return;
		}
		chat(p, "Jetzt baut er sein Erd-Gerüst wieder ab (" + n + " Blöcke).");
		BaritoneRahmen.einstellung("allowBreak", true);
		if (!aufraeumenAuftrag()) {
			abschnittFertig(mc);
			return;
		}
		schritt = Schritt.AUFRAEUMEN;
		versuch = 0;
		aufpassTicks = 0;
		stillstand = 0;
		letzterRest = n;
		warte = 40;
	}

	private void aufraeumen(Minecraft mc, LocalPlayer p) {
		if (BaritoneRahmen.bautGerade()) {
			if (++aufpassTicks >= 600) { // alle 30 Sekunden: wird es weniger?
				aufpassTicks = 0;
				int n = geruestBloecke(mc);
				if (n < letzterRest) {
					letzterRest = n;
					stillstand = 0;
				} else if (++stillstand >= 4) {
					BaritoneRahmen.befehl("stop");
				}
			}
			return;
		}
		int n = geruestBloecke(mc);
		if (n > 0 && ++versuch < 3 && aufraeumenAuftrag()) {
			warte = 40;
			return;
		}
		BaritoneRahmen.einstellung("allowBreak", false);
		if (n > 0) {
			chat(p, n + " Erd-Blöcke vom Gerüst sind noch da – bitte selbst abbauen.");
		}
		abschnittFertig(mc);
	}

	/** Baritone soll alle Gerüst-Erde im Abschnitt (und ein paar Blöcke darüber) abbauen. */
	private boolean aufraeumenAuftrag() {
		int[] a = Burgvorschau.welt(tor, vorne, Bauplan.VERSATZ, Bauplan.VERSATZ);
		int[] b = Burgvorschau.welt(tor, vorne, Bauplan.VERSATZ + plan.w - 1, Bauplan.VERSATZ + plan.l - 1);
		int oben = bis + GERUEST_HOEHE;
		BlockPos ecke = new BlockPos(Math.min(a[0], b[0]), tor.getY() + von, Math.min(a[1], b[1]));
		int breitX = Math.abs(a[0] - b[0]) + 1, laengeZ = Math.abs(a[1] - b[1]) + 1;
		Minecraft mc = Minecraft.getInstance();
		BlockState luft = Blocks.AIR.defaultBlockState();
		return BaritoneRahmen.planBauen("Tittow Gerüst weg", ecke, breitX, oben - von + 1, laengeZ, (wx, wy, wz) -> {
			int[] m = Burgvorschau.plan(tor, vorne, wx, wz);
			int yy = wy - tor.getY();
			return istGeruest(mc, m[0] - Bauplan.VERSATZ, yy, m[1] - Bauplan.VERSATZ, new BlockPos(wx, wy, wz)) ? luft : null;
		});
	}

	/** Erde an einer Stelle, wo laut Bauplan nichts hingehört = Gerüst. */
	private boolean istGeruest(Minecraft mc, int px, int yy, int pz, BlockPos pos) {
		if (yy < 0 || px < 0 || pz < 0 || px >= plan.w || pz >= plan.l) {
			return false;
		}
		if (yy < plan.h && plan.index(px, yy, pz) != 0) {
			return false; // da gehört etwas hin
		}
		return mc.level.getBlockState(pos).is(Blocks.DIRT);
	}

	private int geruestBloecke(Minecraft mc) {
		int n = 0;
		for (int yy = von; yy <= bis + GERUEST_HOEHE; yy++) {
			for (int z = 0; z < plan.l; z++) {
				for (int x = 0; x < plan.w; x++) {
					int[] w = Burgvorschau.welt(tor, vorne, x + Bauplan.VERSATZ, z + Bauplan.VERSATZ);
					if (mc.level.hasChunk(w[0] >> 4, w[1] >> 4) && istGeruest(mc, x, yy, z, new BlockPos(w[0], tor.getY() + yy, w[1]))) {
						n++;
					}
				}
			}
		}
		return n;
	}

	private void holen(Minecraft mc, LocalPlayer p) {
		if (FunkTerminal.freiePlaetze(p) <= 5) {
			platzMachen(mc); // Inventar fast voll: erst ablegen, sonst kommt nichts heraus und er denkt, es ist alle
			return;
		}
		zweck = Zweck.HOLEN;
		holVorher = FunkTerminal.anzahl(p, item);
		int rest = rest(mc);
		terminal.holen(mc, item, Math.min(Math.max(rest - holVorher, 64), 64 * 10));
	}

	/** Alles ablegen, was nicht gebraucht wird (die aktuelle Sorte und die Gerüst-Erde bleiben). Hilft das mehrmals nicht: anhalten. */
	private void platzMachen(Minecraft mc) {
		if (++platzFehler > 3) {
			stoppen(mc, "§cDas Inventar ist zu voll§r (nur eigene Sachen drin). Mach ein paar Plätze frei und klick „Sockel weiter bauen“.");
			return;
		}
		zweck = Zweck.ABLEGEN;
		terminal.ablegen(mc, behalten(item));
	}

	/** Was beim Ablegen im Inventar bleibt: die eigenen Sachen vom Start, die Gerüst-Erde und (falls angegeben) das Baumaterial. */
	private Map<String, Integer> behalten(Item auch) {
		Map<String, Integer> m = new HashMap<>(start);
		m.put(FunkTerminal.schluessel(new ItemStack(Items.DIRT)), 100000);
		if (auch != null) {
			m.put(FunkTerminal.schluessel(new ItemStack(auch)), 100000);
		}
		return m;
	}

	/** Gibt Baritone die aktuelle Schicht, nur mit der aktuellen Blocksorte. */
	private boolean passStarten() {
		int[] a = Burgvorschau.welt(tor, vorne, Bauplan.VERSATZ, Bauplan.VERSATZ);
		int[] b = Burgvorschau.welt(tor, vorne, Bauplan.VERSATZ + plan.w - 1, Bauplan.VERSATZ + plan.l - 1);
		BlockPos ecke = new BlockPos(Math.min(a[0], b[0]), tor.getY() + y, Math.min(a[1], b[1]));
		int breitX = Math.abs(a[0] - b[0]) + 1, laengeZ = Math.abs(a[1] - b[1]) + 1;
		final int schicht = y, sorte = index;
		final BlockState soll = plan.zustaende[index];
		return BaritoneRahmen.planBauen("Tittow Burg", ecke, breitX, 1, laengeZ, (wx, wy, wz) -> {
			if (wy != tor.getY() + schicht) {
				return null;
			}
			int[] m = Burgvorschau.plan(tor, vorne, wx, wz);
			return plan.index(m[0] - Bauplan.VERSATZ, schicht, m[1] - Bauplan.VERSATZ) == sorte ? soll : null;
		});
	}

	/** Wie viele Stellen der aktuellen Sorte in der aktuellen Schicht noch nicht stimmen. */
	private int rest(Minecraft mc) {
		int n = 0;
		BlockState soll = plan.zustaende[index];
		for (int z = 0; z < plan.l; z++) {
			for (int x = 0; x < plan.w; x++) {
				if (plan.index(x, y, z) != index) {
					continue;
				}
				int[] w = Burgvorschau.welt(tor, vorne, x + Bauplan.VERSATZ, z + Bauplan.VERSATZ);
				BlockPos pos = new BlockPos(w[0], tor.getY() + y, w[1]);
				if (!mc.level.hasChunk(w[0] >> 4, w[1] >> 4) || !mc.level.getBlockState(pos).is(soll.getBlock())) {
					n++;
				}
			}
		}
		return n;
	}

	/** Was im aktuellen Abschnitt noch gebaut werden muss (Block-Id -> Anzahl), schaut in der Welt nach. */
	private Map<String, Long> nochZuBauen(Minecraft mc) {
		return nochZuBauen(mc, plan, tor, vorne, von, bis);
	}

	/** Was in den Höhen von..bis noch gebaut werden muss (Block-Id -> Anzahl), schaut in der Welt nach. */
	static Map<String, Long> nochZuBauen(Minecraft mc, Bauplan plan, BlockPos tor, Direction vorne, int von, int bis) {
		long[] n = new long[plan.ids.length];
		for (int yy = von; yy <= bis; yy++) {
			for (int z = 0; z < plan.l; z++) {
				for (int x = 0; x < plan.w; x++) {
					int i = plan.index(x, yy, z);
					if (!plan.setzbar(i)) {
						continue;
					}
					int[] w = Burgvorschau.welt(tor, vorne, x + Bauplan.VERSATZ, z + Bauplan.VERSATZ);
					if (!mc.level.hasChunk(w[0] >> 4, w[1] >> 4)
							|| !mc.level.getBlockState(new BlockPos(w[0], tor.getY() + yy, w[1])).is(plan.zustaende[i].getBlock())) {
						n[i]++;
					}
				}
			}
		}
		Map<String, Long> m = new LinkedHashMap<>();
		for (int i = 0; i < n.length; i++) {
			if (n[i] > 0) {
				m.put(plan.ids[i], n[i]);
			}
		}
		return m;
	}

	/**
	 * Alle 10 Sekunden: hat er einen Block gesetzt? Wenn nicht und er auch nicht läuft, hängt er: dann diese Sorte
	 * erst mal lassen und mit der nächsten weitermachen (in der nächsten Runde kommt sie nochmal dran).
	 * Läuft er nur herum, ohne etwas zu setzen, gilt er nach einer Minute auch als hängend.
	 */
	private void aufpassen(Minecraft mc) {
		if (++aufpassTicks % 200 != 0) {
			return;
		}
		LocalPlayer p = mc.player;
		int rest = rest(mc);
		Vec3 pos = p.position();
		boolean laeuft = letztePos != null && pos.distanceTo(letztePos) > 2.0;
		letztePos = pos;
		if (rest < letzterRest) {
			letzterRest = rest;
			stillstand = 0;
			fortschrittInRunde = true;
			return;
		}
		stillstand++;
		boolean baut = mc.gameMode != null && mc.gameMode.isDestroying();
		if ((laeuft || baut) && stillstand < 6) {
			return; // unterwegs zur nächsten Stelle
		}
		BaritoneRahmen.befehl("stop");
		this.baut = false;
		sorteNr++;
		geholt = false;
		warte = 10;
		System.out.println("[Tittow Panel] Bauen: hängt bei " + new ItemStack(item).getHoverName().getString() + " in Höhe " + y
				+ " (noch " + rest + "), nimmt die nächste Sorte");
		if (FunkTerminal.anzahl(p, item) > 0) {
			// was von dieser Sorte übrig ist, zurück ins Terminal (sonst wird das Inventar mit der Zeit voll)
			zweck = Zweck.ABLEGEN;
			terminal.ablegen(mc, behalten(null));
		}
	}

	// ---- Fortschritt im Chat ------------------------------------------------------------------------

	/** Beim Losbauen: merken, wie viel schon steht, und gleich einmal den Stand melden. */
	private void fortschrittStarten(Minecraft mc, LocalPlayer p) {
		gesamt = 0;
		for (long n : plan.material(von, bis).values()) {
			gesamt += n;
		}
		fertigBeimStart = gesamt - summe(nochZuBauen(mc));
		startZeit = System.currentTimeMillis();
		naechsteMeldung = startZeit + MELDUNG_ALLE;
		chat(p, "§eSockel: " + prozent(fertigBeimStart) + " % geschafft§r – die Restzeit kommt in 5 Minuten.");
	}

	/** Alle 5 Minuten: Prozent fertig und wie lange es (bei dem bisherigen Tempo) noch dauert. */
	private void fortschrittMelden(Minecraft mc, LocalPlayer p) {
		long jetzt = System.currentTimeMillis();
		naechsteMeldung = jetzt + MELDUNG_ALLE;
		long noch = summe(nochZuBauen(mc));
		long fertig = gesamt - noch;
		long gebaut = fertig - fertigBeimStart;
		String restzeit;
		if (noch == 0) {
			restzeit = "gleich fertig";
		} else if (gebaut <= 0) {
			restzeit = "Restzeit wird noch berechnet";
		} else {
			double msJeBlock = (double) (jetzt - startZeit) / gebaut;
			restzeit = "braucht noch " + dauer((long) (msJeBlock * noch));
		}
		chat(p, "§eSockel: " + prozent(fertig) + " % geschafft§r – " + restzeit + ".");
	}

	private int prozent(long fertig) {
		return gesamt <= 0 ? 100 : (int) Math.floor(100.0 * fertig / gesamt);
	}

	private static long summe(Map<String, Long> m) {
		long s = 0;
		for (long n : m.values()) {
			s += n;
		}
		return s;
	}

	private static String zahl(long n) {
		return String.format(Locale.GERMANY, "%,d", n);
	}

	/** z. B. "1 Std. 20 Min." oder "35 Min." */
	private static String dauer(long ms) {
		long min = Math.max(1, Math.round(ms / 60000.0));
		return min >= 60 ? (min / 60) + " Stunden " + (min % 60) + " Minuten" : min + " Minuten";
	}

	/** Wie viele Blöcke in der aktuellen Schicht (alle Sorten) noch fehlen. */
	private int schichtRest(Minecraft mc) {
		int merkIndex = index, n = 0;
		for (int i : sorten) {
			index = i;
			n += rest(mc);
		}
		index = merkIndex;
		return n;
	}

	private void abschnittFertig(Minecraft mc) {
		int nr = einstellungen.burgBauAbschnitt;
		einstellungen.burgBauAbschnitt = nr + 1;
		einstellungen.burgBauSchicht = 0;
		einstellungen.burgBauen = false; // erst mal nur der Sockel: danach ist Schluss
		einstellungen.speichern();
		stoppen(mc, "§aDer Sockel ist fertig gebaut!§r");
	}

	private void einstellungenZurueck() {
		BaritoneRahmen.einstellung("allowBreak", true);
		BaritoneRahmen.einstellung("allowPlace", true);
		if (altWegwerf != null) {
			BaritoneRahmen.einstellung("acceptableThrowawayItems", altWegwerf);
			altWegwerf = null;
		}
	}

	private static void chat(LocalPlayer p, String text) {
		p.sendSystemMessage(Component.literal("§d[Burg]§r " + text));
	}
}
