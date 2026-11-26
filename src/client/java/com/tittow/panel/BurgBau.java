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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
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
	private enum Schritt { VORBEREITEN, PRUEFEN, BAUEN, NACHSCHUB, AUFRAEUMEN }

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
	/** Größe der Felder, in denen gebaut wird (16 x 16 Blöcke). */
	private static final int FELD = 16;
	private List<int[]> felder = List.of(); // Felder {x0, z0, x1, z1} im Bauplan, von außen nach innen
	private int feldNr;                     // Stelle in "felder"
	private int[] feld;                     // aktuelles Feld (null = gerade keins)
	private int feldRunde;                  // wievielte Runde im aktuellen Feld
	private boolean fortschrittImFeld;      // hat die Runde im Feld etwas gebracht?
	private List<Integer> schichtSorten = List.of(); // alle Sorten der aktuellen Höhe
	private boolean nurMauer;               // baut nur die Mauer mit Türmen (das Innere bleibt frei)
	private String teilName = "Sockel";     // für die Meldungen im Chat
	private int[][] feldOffen;              // [Höhe - von][Feld]: wie viele Blöcke dort zuletzt noch fehlten
	private int[] laufFeld;                 // läuft gerade zu diesem Feld, weil es nicht geladen ist
	private int laufTicks;
	private boolean[] mauerNah;             // Stellen an der Mauer: nur dort sucht er nach Gerüst-Erde
	private BlockPos innenZiel;             // steht er außen: erst hierhin, auf die Innenseite
	private int innenTicks;
	private Vec3 innenPos;
	private int speicherTicks;
	/** Ab so wenig Leben sucht er Schutz (8 = 4 Herzen). */
	private static final float SCHUTZ_AB = 8f;
	/** Ab so viel Leben baut er weiter (14 = 7 Herzen). */
	private static final float WIEDER_FIT = 14f;
	private int schutz;                     // 0 = aus, 1 = baut sich ein, 2 = wartet und heilt
	private int schutzTicks;
	private List<BlockPos> schutzPlaetze = List.of();
	private int schutzNr, schutzRunde;
	private List<int[]> ringFelder = List.of();  // die Felder der Mauer (bis Höhe 19), im Kreis
	private List<int[]> turmFelder = List.of();  // die einzelnen Türme (ab Höhe 20), im Kreis
	private int turm = -1;                  // an welchem Turm er gerade baut (-1 = an der Mauer)
	/** Bis auf diese Höhe (Füße) steigt er nach einem Turm wieder runter: oben auf die Mauer. */
	private static final int ABSTIEG_BIS = 18;
	private boolean abstieg;                // steigt gerade über eine Erd-Treppe runter
	private BlockPos abstiegGeht;           // die nächste Stufe, zu der er gerade geht
	private int[] abstiegVon;               // der Turm, von dem er runtersteigt (nicht wieder hinein)
	private int abstiegTicks, abstiegFehler, abstiegSetzt;
	private boolean aufraeumenNachAbstieg;  // erst runter, dann das Gerüst abbauen
	/** Mit so viel Leben springt er vom Turm (18 = 9 Herzen). Der tiefste Sprung kostet ohne Schutz bis zu 7,5 Herzen. */
	private static final float SPRUNG_LEBEN = 18f;
	/** So tief darf er beim Sprung vom Turm fallen. */
	private static final int SPRUNG_TIEFE = 20;
	private int sprung;                     // 0 = aus, 1 = wartet auf genug Leben, 2 = springt gerade
	private BlockPos sprungZiel;            // wo er unten auf der Mauer landen soll
	private int sprungTicks;
	private Object altFallHoehe;            // Baritones Fallhöhe von vorher, kommt nach dem Sprung zurück
	private boolean schichtAngesagt;        // "Höhe ..." schon in den Chat geschrieben
	private int slotWarte;                  // Ticks gewartet, bis der Block in Slot 6 liegt
	private int nachbessern;                // wie oft nach dem Aufräumen schon nachgebessert wurde
	private boolean ausgewichen;   // bei dieser Sorte schon einmal ausgewichen (beim nächsten Hängen: nächste Sorte)
	private int ausweichTicks = -1; // >= 0: weicht gerade aus (springen, ein Stück weggehen)
	private Vec3 ausweichStart;     // wo er beim Ausweichen losging
	private final java.util.Set<Integer> leer = new java.util.HashSet<>(); // Sorten, die im Terminal wirklich alle sind
	private final java.util.Set<Integer> nachgecraftet = new java.util.HashSet<>(); // dafür schon Nachschub hergestellt
	private Object altWegwerf;  // Baritones Liste der "Wegwerf-Blöcke", wird am Ende zurückgestellt
	private final Map<String, Object> alteZeiten = new HashMap<>(); // Baritone-Einstellungen von vorher, kommen am Ende zurück

	/**
	 * Baritone-Einstellungen, die nur beim Bauen gelten:
	 * - Rechenzeiten für Wege (Millisekunden), länger als normal;
	 * - falsche Erde (vom Hochbauen) darf er abbauen, auch wenn er obendrauf steht. Sonst bleibt Gerüst-Erde dort
	 *   liegen, wo eigentlich ein anderer Block hin soll. Alles andere baut er weiterhin nicht ab.
	 */
	private static Map<String, Object> bauEinstellungen() {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("primaryTimeoutMS", 2000L);
		m.put("failureTimeoutMS", 6000L);
		m.put("planAheadPrimaryTimeoutMS", 6000L);
		m.put("planAheadFailureTimeoutMS", 10000L);
		m.put("allowBreakAnyway", new ArrayList<>(List.of(Blocks.DIRT)));
		m.put("breakFromAbove", true);
		m.put("goalBreakFromAbove", true);
		m.put("blockPlacementPenalty", 40.0); // lieber ein Stück laufen, als neue Gerüst-Erde zu setzen
		return m;
	}

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
		nurMauer = false;
		teilName = "Sockel";
		feldOffen = null;
		laufFeld = null;
		turm = -1;
		abstieg = false;
		List<int[]> abschnitte = plan.abschnitte();
		int nr = einstellungen.burgBauAbschnitt;
		if (nr >= 1) { // erst mal wird nur der Sockel gebaut
			// steht er wirklich ganz? Sonst (z. B. Löcher) nochmal nachbessern
			long fehlt = summe(nochZuBauen(mc, plan, tor, vorne, abschnitte.get(0)[0], abschnitte.get(0)[1]));
			if (fehlt == 0) {
				return "Der Sockel ist schon fertig gebaut.";
			}
			chat(p, "Am Sockel fehlen noch " + zahl(fehlt) + " Blöcke (z. B. Löcher) – er bessert nach.");
			nr = 0;
			einstellungen.burgBauAbschnitt = 0;
			einstellungen.speichern();
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

	/** Baut die Mauer mit Türmen (Außenmauer, Ecktürme, Mauertürme, Tor) vom Sockel bis ganz oben. Das Innere bleibt frei. */
	String mauerStarten(Minecraft mc, BlockPos tor, Direction vorne) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) {
			return "";
		}
		plan = Bauplan.laden();
		if (plan == null) {
			return "Der Bauplan fehlt in der Mod.";
		}
		nurMauer = true;
		teilName = "Mauer mit Türmen";
		feldOffen = null;
		laufFeld = null;
		this.tor = tor;
		this.vorne = vorne;
		von = plan.sockel;
		bis = plan.mauerOben();
		schutz = 0;
		innenZiel = null;
		turm = -1;
		abstieg = false;
		abstiegGeht = null;
		aufraeumenNachAbstieg = false;
		sprung = 0;
		Erdspur.fuerBurg(tor);
		mauerNahMachen();
		Map<String, Long> noch = nochZuBauen(mc);
		if (noch.isEmpty() && geruestBloecke(mc) == 0) { // steht noch Gerüst, läuft er durch und räumt es weg
			einstellungen.burgBauen = false;
			einstellungen.speichern();
			return "Die Mauer mit Türmen steht schon komplett.";
		}
		an = true;
		Erdspur.aktiv(true);
		einstellungen.burgBauen = true;
		einstellungen.speichern();
		start = new HashMap<>(FunkTerminal.inventar(p));
		for (BlockState s : plan.zustaende) {
			start.remove(FunkTerminal.schluessel(new ItemStack(s.getBlock().asItem())));
		}
		start.remove(FunkTerminal.schluessel(new ItemStack(Items.DIRT))); // Erde über 5 Stapel darf ins Funk-Terminal
		platzFehler = 0;
		zweck = Zweck.KEINS;
		baut = false;
		warte = 0;
		long offen = summe(noch);
		chat(p, offen > 0
				? "Mauer mit Türmen: §eEs fehlen noch " + zahl(offen) + " Blöcke.§r Er prüft das Material, stellt her, was noch fehlt, und schließt dann die Löcher von unten nach oben."
				: "Mauer mit Türmen: Es fehlt kein Block mehr, er räumt nur noch das Gerüst weg.");
		schritt = Schritt.VORBEREITEN;
		herstellen.starten(mc, noch, "die Mauer mit Türmen");
		return "Mauer bauen läuft.";
	}

	/** Der Knopf, mit dem es nach einem Stopp weitergeht (für die Chat-Meldungen). */
	private String weiterKnopf() {
		return nurMauer ? "„Mauer weiter“" : "„Sockel weiter bauen“";
	}

	void stoppen(Minecraft mc, String grund) {
		if (!an) {
			return;
		}
		an = false;
		laufFeld = null;
		innenZiel = null;
		abstieg = false;
		abstiegGeht = null;
		aufraeumenNachAbstieg = false;
		sprung = 0;
		fallHoeheZurueck();
		schutz = 0;
		Erdspur.aktiv(false);
		Mauerstand.speichern();
		if (ausweichTicks >= 0) {
			mc.options.keyJump.setDown(false); // Springen loslassen
			ausweichTicks = -1;
		}
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

	// ---- Schutz: gestorben oder wenig Leben -----------------------------------------------------

	/** Gestorben? Dann sofort anhalten und sagen, wo die Sachen liegen. */
	boolean todPruefen(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (!an || p == null || p.isAlive() && p.getHealth() > 0) {
			return false;
		}
		BlockPos wo = p.blockPosition();
		stoppen(mc, "§cDu bist gestorben bei " + wo.getX() + " " + wo.getY() + " " + wo.getZ()
				+ ".§r Bauen angehalten. Hol deine Sachen und klick dann " + weiterKnopf() + ".");
		return true;
	}

	/** Steht der Schutz schon (dann darf er essen)? */
	boolean schutzSteht() {
		return schutz == 2;
	}

	/** Bei 4 Herzen oder weniger: einbauen und warten, bis er wieder fit ist. true = solange nicht weiterbauen. */
	boolean schutzTick(Minecraft mc, LocalPlayer p) {
		if (!an || mc.gameMode == null || mc.level == null) {
			return false;
		}
		if (schutz == 0) {
			if (p.getHealth() > SCHUTZ_AB || schritt != Schritt.BAUEN && schritt != Schritt.AUFRAEUMEN || imMenue()) {
				return false;
			}
			BaritoneRahmen.befehl("stop");
			if (ausweichTicks >= 0) {
				mc.options.keyJump.setDown(false);
				ausweichTicks = -1;
			}
			baut = false;
			laufFeld = null;
			innenZiel = null;
			abstiegGeht = null; // die Erd-Treppe macht er danach weiter
			if (sprung == 2) { // mitten im Sprung: danach nochmal von oben ansetzen
				fallHoeheZurueck();
				sprung = 1;
				sprungTicks = 0;
			}
			schutz = 1;
			schutzTicks = 0;
			schutzPlaetze = List.of();
			schutzNr = 0;
			schutzRunde = 0;
			chat(p, "§eNur noch " + herzen(p) + " Herzen:§r Er baut sich einen Schutz aus Erde und wartet, bis er wieder fit ist.");
			return true;
		}
		schutzTicks++;
		if (schutz == 1) {
			if (p.onGround() || schutzTicks >= 40) { // erst landen, dann einbauen
				if (schutzTicks % 2 == 0) {
					schutzSetzen(mc, p);
				}
			}
			return true;
		}
		float leben = p.getHealth();
		if (leben >= WIEDER_FIT || schutzTicks > 20 * 180 && leben > SCHUTZ_AB) {
			schutz = 0;
			chat(p, "Wieder " + herzen(p) + " Herzen: Er baut weiter.");
			warte = 10;
			if (schritt == Schritt.AUFRAEUMEN && aufraeumenAuftrag()) {
				warte = 40;
			}
			return false;
		}
		if (schutzTicks > 20 * 180) {
			stoppen(mc, "§cNach 3 Minuten im Schutz immer noch nur " + herzen(p) + " Herzen.§r Bauen angehalten, sieh bitte nach. "
					+ weiterKnopf() + " macht danach weiter.");
			return true;
		}
		return true;
	}

	private static String herzen(LocalPlayer p) {
		return String.format(Locale.GERMANY, "%.1f", p.getHealth() / 2f).replace(",0", "");
	}

	/** Setzt rund um ihn Erde: 4 Seiten an den Füßen, 4 am Kopf, 1 oben drauf. */
	private void schutzSetzen(Minecraft mc, LocalPlayer p) {
		if (schutzPlaetze.isEmpty()) {
			BlockPos f = p.blockPosition();
			List<BlockPos> l = new ArrayList<>();
			for (BlockPos ebene : List.of(f, f.above())) {
				for (Direction d : Direction.Plane.HORIZONTAL) {
					l.add(ebene.relative(d));
				}
			}
			l.add(f.above(2));
			schutzPlaetze = l;
		}
		if (schutzNr >= schutzPlaetze.size()) {
			if (++schutzRunde >= 2) { // zweite Runde: was vorher noch keinen Nachbarn hatte
				schutz = 2;
				schutzTicks = 0;
				return;
			}
			schutzNr = 0;
		}
		int slot = erdeInHotbar(p);
		if (slot < 0) {
			chat(p, "Keine Erde in der Hotbar: Er bleibt stehen und wartet ohne Schutz.");
			schutz = 2;
			schutzTicks = 0;
			return;
		}
		BlockPos pos = schutzPlaetze.get(schutzNr++);
		if (!mc.level.getBlockState(pos).canBeReplaced()
				|| p.getBoundingBox().intersects(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1)) {
			return;
		}
		erdeSetzen(mc, p, pos);
	}

	/** Setzt einen Block Erde an diese Stelle, angelehnt an einen festen Nachbarn. false = keine Erde oder kein Nachbar. */
	private boolean erdeSetzen(Minecraft mc, LocalPlayer p, BlockPos pos) {
		int slot = erdeInHotbar(p);
		if (slot < 0) {
			return false;
		}
		for (Direction d : Direction.values()) {
			BlockPos n = pos.relative(d);
			if (mc.level.getBlockState(n).getCollisionShape(mc.level, n).isEmpty() || mc.level.getBlockEntity(n) != null) {
				continue;
			}
			Direction seite = d.getOpposite();
			Vec3 klick = Vec3.atCenterOf(n).add(seite.getStepX() * 0.5, seite.getStepY() * 0.5, seite.getStepZ() * 0.5);
			int alt = p.getInventory().getSelectedSlot();
			p.getInventory().setSelectedSlot(slot);
			mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(klick, seite, n, false));
			p.getInventory().setSelectedSlot(alt);
			return true;
		}
		return false;
	}

	/** Slot 5, sonst Slot 6, sonst eine andere Stelle in der Hotbar mit Erde (nie Slot 9). -1 = keine. */
	private static int erdeInHotbar(LocalPlayer p) {
		Inventory inv = p.getInventory();
		if (inv.getItem(GERUEST_SLOT).is(Items.DIRT)) {
			return GERUEST_SLOT;
		}
		for (int i = 0; i < 8; i++) {
			if (inv.getItem(i).is(Items.DIRT)) {
				return i;
			}
		}
		return -1;
	}

	void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (!an || p == null || mc.level == null) {
			return;
		}
		if (nurMauer && ++speicherTicks >= 600) { // alle 30 Sekunden die Gerüst-Liste sichern
			speicherTicks = 0;
			Erdspur.speichern();
			Mauerstand.speichern(); // was schon steht
		}
		if (schritt == Schritt.VORBEREITEN) {
			if (herstellen.laeuft()) {
				herstellen.tick(mc);
				return;
			}
			if (!herstellen.zuEndeGelaufen()) {
				stoppen(mc, "Herstellen wurde abgebrochen – Bauen angehalten. " + weiterKnopf() + " startet neu.");
				return;
			}
			schritt = Schritt.PRUEFEN;
			zweck = Zweck.ZAEHLEN;
			terminal.zaehlen(mc);
			return;
		}
		if (schritt == Schritt.NACHSCHUB) { // eine Sorte war alle: erst nachcraften, dann weiterbauen
			if (herstellen.laeuft()) {
				herstellen.tick(mc);
				return;
			}
			nachschubEnde(mc, p);
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
		if (sprung > 0) {
			springen(mc, p);
			return;
		}
		if (abstieg) {
			absteigen(mc, p);
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
		} else if (z == Zweck.ABLEGEN) {
			if (meldung.startsWith("OK")) {
				platzFehler = 0; // es ist wieder Platz im Inventar
			}
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
			// im Terminal ist wirklich nichts mehr davon: erst versuchen, Nachschub herzustellen
			if (nachschubStarten(mc, p)) {
				return;
			}
			// geht auch nicht: diese Sorte auslassen und mit den anderen weiterbauen
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

	/**
	 * Eine Sorte ist im Funk-Terminal alle: Bauen kurz anhalten und aus den Rohstoffen im Terminal alles nachcraften
	 * bzw. brennen, was noch fehlt (nicht nur diese Sorte, damit er nicht bei jeder Sorte einzeln losmuss).
	 * Pro Sorte nur einmal je Start. Roter Beton geht nicht (das Pulver muss ins Wasser). false = kein Nachschub möglich.
	 */
	private boolean nachschubStarten(Minecraft mc, LocalPlayer p) {
		String id = plan.ids[index];
		if (nachgecraftet.contains(index) || Herstellen.WASSER.containsKey(id)) {
			return false;
		}
		nachgecraftet.add(index);
		if (baut) {
			BaritoneRahmen.befehl("stop");
			baut = false;
		}
		Map<String, Long> noch = nochZuBauen(mc);
		if (noch.isEmpty()) {
			return false;
		}
		chat(p, "§e" + new ItemStack(item).getHoverName().getString() + " ist im Funk-Terminal alle§r – er stellt Nachschub her"
				+ " (aus den Rohstoffen im Terminal) und baut dann weiter.");
		schritt = Schritt.NACHSCHUB;
		herstellen.starten(mc, noch, nurMauer ? "den Nachschub für die Mauer" : "den Nachschub für den Sockel");
		return true;
	}

	/** Nachschub fertig (oder ging nicht): Baritone wieder aufs Bauen stellen und mit derselben Sorte weitermachen. */
	private void nachschubEnde(Minecraft mc, LocalPlayer p) {
		schritt = Schritt.BAUEN;
		// Herstellen stellt Baritone am Ende aufs Abbauen: das darf er beim Bauen nicht (sonst reißt er Fertiges ein)
		BaritoneRahmen.einstellung("allowBreak", false);
		BaritoneRahmen.einstellung("allowPlace", true);
		BaritoneRahmen.kameraFolgen();
		if (!herstellen.zuEndeGelaufen()) {
			chat(p, "Nachschub herstellen ging nicht ganz – er baut mit dem weiter, was da ist.");
		}
		geholt = false;
		lagerLeer = false;
		platzFehler = 0;
		slotWarte = 0;
		warte = 10;
	}

	/** Nach dem Herstellen: ist jetzt alles für den Abschnitt da? Sonst sagen, was fehlt, und mit dem bauen, was da ist. */
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
			chat(p, "§cFür " + (nurMauer ? "die Mauer mit Türmen" : "den Sockel") + " fehlt noch Material:§r");
			for (Map.Entry<String, Long> e : fehlt.entrySet()) {
				p.sendSystemMessage(Component.literal(" §c✘ ").append(new ItemStack(Herstellen.item(e.getKey())).getHoverName())
						.append(Component.literal("§7: fehlen " + String.format(Locale.GERMANY, "%,d", e.getValue()))));
			}
			chat(p, "Er baut trotzdem schon mit dem, was da ist. Geht eine Sorte aus, craftet er nach, so weit die Rohstoffe reichen."
					+ " Den Rest bitte besorgen und danach " + weiterKnopf() + " klicken.");
		} else {
			chat(p, "§aAlles da.§r Jetzt wird gebaut, Schicht für Schicht.");
		}
		// Baritone: nichts abbauen (sonst reißt es Fertiges wieder ein), keine eigenen Blöcke zum Hochbauen benutzen
		BaritoneRahmen.einstellung("allowInventory", false);
		BaritoneRahmen.kameraFolgen(); // Kamera schaut, wohin er läuft und baut
		BaritoneRahmen.einstellung("allowBreak", false);
		BaritoneRahmen.einstellung("allowPlace", true);
		BaritoneRahmen.einstellung("buildInLayers", false);
		BaritoneRahmen.einstellung("okIfWater", false);
		// mehr Zeit zum Wegrechnen (sonst nur halbe, blaue Wege und Hin und Her) und falsche Erde abbauen dürfen
		for (Map.Entry<String, Object> e : bauEinstellungen().entrySet()) {
			if (!alteZeiten.containsKey(e.getKey())) {
				Object alt = BaritoneRahmen.einstellungLesen(e.getKey());
				if (alt != null) {
					alteZeiten.put(e.getKey(), alt);
				}
			}
			BaritoneRahmen.einstellung(e.getKey(), e.getValue());
		}
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
		sorten = List.of();
		felder = nurMauer ? List.of() : felderMachen();
		if (nurMauer) {
			ringFelder = mauerFelder();
			turmFelder = plan.mauerTuerme();
			feldOffen = new int[bis - von + 1][];
			for (int yy = von; yy <= bis; yy++) {
				List<int[]> f = felderFuer(yy);
				feldOffen[yy - von] = new int[f.size()];
				for (int nr = 0; nr < f.size(); nr++) {
					feldOffen[yy - von][nr] = feldZaehlen(mc, yy, f.get(nr)); // geladen: nachsehen, sonst der gemerkte Stand
				}
			}
			Mauerstand.speichern();
			lochUebersicht(p);
			mauerNahMachen();
			vonVorn(mc, p);
		}
		schichtSorten = sortenDerSchicht(y);
		feldNr = feldAnfang();
		feld = null;
		schichtAngesagt = false;
		slotWarte = 0;
		runde = 0;
		fortschrittInRunde = false;
		baut = false;
		// alles vom letzten Mal vergessen (sonst holt er für die erste Sorte nichts und Baritone hat kein Material)
		geholt = false;
		lagerLeer = false;
		erdeGeholt = false;
		platzFehler = 0;
		leer.clear();
		nachgecraftet.clear();
		nachbessern = 0;
		ausgewichen = false;
		ausweichTicks = -1;
	}

	private boolean enthaeltLava() {
		for (int yy = von; yy <= bis; yy++) {
			for (int z = 0; z < plan.l; z++) {
				for (int x = 0; x < plan.w; x++) {
					if ((!nurMauer || Bauplan.mauerRing(x, z)) && plan.ids[plan.index(x, yy, z)].equals("minecraft:lava")) {
						return true;
					}
				}
			}
		}
		return false;
	}

	// ---- Bauen ---------------------------------------------------------------------------

	private void bauen(Minecraft mc, LocalPlayer p) {
		if (ausweichTicks >= 0) {
			ausweichen(mc, p);
			return;
		}
		if (laufFeld != null) { // unterwegs zu einem Feld, das noch nicht geladen ist
			boolean da = feldGeladen(mc, laufFeld);
			if (da || ++laufTicks > 20 * 60) {
				BaritoneRahmen.befehl("stop");
				laufFeld = null;
				if (!da) {
					feldNr++; // kommt nicht hin: das holt er in der nächsten Runde nach
				}
				warte = 10;
			}
			return;
		}
		if (innenZiel != null) { // stand außen: erst auf die Innenseite, damit das Gerüst innen entsteht
			innenTicks++;
			boolean da = p.position().distanceTo(Vec3.atBottomCenterOf(innenZiel)) < 1.5;
			boolean steht = false;
			if (innenTicks % 40 == 0) {
				steht = p.position().distanceTo(innenPos) < 0.5;
				innenPos = p.position();
			}
			if (da || steht || innenTicks > 20 * 20) {
				BaritoneRahmen.befehl("stop");
				innenZiel = null;
				warte = 5;
			}
			return;
		}
		if (baut) {
			versorgung.nachlegen(mc, p, item);
			if (BaritoneRahmen.bautGerade()) {
				int hab = FunkTerminal.anzahl(p, item);
				if (FunkTerminal.inventarVoll(p)) {
					platzMachen(mc); // ins Funk-Terminal: das Baumaterial bleibt, 5 Stapel Gerüst-Erde auch
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
			// Baritone ist fertig (oder kommt nicht weiter): weiter mit der nächsten Sorte im Feld. Was von dieser Sorte
			// noch fehlt, kommt in der nächsten Runde dran (dann haben die Blöcke oft schon Nachbarn, an die er sie setzen kann).
			baut = false;
			if (rest(mc) < passStartRest) {
				fortschrittInRunde = true;
				fortschrittImFeld = true;
			}
			sorteNr++;
			geholt = false;
			warte = 10;
			return;
		}
		// nächste Sorte im Feld, nächstes Feld bzw. nächste Schicht
		while (true) {
			if (feld == null) {
				if (feldNr >= feldEnde()) {
					// alle Felder der Schicht durch. Fehlt noch etwas und hat die Runde etwas gebracht: nochmal alle Felder.
					int offen = schichtRest(mc);
					if (offen > 0 && fortschrittInRunde) {
						feldNr = feldAnfang();
						runde++;
						fortschrittInRunde = false;
						continue;
					}
					if (offen > 0) {
						chat(p, "In Höhe " + y + " fehlen noch " + offen + " Blöcke, da kommt er gerade nicht hin – die holt er am Schluss nach.");
					}
					naechsteSchicht(mc, p);
					return;
				}
				if (nurMauer && !feldGeladen(mc, felder.get(feldNr))) {
					if (feldOffen[y - von][feldNr] == 0) { // weit weg, aber dort steht schon alles
						feldNr++;
						continue;
					}
					zumFeldLaufen(felder.get(feldNr));
					return;
				}
				feld = felder.get(feldNr);
				sorten = sortenImFeld(mc);
				sorteNr = 0;
				feldRunde = 0;
				fortschrittImFeld = false;
				if (sorten.isEmpty()) { // in diesem Feld steht schon alles
					feldVerlassen(mc);
				} else if (nurMauer && nachInnen(mc, p, feld)) {
					return;
				}
				continue;
			}
			if (sorteNr >= sorten.size()) {
				// Feld einmal durch: hat es etwas gebracht und fehlt noch etwas, nochmal (höchstens 3 Mal), sonst nächstes Feld
				List<Integer> noch = sortenImFeld(mc);
				if (fortschrittImFeld && feldRunde < 2 && !noch.isEmpty()) {
					sorten = noch;
					sorteNr = 0;
					feldRunde++;
					fortschrittImFeld = false;
					continue;
				}
				feldVerlassen(mc);
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
		if (hab == 0) { // auch nach dem Holen nichts da: diese Sorte erst mal lassen
			sorteNr++;
			geholt = false;
			return;
		}
		// erst wenn der Block wirklich in Slot 6 liegt, Baritone starten (sonst "Missing materials" und er hängt)
		if (!p.getInventory().getItem(Versorgung.ERDE_SLOT).is(item) && slotWarte < 60) {
			slotWarte++;
			versorgung.sofortNachlegen(mc, p, item);
			return;
		}
		if (slotWarte >= 60) {
			System.out.println("[Tittow Panel] Bauen: " + new ItemStack(item).getHoverName().getString()
					+ " kam nicht in Slot 6, startet trotzdem");
		}
		slotWarte = 0;
		geholt = false;
		lagerLeer = false;
		if (!passStarten()) {
			stoppen(mc, "Baritone hat den Bauplan nicht angenommen.");
			return;
		}
		baut = true;
		ausgewichen = false; // neue Sorte: beim nächsten Hängen darf er wieder erst ausweichen
		aufpassTicks = 0;
		stillstand = 0;
		letzterRest = rest;
		passStartRest = rest;
		letztePos = p.position();
		warte = 40; // Baritone braucht einen Moment
		if (!schichtAngesagt) {
			schichtAngesagt = true;
			chat(p, turm >= 0
					? "Turm " + (turm + 1) + " von " + turmFelder.size() + ", Höhe " + y + " (Y " + (tor.getY() + y) + ")."
					: "Höhe " + y + " (Y " + (tor.getY() + y) + "): " + schichtSorten.size() + " Blocksorte(n), in "
							+ felder.size() + " Feldern.");
		}
	}

	/** Nächste Höhe (oder oben angekommen: Übersprungenes nachholen bzw. Abschnitt fertig). */
	private void naechsteSchicht(Minecraft mc, LocalPlayer p) {
		runde = 0;
		fortschrittInRunde = false;
		feld = null;
		feldNr = 0;
		schichtAngesagt = false;
		if (nurMauer) { // nächste Höhe, die noch nicht steht: erst die Mauer, dann Turm für Turm
			if (!naechsteOffeneStelle(mc, p)) {
				y = bis + 1;
			}
			feldNr = feldAnfang();
		} else {
			y++;
		}
		einstellungen.burgBauSchicht = y; // merken, wo er gerade ist
		einstellungen.speichern();
		if (y > bis) {
			// oben angekommen: was übersprungen wurde, jetzt nachholen (bis zu 3 Durchgänge von unten nach oben)
			long offenGesamt = offenGesamt(mc);
			if (offenGesamt > 0 && nachholRunde < 3) {
				nachholRunde++;
				chat(p, "Jetzt macht er fertig, was er übersprungen hat (" + offenGesamt + " Blöcke, Durchgang " + nachholRunde + " von 3).");
				if (nurMauer) {
					vonVorn(mc, p);
				} else {
					y = von;
				}
				feldNr = feldAnfang();
				schichtSorten = sortenDerSchicht(y);
				restAblegen(mc);
				return;
			}
			if (offenGesamt > 0 && nurMauer) {
				restStellenMelden(mc, p);
			}
			if (offenGesamt > 0 && !leer.isEmpty()) {
				StringBuilder namen = new StringBuilder();
				for (int i : leer) {
					namen.append(namen.length() > 0 ? ", " : "").append(new ItemStack(plan.zustaende[i].getBlock().asItem()).getHoverName().getString());
				}
				chat(p, "§c" + offenGesamt + " Blöcke fehlen noch.§r Im Funk-Terminal war alle: " + namen
						+ ". Besorg mehr und klick " + weiterKnopf() + ".");
			} else if (offenGesamt > 0) {
				chat(p, "§c" + offenGesamt + " Blöcke gehen auch nach 3 Nachhol-Durchgängen nicht§r – die bitte selbst setzen.");
			}
			if (nurMauer) { // steht er noch oben auf einem Turm: erst runter
				turm = -1;
				abstiegWennNoetig(p, ABSTIEG_BIS);
				if (abstieg) {
					aufraeumenNachAbstieg = true;
					return;
				}
			}
			aufraeumenStarten(mc, p); // erst das Erd-Gerüst wieder weg, dann ist der Abschnitt fertig
			return;
		}
		schichtSorten = sortenDerSchicht(y);
		restAblegen(mc);
	}

	// ---- Nur die Mauer mit Türmen -------------------------------------------------------------

	/** Die Felder, in denen Mauer steht, einmal im Kreis herum. Die letzte Reihe liegt am Rand, so passt jeder Turm hinein. */
	private List<int[]> mauerFelder() {
		List<Integer> xs = feldGrenzen(plan.w), zs = feldGrenzen(plan.l);
		List<int[]> f = new ArrayList<>();
		for (int zi = 0; zi < zs.size() - 1; zi++) {
			for (int xi = 0; xi < xs.size() - 1; xi++) {
				int[] k = { xs.get(xi), zs.get(zi), xs.get(xi + 1) - 1, zs.get(zi + 1) - 1 };
				for (int yy = von; yy <= Math.min(bis, Bauplan.TURM_AB - 1); yy++) {
					if (feldZaehlen(null, yy, k) > 0) {
						f.add(k);
						break;
					}
				}
			}
		}
		double cx = (plan.w - 1) / 2.0, cz = (plan.l - 1) / 2.0;
		f.sort((a, b) -> Double.compare(Math.atan2((a[1] + a[3]) / 2.0 - cz, (a[0] + a[2]) / 2.0 - cx),
				Math.atan2((b[1] + b[3]) / 2.0 - cz, (b[0] + b[2]) / 2.0 - cx)));
		return f;
	}

	private static List<Integer> feldGrenzen(int n) {
		List<Integer> g = new ArrayList<>();
		for (int v = 0; v < n - FELD; v += FELD) {
			g.add(v);
		}
		g.add(n - FELD);
		g.add(n);
		return g;
	}

	/** Wie viele Mauer-Blöcke in Höhe yy im Feld f noch fehlen. mc = null: wie viele dort laut Plan hingehören. */
	private int feldZaehlen(Minecraft mc, int yy, int[] f) {
		int n = 0;
		for (int z = f[1]; z <= f[3]; z++) {
			for (int x = f[0]; x <= f[2]; x++) {
				int i = plan.index(x, yy, z);
				if (!plan.setzbar(i) || !Bauplan.mauerRing(x, z)) {
					continue;
				}
				if (mc == null) {
					n++;
					continue;
				}
				int[] w = Burgvorschau.welt(tor, vorne, x + Bauplan.VERSATZ, z + Bauplan.VERSATZ);
				if (!stehtSchon(mc, plan, tor, x, yy, z, w, i, true)) {
					n++;
				}
			}
		}
		return n;
	}

	private boolean feldGeladen(Minecraft mc, int[] f) {
		for (int[] e : new int[][] { { f[0], f[1] }, { f[2], f[1] }, { f[0], f[3] }, { f[2], f[3] } }) {
			int[] w = Burgvorschau.welt(tor, vorne, e[0] + Bauplan.VERSATZ, e[1] + Bauplan.VERSATZ);
			if (!mc.level.hasChunk(w[0] >> 4, w[1] >> 4)) {
				return false;
			}
		}
		return true;
	}

	/** Was im Feld noch fehlt. Was gerade nicht geladen ist, zählt mit dem zuletzt gesehenen Stand. */
	private int feldRest(Minecraft mc, int yy, int nr) {
		feldOffen[yy - von][nr] = feldZaehlen(mc, yy, felderFuer(yy).get(nr));
		return feldOffen[yy - von][nr];
	}

	/** Bis Höhe 19 wird in Feldern rundherum gebaut, darüber gilt jeder Turm als ein Feld. */
	private List<int[]> felderFuer(int yy) {
		return yy >= Bauplan.TURM_AB ? turmFelder : ringFelder;
	}

	private int feldAnfang() {
		return turm >= 0 ? turm : 0;
	}

	private int feldEnde() {
		return turm >= 0 ? turm + 1 : felder.size();
	}

	/** Fängt unten an: sucht die erste Stelle, an der noch etwas fehlt. */
	private void vonVorn(Minecraft mc, LocalPlayer p) {
		turm = -1;
		y = von - 1;
		if (naechsteOffeneStelle(mc, p)) {
			if (turm < 0) {
				abstiegWennNoetig(p, Math.max(ABSTIEG_BIS, y + 2));
			}
			return;
		}
		// alles steht: über die oberste Mauer-Höhe läuft er direkt ins Nachsehen und Aufräumen
		turm = -1;
		y = Math.min(bis, Bauplan.TURM_AB - 1);
		felder = ringFelder;
	}

	/**
	 * Geht von der aktuellen Stelle zur nächsten, an der etwas fehlt: an der Mauer die nächste Höhe, am Turm die nächste
	 * Höhe desselben Turms. Ist er fertig, kommt der nächste Turm dran. false = alles steht.
	 */
	private boolean naechsteOffeneStelle(Minecraft mc, LocalPlayer p) {
		if (turm < 0) {
			for (int yy = Math.max(von, y + 1); yy < Bauplan.TURM_AB && yy <= bis; yy++) {
				for (int nr = 0; nr < ringFelder.size(); nr++) {
					if (feldRest(mc, yy, nr) > 0) {
						y = yy;
						felder = ringFelder;
						return true;
					}
				}
			}
		} else {
			for (int yy = Math.max(Bauplan.TURM_AB, y + 1); yy <= bis; yy++) {
				if (feldRest(mc, yy, turm) > 0) {
					y = yy;
					return true;
				}
			}
		}
		int t = naechsterOffenerTurm(mc, p);
		if (t < 0) {
			return false;
		}
		turm = t;
		felder = turmFelder;
		for (int yy = Bauplan.TURM_AB; yy <= bis; yy++) {
			if (feldRest(mc, yy, t) > 0) {
				y = yy;
				break;
			}
		}
		chat(p, "§eJetzt Turm " + (t + 1) + " von " + turmFelder.size() + ":§r Er baut ihn ganz fertig und steigt dabei mit hoch.");
		abstiegWennNoetig(p, ABSTIEG_BIS);
		return true;
	}

	/** Der nächste Turm, an dem noch etwas fehlt: nach einem Turm der Nachbar im Kreis, sonst der nächstgelegene. -1 = keiner. */
	private int naechsterOffenerTurm(Minecraft mc, LocalPlayer p) {
		int n = turmFelder.size();
		if (turm >= 0) {
			for (int k = 1; k < n; k++) {
				int t = (turm + k) % n;
				if (turmOffen(mc, t)) {
					return t;
				}
			}
			return -1;
		}
		int[] m = Burgvorschau.plan(tor, vorne, p.getBlockX(), p.getBlockZ());
		int px = m[0] - Bauplan.VERSATZ, pz = m[1] - Bauplan.VERSATZ, beste = -1;
		double besteWeite = Double.MAX_VALUE;
		for (int t = 0; t < n; t++) {
			int[] k = turmFelder.get(t);
			double weite = Math.hypot((k[0] + k[2]) / 2.0 - px, (k[1] + k[3]) / 2.0 - pz);
			if (weite < besteWeite && turmOffen(mc, t)) {
				beste = t;
				besteWeite = weite;
			}
		}
		return beste;
	}

	private boolean turmOffen(Minecraft mc, int t) {
		for (int yy = Bauplan.TURM_AB; yy <= bis; yy++) {
			if (feldRest(mc, yy, t) > 0) {
				return true;
			}
		}
		return false;
	}

	// ---- Vom Turm wieder runter: Erd-Treppe -----------------------------------------------------

	private static boolean imKasten(int[] k, int px, int pz) {
		return px >= k[0] && px <= k[2] && pz >= k[1] && pz <= k[3];
	}

	/** Steht er höher als "grenze" und nicht auf dem Turm, der jetzt dran ist? Dann steigt er erst runter. */
	private void abstiegWennNoetig(LocalPlayer p, int grenze) {
		if (!nurMauer || abstieg || sprung > 0 || p.getBlockY() - tor.getY() <= grenze) {
			return;
		}
		int[] m = Burgvorschau.plan(tor, vorne, p.getBlockX(), p.getBlockZ());
		int px = m[0] - Bauplan.VERSATZ, pz = m[1] - Bauplan.VERSATZ;
		if (turm >= 0 && imKasten(turmFelder.get(turm), px, pz)) {
			return;
		}
		abstiegVon = null;
		for (int[] k : turmFelder) {
			if (imKasten(k, px, pz)) {
				abstiegVon = k;
				break;
			}
		}
		BaritoneRahmen.befehl("stop");
		sprungZiel = landeplatz(Minecraft.getInstance());
		if (sprungZiel != null) { // vom Turm einfach auf die Mauer runterspringen
			sprung = 1;
			sprungTicks = 0;
			chat(p, "Turm fertig: Er springt runter auf die Mauer.");
			return;
		}
		treppeStarten(p);
	}

	/** Notlösung, wenn es keinen Landeplatz gibt oder zu wenig Leben da ist: über eine Erd-Treppe runter. */
	private void treppeStarten(LocalPlayer p) {
		abstieg = true;
		abstiegGeht = null;
		abstiegTicks = 0;
		abstiegFehler = 0;
		abstiegSetzt = 0;
		BaritoneRahmen.befehl("stop");
		chat(p, "Er steigt über eine Erd-Treppe runter auf die Mauer (die Treppe baut er am Ende wieder ab).");
	}

	/**
	 * Ein Platz oben auf der fertigen Mauer direkt neben dem Turm, auf den er springen kann. Am liebsten der, der am
	 * nächsten am Ziel liegt (nächster Turm bzw. Burgmitte). null = keiner (dann die Erd-Treppe).
	 */
	private BlockPos landeplatz(Minecraft mc) {
		if (abstiegVon == null || mc.level == null) {
			return null;
		}
		int[] k = abstiegVon;
		int[] ziel = turm >= 0 ? turmFelder.get(turm) : new int[] { plan.w / 2, plan.l / 2, plan.w / 2, plan.l / 2 };
		double zx = (ziel[0] + ziel[2]) / 2.0, zz = (ziel[1] + ziel[3]) / 2.0;
		BlockPos beste = null;
		double besteWeite = Double.MAX_VALUE;
		for (int z = k[1] - 3; z <= k[3] + 3; z++) {
			for (int x = k[0] - 3; x <= k[2] + 3; x++) {
				if (imKasten(k, x, z) || x < 0 || z < 0 || x >= plan.w || z >= plan.l || !Bauplan.mauerRing(x, z)) {
					continue;
				}
				int oben = -1; // die oberste Mauer-Höhe an dieser Stelle (unter den Türmen)
				for (int yy = Bauplan.TURM_AB - 1; yy >= von; yy--) {
					if (plan.setzbar(plan.index(x, yy, z))) {
						oben = yy;
						break;
					}
				}
				if (oben < 0) {
					continue;
				}
				int[] w = Burgvorschau.welt(tor, vorne, x + Bauplan.VERSATZ, z + Bauplan.VERSATZ);
				BlockPos fuss = new BlockPos(w[0], tor.getY() + oben + 1, w[1]);
				if (frei(mc, fuss.below()) || !frei(mc, fuss) || !frei(mc, fuss.above())) {
					continue; // Mauer dort noch nicht fertig, oder kein Platz zum Landen
				}
				double weite = Math.hypot(x - zx, z - zz);
				if (weite < besteWeite) {
					beste = fuss;
					besteWeite = weite;
				}
			}
		}
		return beste;
	}

	/** Sprung vom Turm: erst genug Leben, dann darf Baritone tief fallen und springt auf den Landeplatz. */
	private void springen(Minecraft mc, LocalPlayer p) {
		sprungTicks++;
		if (sprung == 1) {
			if (p.getHealth() >= SPRUNG_LEBEN) {
				if (altFallHoehe == null) {
					altFallHoehe = BaritoneRahmen.einstellungLesen("maxFallHeightNoWater");
				}
				BaritoneRahmen.einstellung("maxFallHeightNoWater", SPRUNG_TIEFE);
				BaritoneRahmen.befehl("goto " + sprungZiel.getX() + " " + sprungZiel.getY() + " " + sprungZiel.getZ());
				sprung = 2;
				sprungTicks = 0;
			} else if (sprungTicks == 1) {
				chat(p, "Er wartet oben, bis er wieder 9 Herzen hat, dann springt er.");
			} else if (sprungTicks > 20 * 120) {
				sprungEnde(mc, p, false, "Nach 2 Minuten noch zu wenig Leben zum Springen.");
			}
			return;
		}
		boolean unten = p.onGround() && p.getBlockY() - tor.getY() <= ABSTIEG_BIS + 1;
		if (unten || p.onGround() && p.blockPosition().distManhattan(sprungZiel) <= 1) {
			sprungEnde(mc, p, true, null);
		} else if (sprungTicks > 20 * 40) {
			sprungEnde(mc, p, false, "Er findet keinen Weg zum Absprung.");
		}
	}

	private void sprungEnde(Minecraft mc, LocalPlayer p, boolean geschafft, String grund) {
		BaritoneRahmen.befehl("stop");
		fallHoeheZurueck();
		sprung = 0;
		warte = 10;
		if (!geschafft) {
			chat(p, "§e" + grund + "§r Er nimmt die Erd-Treppe.");
			treppeStarten(p); // macht nach der Treppe auch mit dem Aufräumen weiter, falls das dran war
			return;
		}
		if (aufraeumenNachAbstieg) {
			aufraeumenNachAbstieg = false;
			aufraeumenStarten(mc, p);
		}
	}

	private void fallHoeheZurueck() {
		if (altFallHoehe != null) {
			BaritoneRahmen.einstellung("maxFallHeightNoWater", altFallHoehe);
			altFallHoehe = null;
		}
	}

	/** Eine Stufe nach der anderen: Erde schräg unter den nächsten Schritt setzen, draufgehen, wieder von vorn. */
	private void absteigen(Minecraft mc, LocalPlayer p) {
		abstiegTicks++;
		if (abstiegGeht != null) {
			if (p.onGround() && p.blockPosition().equals(abstiegGeht)) {
				BaritoneRahmen.befehl("stop");
				abstiegGeht = null;
				abstiegTicks = 0;
				abstiegFehler = 0;
				abstiegSetzt = 0;
				warte = 2;
			} else if (abstiegTicks > 100) {
				BaritoneRahmen.befehl("stop");
				abstiegGeht = null;
				abstiegTicks = 0;
				if (++abstiegFehler >= 3) {
					abstiegEnde(mc, p, false);
				}
			}
			return;
		}
		if (!p.onGround()) {
			if (abstiegTicks > 100) {
				abstiegEnde(mc, p, false);
			}
			return;
		}
		BlockPos f = p.blockPosition();
		if (f.getY() - tor.getY() <= ABSTIEG_BIS) {
			abstiegEnde(mc, p, true);
			return;
		}
		if (!geruestErde(mc, p)) {
			return; // holt gerade Erde aus dem Funk-Terminal
		}
		Direction d = abstiegRichtung(mc, f);
		if (d == null) {
			abstiegEnde(mc, p, false);
			return;
		}
		BlockPos neu = f.relative(d).below(), boden = neu.below(), stuetze = f.below(2);
		if (frei(mc, boden)) { // unter der neuen Stufe ist Luft: erst ein Stützblock unter ihm, dann die Stufe daran
			BlockPos setzen = frei(mc, stuetze) ? stuetze : boden;
			if (++abstiegSetzt > 8 || !erdeSetzen(mc, p, setzen)) {
				abstiegEnde(mc, p, false);
				return;
			}
			warte = 3;
			return;
		}
		abstiegGeht = neu;
		abstiegTicks = 0;
		BaritoneRahmen.befehl("goto " + neu.getX() + " " + neu.getY() + " " + neu.getZ());
	}

	private void abstiegEnde(Minecraft mc, LocalPlayer p, boolean geschafft) {
		abstieg = false;
		abstiegGeht = null;
		BaritoneRahmen.befehl("stop");
		warte = 5;
		if (!geschafft) {
			System.out.println("[Tittow Panel] Erd-Treppe: kommt bei " + p.blockPosition().toShortString() + " nicht weiter runter");
			chat(p, "§eDie Erd-Treppe geht hier nicht weiter§r (" + p.blockPosition().toShortString() + "). Er versucht es auf normalem Weg.");
		}
		if (aufraeumenNachAbstieg) {
			aufraeumenNachAbstieg = false;
			aufraeumenStarten(mc, p);
		}
	}

	/** In welche Richtung die nächste Stufe geht: möglichst zum Ziel, aber nie zurück in den Turm hinein. */
	private Direction abstiegRichtung(Minecraft mc, BlockPos f) {
		int[] ziel = turm >= 0 ? turmFelder.get(turm) : new int[] { plan.w / 2, plan.l / 2, plan.w / 2, plan.l / 2 };
		int[] w = Burgvorschau.welt(tor, vorne, Bauplan.VERSATZ + (ziel[0] + ziel[2]) / 2, Bauplan.VERSATZ + (ziel[1] + ziel[3]) / 2);
		List<Direction> richtungen = new ArrayList<>();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			richtungen.add(d);
		}
		richtungen.sort((a, b) -> Double.compare(
				Math.hypot(f.getX() + a.getStepX() - w[0], f.getZ() + a.getStepZ() - w[1]),
				Math.hypot(f.getX() + b.getStepX() - w[0], f.getZ() + b.getStepZ() - w[1])));
		for (Direction d : richtungen) {
			BlockPos neu = f.relative(d).below();
			if (!frei(mc, neu) || !frei(mc, neu.above()) || !frei(mc, neu.above(2))
					|| kommtNochHin(mc, neu) || kommtNochHin(mc, neu.below())) {
				continue;
			}
			if (abstiegVon != null && turmAbstand(neu) < turmAbstand(f)) {
				continue; // nicht wieder ins Dach oder in den hohlen Turm
			}
			return d;
		}
		return null;
	}

	/** Gehört an diese freie Stelle noch ein Mauer-Block? Dann dort keine Treppe. */
	private boolean kommtNochHin(Minecraft mc, BlockPos pos) {
		int[] m = Burgvorschau.plan(tor, vorne, pos.getX(), pos.getZ());
		int px = m[0] - Bauplan.VERSATZ, pz = m[1] - Bauplan.VERSATZ;
		return px >= 0 && pz >= 0 && px < plan.w && pz < plan.l && Bauplan.mauerRing(px, pz)
				&& plan.setzbar(plan.index(px, pos.getY() - tor.getY(), pz)) && frei(mc, pos);
	}

	/** Wie weit diese Stelle von der Mitte des Turms weg ist, von dem er runtersteigt. */
	private double turmAbstand(BlockPos pos) {
		int[] m = Burgvorschau.plan(tor, vorne, pos.getX(), pos.getZ());
		return Math.max(Math.abs(m[0] - Bauplan.VERSATZ - (abstiegVon[0] + abstiegVon[2]) / 2.0),
				Math.abs(m[1] - Bauplan.VERSATZ - (abstiegVon[1] + abstiegVon[3]) / 2.0));
	}

	/** Wie viele Blöcke im ganzen Bauabschnitt noch fehlen. */
	/** Beim Losbauen: wo an der Mauer noch Löcher sind (die Mauer bis Höhe 19 und jeder Turm einzeln). */
	private void lochUebersicht(LocalPlayer p) {
		long mauer = 0, alle = 0;
		long[] tuerme = new long[turmFelder.size()];
		for (int yy = von; yy <= bis; yy++) {
			for (int nr = 0; nr < feldOffen[yy - von].length; nr++) {
				int n = feldOffen[yy - von][nr];
				alle += n;
				if (yy < Bauplan.TURM_AB) {
					mauer += n;
				} else {
					tuerme[nr] += n;
				}
			}
		}
		if (alle == 0) {
			return;
		}
		StringBuilder b = new StringBuilder("§eNoch " + zahl(alle) + " Blöcke offen:§r ");
		boolean erst = true;
		if (mauer > 0) {
			b.append("Mauer bis Höhe ").append(Bauplan.TURM_AB - 1).append(": ").append(zahl(mauer));
			erst = false;
		}
		for (int t = 0; t < tuerme.length; t++) {
			if (tuerme[t] > 0) {
				b.append(erst ? "" : ", ").append("Turm ").append(t + 1).append(": ").append(zahl(tuerme[t]));
				erst = false;
			}
		}
		chat(p, b.toString());
	}

	/** Was nach allen Durchgängen noch fehlt: die ersten Stellen mit Koordinaten, damit man sie selbst setzen kann. */
	private void restStellenMelden(Minecraft mc, LocalPlayer p) {
		final int zeigen = 12;
		int gefunden = 0;
		List<String> zeilen = new ArrayList<>();
		for (int yy = von; yy <= bis; yy++) {
			for (int z = 0; z < plan.l; z++) {
				for (int x = 0; x < plan.w; x++) {
					int i = plan.index(x, yy, z);
					if (!plan.setzbar(i) || !Bauplan.mauerRing(x, z)) {
						continue;
					}
					int[] w = Burgvorschau.welt(tor, vorne, x + Bauplan.VERSATZ, z + Bauplan.VERSATZ);
					if (stehtSchon(mc, plan, tor, x, yy, z, w, i, true)) {
						continue;
					}
					if (++gefunden <= zeigen) {
						zeilen.add(" §7• " + w[0] + " " + (tor.getY() + yy) + " " + w[1] + "§r – "
								+ new ItemStack(plan.zustaende[i].getBlock().asItem()).getHoverName().getString());
					}
				}
			}
		}
		if (gefunden == 0) {
			return;
		}
		chat(p, "Hier fehlt noch etwas (X Y Z):");
		for (String z : zeilen) {
			p.sendSystemMessage(Component.literal(z));
		}
		if (gefunden > zeigen) {
			p.sendSystemMessage(Component.literal(" §7… und " + (gefunden - zeigen) + " weitere"));
		}
	}

	private long offenGesamt(Minecraft mc) {
		if (!nurMauer || feldOffen == null) {
			return summe(nochZuBauen(mc));
		}
		long n = 0;
		for (int yy = von; yy <= bis; yy++) {
			int felderHier = felderFuer(yy).size();
			for (int nr = 0; nr < felderHier; nr++) {
				n += feldRest(mc, yy, nr);
			}
		}
		return n;
	}

	private void feldVerlassen(Minecraft mc) {
		if (nurMauer) {
			feldRest(mc, y, feldNr);
		}
		feld = null;
		feldNr++;
	}

	private void zumFeldLaufen(int[] f) {
		int[] w = Burgvorschau.welt(tor, vorne, Bauplan.VERSATZ + (f[0] + f[2]) / 2, Bauplan.VERSATZ + (f[1] + f[3]) / 2);
		laufFeld = f;
		laufTicks = 0;
		BaritoneRahmen.befehl("goto " + w[0] + " " + w[1]);
	}

	/** Steht er außen an der Burg? Dann erst zur Innenseite des Felds gehen. true = er läuft jetzt los. */
	private boolean nachInnen(Minecraft mc, LocalPlayer p, int[] f) {
		int[] m = Burgvorschau.plan(tor, vorne, p.getBlockX(), p.getBlockZ());
		int px = m[0] - Bauplan.VERSATZ, pz = m[1] - Bauplan.VERSATZ;
		if (turm >= 0) { // am Turm: oben auf dem Rand bleiben, sonst baut er das Dach über sich zu
			if (p.getY() >= tor.getY() + y - 0.5) {
				return false;
			}
		} else if (px >= 2 && pz >= 2 && px < plan.w - 2 && pz < plan.l - 2) {
			return false; // steht schon auf der Mauer oder innen
		}
		BlockPos ziel = innenPlatz(mc, f);
		if (ziel == null || p.position().distanceTo(Vec3.atBottomCenterOf(ziel)) < 3) {
			return false;
		}
		innenZiel = ziel;
		innenTicks = 0;
		innenPos = p.position();
		BaritoneRahmen.befehl("goto " + ziel.getX() + " " + ziel.getY() + " " + ziel.getZ());
		return true;
	}

	/** Der freie Stehplatz im Feld, der am nächsten an der Burgmitte liegt (auf dem Boden oder oben auf der Mauer). */
	private BlockPos innenPlatz(Minecraft mc, int[] f) {
		double cx = (plan.w - 1) / 2.0, cz = (plan.l - 1) / 2.0;
		for (int yy = y; yy <= y + 1; yy++) {
			BlockPos beste = null;
			double besteWeite = Double.MAX_VALUE;
			for (int z = f[1]; z <= f[3]; z++) {
				for (int x = f[0]; x <= f[2]; x++) {
					double weite = Math.max(Math.abs(x - cx), Math.abs(z - cz));
					if (weite >= besteWeite) {
						continue;
					}
					int[] w = Burgvorschau.welt(tor, vorne, x + Bauplan.VERSATZ, z + Bauplan.VERSATZ);
					BlockPos fuss = new BlockPos(w[0], tor.getY() + yy, w[1]);
					if (frei(mc, fuss) && frei(mc, fuss.above()) && !frei(mc, fuss.below())) {
						beste = fuss;
						besteWeite = weite;
					}
				}
			}
			if (beste != null) {
				return beste;
			}
		}
		return null;
	}

	private static boolean frei(Minecraft mc, BlockPos b) {
		return mc.level.getBlockState(b).getCollisionShape(mc.level, b).isEmpty() && mc.level.getFluidState(b).isEmpty();
	}

	/** Die Sorten der Höhe yy, bei der Mauer nur die, die in der Mauer vorkommen. */
	private List<Integer> sortenDerSchicht(int yy) {
		List<Integer> alle = plan.sortenInSchicht(yy);
		if (!nurMauer) {
			return alle;
		}
		boolean[] drin = new boolean[plan.ids.length];
		for (int z = 0; z < plan.l; z++) {
			for (int x = 0; x < plan.w; x++) {
				if (Bauplan.mauerRing(x, z)) {
					drin[plan.index(x, yy, z)] = true;
				}
			}
		}
		List<Integer> r = new ArrayList<>();
		for (int i : alle) {
			if (drin[i]) {
				r.add(i);
			}
		}
		return r;
	}

	/** Merkt sich die Stellen bis 5 Blöcke neben der Mauer: nur dort kann Gerüst-Erde von ihm stehen. */
	private void mauerNahMachen() {
		mauerNah = new boolean[plan.w * plan.l];
		for (int z = 0; z < plan.l; z++) {
			for (int x = 0; x < plan.w; x++) {
				boolean nah = false;
				for (int dz = -5; dz <= 5 && !nah; dz += 5) {
					for (int dx = -5; dx <= 5 && !nah; dx += 5) {
						int xx = x + dx, zz = z + dz;
						nah = xx >= 0 && zz >= 0 && xx < plan.w && zz < plan.l && Bauplan.mauerRing(xx, zz);
					}
				}
				mauerNah[z * plan.w + x] = nah;
			}
		}
	}

	/** Am Ende einer Höhe: übriges Baumaterial zurück ins Funk-Terminal (die Gerüst-Erde bleibt). */
	private void restAblegen(Minecraft mc) {
		zweck = Zweck.ABLEGEN;
		terminal.ablegen(mc, behalten(null));
	}

	/**
	 * Die Schicht in Felder von 16 x 16 teilen, Reihenfolge von außen nach innen (Ring für Ring, jeder Ring einmal
	 * herum). Außen steht schon die Mauer, so hat jeder neue Block meist schon einen Nachbarn, an den er ihn setzen kann.
	 */
	private List<int[]> felderMachen() {
		List<int[]> f = new ArrayList<>();
		for (int z0 = 0; z0 < plan.l; z0 += FELD) {
			for (int x0 = 0; x0 < plan.w; x0 += FELD) {
				f.add(new int[] { x0, z0, Math.min(x0 + FELD, plan.w) - 1, Math.min(z0 + FELD, plan.l) - 1 });
			}
		}
		double cx = (plan.w - 1) / 2.0, cz = (plan.l - 1) / 2.0;
		f.sort((a, b) -> {
			double ax = (a[0] + a[2]) / 2.0 - cx, az = (a[1] + a[3]) / 2.0 - cz;
			double bx = (b[0] + b[2]) / 2.0 - cx, bz = (b[1] + b[3]) / 2.0 - cz;
			int ring = Double.compare(Math.max(Math.abs(bx), Math.abs(bz)), Math.max(Math.abs(ax), Math.abs(az)));
			return ring != 0 ? ring : Double.compare(Math.atan2(az, ax), Math.atan2(bz, bx));
		});
		return f;
	}

	/** Welche Sorten im aktuellen Feld (in dieser Höhe) noch nicht stimmen, in der Reihenfolge der Schicht. */
	private List<Integer> sortenImFeld(Minecraft mc) {
		boolean[] fehlt = new boolean[plan.ids.length];
		for (int z = feld[1]; z <= feld[3]; z++) {
			for (int x = feld[0]; x <= feld[2]; x++) {
				int i = plan.index(x, y, z);
				if (!fehlt[i] && plan.setzbar(i) && (!nurMauer || Bauplan.mauerRing(x, z)) && !stimmt(mc, x, z, i)) {
					fehlt[i] = true;
				}
			}
		}
		List<Integer> r = new ArrayList<>();
		for (int i : schichtSorten) {
			if (fehlt[i]) {
				r.add(i);
			}
		}
		return r;
	}

	/** Steht an der Bauplan-Stelle x, z (aktuelle Höhe) schon der richtige Block? */
	private boolean stimmt(Minecraft mc, int x, int z, int i) {
		int[] w = Burgvorschau.welt(tor, vorne, x + Bauplan.VERSATZ, z + Bauplan.VERSATZ);
		return mc.level.hasChunk(w[0] >> 4, w[1] >> 4)
				&& mc.level.getBlockState(new BlockPos(w[0], tor.getY() + y, w[1])).is(plan.zustaende[i].getBlock());
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
		if (p != null && !p.isAlive()) {
			werkzeugIn5 = null; // gestorben: die Sachen liegen am Boden, nichts tauschen
			return;
		}
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
		if (nurMauer) {
			Erdspur.aufraeumen(mc.level); // Stellen vergessen, an denen inzwischen keine Erde mehr steht
		}
		int n = geruestBloecke(mc);
		if (n == 0) {
			abschnittFertig(mc);
			return;
		}
		chat(p, "Jetzt baut er sein Erd-Gerüst wieder ab (" + n + " Blöcke).");
		// allowBreak bleibt aus: er darf nur Erde abbauen (allowBreakAnyway), nie den fertigen Boden aufgraben
		BaritoneRahmen.einstellung("allowBreak", false);
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
		if (FunkTerminal.inventarVoll(p)) {
			platzMachen(mc); // die abgebaute Erde ins Funk-Terminal, sonst bleibt sie am Boden liegen
			return;
		}
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
		// zur Sicherheit nochmal nachsehen: fehlt doch noch etwas (z. B. ein Loch), wird nachgebessert
		long fehlt = offenGesamt(mc);
		if (fehlt > 0 && nachbessern < 2) {
			nachbessern++;
			chat(p, "Es fehlen noch " + zahl(fehlt) + " Blöcke – er bessert nach.");
			schritt = Schritt.BAUEN;
			nachholRunde = 0;
			if (nurMauer) {
				vonVorn(mc, p);
			} else {
				y = von;
			}
			schichtSorten = sortenDerSchicht(y);
			feld = null;
			feldNr = feldAnfang();
			runde = 0;
			fortschrittInRunde = false;
			schichtAngesagt = false;
			baut = false;
			warte = 20;
			return;
		}
		abschnittFertig(mc);
	}

	/** Baritone soll alle Gerüst-Erde im Abschnitt (und ein paar Blöcke darüber) abbauen. */
	private boolean aufraeumenAuftrag() {
		int[] a = Burgvorschau.welt(tor, vorne, Bauplan.VERSATZ, Bauplan.VERSATZ);
		int[] b = Burgvorschau.welt(tor, vorne, Bauplan.VERSATZ + plan.w - 1, Bauplan.VERSATZ + plan.l - 1);
		int oben = bis + GERUEST_HOEHE, unten = von;
		int x0 = Math.min(a[0], b[0]), x1 = Math.max(a[0], b[0]), z0 = Math.min(a[1], b[1]), z1 = Math.max(a[1], b[1]);
		if (nurMauer) { // auch die Erde, die er außerhalb der Burg gesetzt hat
			for (BlockPos e : Erdspur.alle()) {
				x0 = Math.min(x0, e.getX());
				x1 = Math.max(x1, e.getX());
				z0 = Math.min(z0, e.getZ());
				z1 = Math.max(z1, e.getZ());
				unten = Math.min(unten, e.getY() - tor.getY());
				oben = Math.max(oben, e.getY() - tor.getY());
			}
		}
		BlockPos ecke = new BlockPos(x0, tor.getY() + unten, z0);
		int breitX = x1 - x0 + 1, laengeZ = z1 - z0 + 1;
		Minecraft mc = Minecraft.getInstance();
		BlockState luft = Blocks.AIR.defaultBlockState();
		return BaritoneRahmen.planBauen("Tittow Gerüst weg", ecke, breitX, oben - unten + 1, laengeZ, (wx, wy, wz) -> {
			int[] m = Burgvorschau.plan(tor, vorne, wx, wz);
			int yy = wy - tor.getY();
			return istGeruest(mc, m[0] - Bauplan.VERSATZ, yy, m[1] - Bauplan.VERSATZ, new BlockPos(wx, wy, wz)) ? luft : null;
		});
	}

	/**
	 * Erde über dem Abschnitt, wo laut Bauplan nichts hingehört = Gerüst. Erde unten im hohlen Sockel (unter dem
	 * Boden) bleibt liegen: die sieht man nicht, und um da hinzukommen, müsste er den fertigen Boden aufgraben.
	 */
	private boolean istGeruest(Minecraft mc, int px, int yy, int pz, BlockPos pos) {
		if (nurMauer && Erdspur.enthaelt(pos)) { // die hat er nachweislich selbst gesetzt, auch außerhalb der Burg
			boolean mauerHier = Bauplan.mauerRing(px, pz) && plan.index(px, yy, pz) != 0;
			return !mauerHier && mc.level.getBlockState(pos).is(Blocks.DIRT);
		}
		if (px < 0 || pz < 0 || px >= plan.w || pz >= plan.l) {
			return false;
		}
		if (nurMauer) {
			// über dem Sockel, an der Mauer: Erde ist dort immer sein Gerüst (außer wo die Mauer selbst hingehört)
			if (yy < von || mauerNah == null || !mauerNah[pz * plan.w + px]
					|| Bauplan.mauerRing(px, pz) && plan.index(px, yy, pz) != 0) {
				return false;
			}
			return mc.level.getBlockState(pos).is(Blocks.DIRT);
		}
		if (yy <= bis) {
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
					if (nurMauer && !mauerNah[z * plan.w + x]) {
						continue; // weit weg von der Mauer: da hat er nichts gebaut
					}
					int[] w = Burgvorschau.welt(tor, vorne, x + Bauplan.VERSATZ, z + Bauplan.VERSATZ);
					if (mc.level.hasChunk(w[0] >> 4, w[1] >> 4) && istGeruest(mc, x, yy, z, new BlockPos(w[0], tor.getY() + yy, w[1]))) {
						n++;
					}
				}
			}
		}
		if (nurMauer) { // dazu die eigene Erde, die oben nicht mitgezählt wurde (außerhalb der Burg oder tiefer)
			for (BlockPos e : Erdspur.alle()) {
				int[] m = Burgvorschau.plan(tor, vorne, e.getX(), e.getZ());
				int px = m[0] - Bauplan.VERSATZ, pz = m[1] - Bauplan.VERSATZ, yy = e.getY() - tor.getY();
				boolean schonGezaehlt = px >= 0 && pz >= 0 && px < plan.w && pz < plan.l && mauerNah[pz * plan.w + px]
						&& yy >= von && yy <= bis + GERUEST_HOEHE;
				if (!schonGezaehlt && mc.level.hasChunk(e.getX() >> 4, e.getZ() >> 4) && istGeruest(mc, px, yy, pz, e)) {
					n++;
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
		terminal.holen(mc, item, Math.min(Math.max(rest - holVorher, 64), 64 * 5)); // höchstens 5 Stapel auf einmal
	}

	/** Alles ablegen, was nicht gebraucht wird (die aktuelle Sorte und die Gerüst-Erde bleiben). Hilft das mehrmals nicht: anhalten. */
	private void platzMachen(Minecraft mc) {
		if (++platzFehler > 3) {
			stoppen(mc, "§cDas Inventar ist zu voll§r (nur eigene Sachen drin). Mach ein paar Plätze frei und klick " + weiterKnopf() + ".");
			return;
		}
		zweck = Zweck.ABLEGEN;
		terminal.ablegen(mc, behalten(schritt == Schritt.AUFRAEUMEN ? null : item));
	}

	/** Was beim Ablegen im Inventar bleibt: die eigenen Sachen vom Start, die Gerüst-Erde und (falls angegeben) das Baumaterial. */
	private Map<String, Integer> behalten(Item auch) {
		Map<String, Integer> m = new HashMap<>(start);
		m.put(FunkTerminal.schluessel(new ItemStack(Items.DIRT)), nurMauer ? 320 : 100000); // bei der Mauer: 5 Stapel, der Rest ins Lager
		if (auch != null) {
			m.put(FunkTerminal.schluessel(new ItemStack(auch)), 100000);
		}
		return m;
	}

	/** Gibt Baritone die aktuelle Schicht, nur mit der aktuellen Blocksorte. */
	private boolean passStarten() {
		// nur das aktuelle Feld (kurze Wege, weniger Hin und Her)
		final int[] f = feld != null ? feld : new int[] { 0, 0, plan.w - 1, plan.l - 1 };
		int[] a = Burgvorschau.welt(tor, vorne, Bauplan.VERSATZ + f[0], Bauplan.VERSATZ + f[1]);
		int[] b = Burgvorschau.welt(tor, vorne, Bauplan.VERSATZ + f[2], Bauplan.VERSATZ + f[3]);
		BlockPos ecke = new BlockPos(Math.min(a[0], b[0]), tor.getY() + y, Math.min(a[1], b[1]));
		int breitX = Math.abs(a[0] - b[0]) + 1, laengeZ = Math.abs(a[1] - b[1]) + 1;
		final int schicht = y, sorte = index;
		final BlockState soll = plan.zustaende[index];
		return BaritoneRahmen.planBauen("Tittow Burg", ecke, breitX, 1, laengeZ, (wx, wy, wz) -> {
			if (wy != tor.getY() + schicht) {
				return null;
			}
			int[] m = Burgvorschau.plan(tor, vorne, wx, wz);
			int px = m[0] - Bauplan.VERSATZ, pz = m[1] - Bauplan.VERSATZ;
			if (px < f[0] || px > f[2] || pz < f[1] || pz > f[3]) {
				return null;
			}
			if (nurMauer && !Bauplan.mauerRing(px, pz)) {
				return null; // das Innere der Burg bleibt frei
			}
			return plan.index(px, schicht, pz) == sorte ? soll : null;
		});
	}

	/** Wie viele Stellen der aktuellen Sorte im aktuellen Feld (ohne Feld: in der ganzen Schicht) noch nicht stimmen. */
	private int rest(Minecraft mc) {
		return fehlen(mc, index, feld);
	}

	/** Wie viele Stellen in der aktuellen Höhe noch nicht stimmen: nur Sorte "sorte" (-1 = alle), nur im Feld f (null = überall). */
	private int fehlen(Minecraft mc, int sorte, int[] f) {
		int n = 0;
		int x0 = f != null ? f[0] : 0, z0 = f != null ? f[1] : 0;
		int x1 = f != null ? f[2] : plan.w - 1, z1 = f != null ? f[3] : plan.l - 1;
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				int i = plan.index(x, y, z);
				if ((sorte >= 0 ? i != sorte : !plan.setzbar(i)) || nurMauer && !Bauplan.mauerRing(x, z) || stimmt(mc, x, z, i)) {
					continue;
				}
				n++;
			}
		}
		return n;
	}

	/** Was im aktuellen Abschnitt noch gebaut werden muss (Block-Id -> Anzahl), schaut in der Welt nach. */
	private Map<String, Long> nochZuBauen(Minecraft mc) {
		return nochZuBauen(mc, plan, tor, vorne, von, bis, nurMauer);
	}

	/** Was in den Höhen von..bis noch gebaut werden muss (Block-Id -> Anzahl), schaut in der Welt nach. */
	static Map<String, Long> nochZuBauen(Minecraft mc, Bauplan plan, BlockPos tor, Direction vorne, int von, int bis) {
		return nochZuBauen(mc, plan, tor, vorne, von, bis, false);
	}

	/** Wie oben; nurMauer = nur die Mauer mit Türmen zählen (Bauplan.mauerRing). mc = null: nicht in der Welt nachsehen. */
	static Map<String, Long> nochZuBauen(Minecraft mc, Bauplan plan, BlockPos tor, Direction vorne, int von, int bis, boolean nurMauer) {
		return zaehlen(mc, plan, tor, vorne, von, bis, nurMauer ? 1 : 0);
	}

	/** Was an den Innengebäuden (alles innerhalb der Mauer, vom Sockel bis ganz oben) noch gebaut werden muss. mc = null: alles. */
	static Map<String, Long> nochInnen(Minecraft mc, Bauplan plan, BlockPos tor, Direction vorne) {
		return zaehlen(mc, plan, tor, vorne, plan.sockel, plan.h - 1, 2);
	}

	/** teil: 0 = alles, 1 = nur die Mauer mit Türmen, 2 = nur die Innengebäude (alles ohne die Mauer). */
	private static Map<String, Long> zaehlen(Minecraft mc, Bauplan plan, BlockPos tor, Direction vorne, int von, int bis, int teil) {
		boolean nurMauer = teil == 1;
		long[] n = new long[plan.ids.length];
		if (nurMauer && mc != null) {
			Mauerstand.fuerBurg(tor, plan); // was weit weg (nicht geladen) ist: der zuletzt gesehene Stand
		}
		for (int yy = von; yy <= bis; yy++) {
			for (int z = 0; z < plan.l; z++) {
				for (int x = 0; x < plan.w; x++) {
					int i = plan.index(x, yy, z);
					if (!plan.setzbar(i) || teil == 1 && !Bauplan.mauerRing(x, z) || teil == 2 && Bauplan.mauerRing(x, z)) {
						continue;
					}
					if (mc == null) {
						n[i]++;
						continue;
					}
					int[] w = Burgvorschau.welt(tor, vorne, x + Bauplan.VERSATZ, z + Bauplan.VERSATZ);
					if (!stehtSchon(mc, plan, tor, x, yy, z, w, i, nurMauer)) {
						n[i]++;
					}
				}
			}
		}
		if (nurMauer && mc != null) {
			Mauerstand.speichern();
		}
		List<Integer> reihe = new ArrayList<>();
		for (int i = 0; i < n.length; i++) {
			if (n[i] > 0) {
				reihe.add(i);
			}
		}
		reihe.sort((a, b) -> Long.compare(n[b], n[a])); // die meisten zuerst
		Map<String, Long> m = new LinkedHashMap<>();
		for (int i : reihe) {
			m.put(plan.ids[i], n[i]);
		}
		return m;
	}

	/**
	 * Steht an der Bauplan-Stelle schon der richtige Block? Ist der Teil gerade geladen, wird nachgesehen (und bei der Mauer
	 * gemerkt), sonst gilt bei der Mauer der zuletzt gesehene Stand und beim Sockel "fehlt noch".
	 */
	private static boolean stehtSchon(Minecraft mc, Bauplan plan, BlockPos tor, int x, int yy, int z, int[] w, int i, boolean nurMauer) {
		if (mc.level.hasChunk(w[0] >> 4, w[1] >> 4)) {
			boolean richtig = mc.level.getBlockState(new BlockPos(w[0], tor.getY() + yy, w[1])).is(plan.zustaende[i].getBlock());
			if (nurMauer) {
				Mauerstand.merken(x, yy, z, richtig);
			}
			return richtig;
		}
		return nurMauer && Mauerstand.steht(x, yy, z);
	}

	/**
	 * Alle 10 Sekunden: hat er einen Block gesetzt? Wenn nicht und er auch nicht läuft, hängt er: dann diese Sorte
	 * erst mal lassen und mit der nächsten weitermachen (in der nächsten Runde kommt sie nochmal dran).
	 * Läuft er nur herum, ohne etwas zu setzen, gilt er nach einer Minute auch als hängend.
	 */
	/**
	 * Ausweichen nach dem Hängen: springen, dann 3-4 Blöcke weg zu einer freien Stelle gehen (höchstens 6 Sekunden)
	 * und mit derselben Sorte neu ansetzen.
	 */
	private void ausweichen(Minecraft mc, LocalPlayer p) {
		ausweichTicks++;
		boolean springen = ausweichTicks <= 4 || (ausweichTicks >= 40 && ausweichTicks <= 44);
		mc.options.keyJump.setDown(springen);
		if (ausweichTicks == 8) {
			BlockPos ziel = ausweichZiel(mc, p);
			if (ziel != null) {
				BaritoneRahmen.befehl("goto " + ziel.getX() + " " + ziel.getY() + " " + ziel.getZ());
			} else { // keine freie Stelle gefunden: einfach in eine Richtung
				double w = p.getRandom().nextInt(8) * Math.PI / 4;
				BaritoneRahmen.befehl("goto " + (p.getBlockX() + (int) Math.round(Math.cos(w) * 4)) + " "
						+ (p.getBlockZ() + (int) Math.round(Math.sin(w) * 4)));
			}
		}
		boolean weg = ausweichTicks > 20 && p.position().distanceTo(ausweichStart) >= 3.0;
		if (ausweichTicks < 120 && !weg) {
			return;
		}
		mc.options.keyJump.setDown(false);
		BaritoneRahmen.befehl("stop");
		ausweichTicks = -1;
		versorgung.nachlegen(mc, p, item);
		if (!passStarten()) {
			stoppen(mc, "Baritone hat den Bauplan nicht angenommen.");
			return;
		}
		baut = true;
		aufpassTicks = 0;
		stillstand = 0;
		letzterRest = rest(mc);
		letztePos = p.position();
		warte = 40;
	}

	/** Eine freie Stelle 3-4 Blöcke entfernt, auf der er stehen kann (Boden fest, Füße und Kopf frei). */
	private BlockPos ausweichZiel(Minecraft mc, LocalPlayer p) {
		BlockPos hier = p.blockPosition();
		int anfang = p.getRandom().nextInt(8);
		for (int abstand = 4; abstand >= 3; abstand--) {
			for (int k = 0; k < 8; k++) {
				double w = ((anfang + k) % 8) * Math.PI / 4;
				int dx = (int) Math.round(Math.cos(w) * abstand), dz = (int) Math.round(Math.sin(w) * abstand);
				for (int dy : new int[] { 0, 1, -1 }) {
					BlockPos f = hier.offset(dx, dy, dz);
					if (mc.level.getBlockState(f).isAir() && mc.level.getBlockState(f.above()).isAir()
							&& !mc.level.getBlockState(f.below()).getCollisionShape(mc.level, f.below()).isEmpty()) {
						return f;
					}
				}
			}
		}
		return null;
	}

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
			fortschrittImFeld = true;
			return;
		}
		stillstand++;
		boolean baut = mc.gameMode != null && mc.gameMode.isDestroying();
		if ((laeuft || baut) && stillstand < 6) {
			return; // unterwegs zur nächsten Stelle
		}
		BaritoneRahmen.befehl("stop");
		this.baut = false;
		if (!ausgewichen) {
			// erst mal springen, 3-4 Blöcke zur Seite gehen und mit derselben Sorte neu ansetzen
			ausgewichen = true;
			ausweichTicks = 0;
			ausweichStart = pos;
			System.out.println("[Tittow Panel] Bauen: hängt bei " + new ItemStack(item).getHoverName().getString() + " in Höhe " + y
					+ " (noch " + rest + "), springt, geht ein Stück weg und setzt neu an");
			return;
		}
		ausgewichen = false;
		sorteNr++;
		geholt = false;
		warte = 10;
		System.out.println("[Tittow Panel] Bauen: hängt bei " + new ItemStack(item).getHoverName().getString() + " in Höhe " + y
				+ " (noch " + rest + "), nimmt die nächste Sorte");
		// der Rest dieser Sorte bleibt im Inventar (im nächsten Feld braucht er sie wieder); ist es zu voll, legt er vor dem Holen ab
	}

	// ---- Fortschritt im Chat ------------------------------------------------------------------------

	/** Beim Losbauen: merken, wie viel schon steht, und gleich einmal den Stand melden. */
	private void fortschrittStarten(Minecraft mc, LocalPlayer p) {
		gesamt = nurMauer ? summe(nochZuBauen(null, plan, tor, vorne, von, bis, true)) : 0;
		if (!nurMauer) {
			for (long n : plan.material(von, bis).values()) {
				gesamt += n;
			}
		}
		fertigBeimStart = gesamt - summe(nochZuBauen(mc));
		startZeit = System.currentTimeMillis();
		naechsteMeldung = startZeit + MELDUNG_ALLE;
		chat(p, "§e" + teilName + ": " + prozent(fertigBeimStart) + " % geschafft§r – die Restzeit kommt in 5 Minuten.");
	}

	/** Alle 5 Minuten: Prozent fertig und wie lange es (bei dem bisherigen Tempo) noch dauert. */
	private void fortschrittMelden(Minecraft mc, LocalPlayer p) {
		long jetzt = System.currentTimeMillis();
		naechsteMeldung = jetzt + MELDUNG_ALLE;
		long noch = offenGesamt(mc);
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
		chat(p, "§e" + teilName + ": " + prozent(fertig) + " % geschafft§r – " + restzeit + ".");
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
		if (nurMauer && turm >= 0) {
			return feldRest(mc, y, turm); // am Turm zählt nur dieser eine Turm
		}
		if (nurMauer) {
			int n = 0;
			for (int nr = 0; nr < felder.size(); nr++) {
				n += feldRest(mc, y, nr);
			}
			return n;
		}
		return fehlen(mc, -1, null);
	}

	private void abschnittFertig(Minecraft mc) {
		if (nurMauer) {
			long offen = feldOffen != null ? offenGesamt(mc) : summe(nochZuBauen(mc));
			Erdspur.aufraeumen(mc.level);
			einstellungen.burgBauen = offen > 0; // fehlt noch etwas, heißt der Knopf weiter „Mauer weiter“
			einstellungen.burgBauSchicht = 0;
			einstellungen.speichern();
			stoppen(mc, offen > 0
					? "§eDie Mauer steht bis auf " + zahl(offen) + " Blöcke.§r „Mauer weiter“ versucht den Rest nochmal."
					: "§aDie Mauer mit Türmen ist fertig gebaut!§r");
			return;
		}
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
		for (Map.Entry<String, Object> e : alteZeiten.entrySet()) {
			BaritoneRahmen.einstellung(e.getKey(), e.getValue());
		}
		alteZeiten.clear();
	}

	private static void chat(LocalPlayer p, String text) {
		p.sendSystemMessage(Component.literal("§d[Burg]§r " + text));
	}
}
