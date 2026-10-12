# Backlog

Open work, grouped by how ready it is rather than by rule section. A snapshot taken 2026-10-09;
every code pointer below was checked against the tree on that date rather than recited from notes.

**Two things this file is not.** It is not a record of what is *done* — the git log and the code
are that, and a backlog that lists finished work starts lying the moment someone finishes
something. And it is not a priority order: the headings say how ready a thing is, not which to do
next.

**Before building anything here, read the rule.** The rulebook PDFs are in `data/rules/`
(gitignored — ADB's watermarked product, never commit them). Grepping the text first is cheap;
inference has produced wrong implementations that each passed their own tests.

**And check the code before believing any line in this file.** Three separate notes once said
erratic maneuvers were unmodelled; EM was fully built. This file was itself drafted with a
pointer to a `TODO` for plasma fast-load that had been complete for four months. A claim that
something does not exist is the kind that rots quietest.

---

## Ready to start

Each of these was named by an earlier session as the next sensible slice in its area, with the
groundwork already in place.

- **R1.32 Base Augmentation Modules.** Module R1 SSD book p.78, not yet read. All nine Base
  Stations declare the fighter, hangar and PF modules out of their baseline, so this is what
  finishes them. Build module **purchasing** with fighters as one entry in a catalogue — the
  sheet draws fighter modules because it has to draw something, and there are many kinds. A
  module is bought, not declared, so the refit model is close but wrong for it: you may buy none,
  one, or several of the same kind.
- **Ballistic targeting (F4.0)** — a seeking weapon aimed at a HEX instead of a unit. This entry
  replaced one that read "a seeker cannot target a hex", which named the symptom and pointed at a
  feature the rules do not have; the rules call it ballistic targeting and give it its own
  section. Read F4.0 and P2.7 before starting, not this summary.

  **The core (F4.0).** "To establish a ballistic course, designate a hex, not a unit, as the
  target of the seeking weapon" (F4.11). It pursues that hex by the most direct route and then
  EVADES it (C1.3223) rather than stopping. Released from control the instant it launches and
  never counts against the control limit (F4.12). One hex only, never a sequence (F4.13). Normal
  range (F4.14). **It can never hit a ship or base in space** (F4.412).

  **And by default it does NOT explode** (F4.21). The uses are detecting a minefield, clearing a
  path through asteroids, saturating defences, and deception. `Game.clearAsteroidPath` already
  exists, so we built one consumer of this framework without the framework.

  **Ground attack (F4.22 → P2.713)**, which is the slice the bombardment missions want. Seeking
  weapons may be fired at a ground target ballistically **from four hexes or less**, **never under
  passive fire control** (tracking is inadequate), **cannot be distracted by ECM or a wild
  weasel**, and **do explode on impact**. Submunitions from a ballistic MW drone may not target a
  ground base. The short reach plus mandatory active fire control is what forces an attacker
  through the static defences and the defending fleet rather than standing off — and because the
  run is immune to EW, a defender's only answers are shooting the seeker down or killing the
  launcher.

  **THE PLANET ITSELF IS A LEGAL TARGET**, which P2.522 settles: "Seeking weapons fired at a
  planet are targeted on specific points or installations... In scenarios where general damage to
  the planet is called for (P2.525), the weapon crews are selecting appropriate cities or
  industrial areas **on the hex side selected by the player**." So the model is the one
  `bombardPlanet(attacker, planet, targetSide, weapons)` already uses, and `Terrain.damageBySide[6]`
  is already the right sink. P2.311 sets the threshold: damage on each hex side, 200 to devastate
  unless the scenario says otherwise.

  **Three different targets, not one.** A GROUND BASE on the planet (P2.713); the PLANET itself,
  by hex side (P2.522); and a planet a seeker acquires BY ACCIDENT when one blocks its line to its
  real target (P2.33 — self-guided weapons only, and P2.3223 makes that reassignment permanent,
  so it will not revert even if the original target voids a weasel).

  **Open question, flagged rather than guessed:** P2.713's four-hex limit is written about a
  ground base, and P2.522 describes planet bombardment without restating it. Whether the limit
  binds all ballistic ground attack or only picking out a base decides whether a bombarding ship
  must close. Settle it from the text before building.

  **Decided 2026-10-11, and the rejected option is worth keeping.** The owner proposed giving
  every planet a default "base" representing its infrastructure, so bombardment would reuse the
  ground-installation rules. Rejected on three grounds. We have NO ground bases at all — no
  concept in code, nothing in the data — so that route makes planet bombardment depend on an
  unbuilt system (P2.6/P2.7/R1.14) rather than on `Terrain`, which already fits. The EW treatment
  differs and a merged object must be wrong for one case: P2.52 gives a target on a planet two
  points of ground-clutter ECM, while P2.524 says planetary BASES do not get it. And the damage
  models differ — a base dies on its own track, a planet accumulates per face toward a threshold.
  Unify at the ORDER instead: the player picks a hex side and fires, and what absorbs it is the
  server's business. When ground bases are built they stand beside the planet as a second kind of
  ground target rather than the planet pretending to be one.

  **What it touches.** A seeker's target is a `Unit` and a planet is a `Marker`, so the field
  widens — see the lock-on note about `Set<Unit>` becoming `Set<Marker>`. Then the launch path,
  the DTO, and the UI. The victory half is a separate gap already recorded: `VictoryConditions`
  cannot express "300 points of damage on this planet", which is what a Planet Crusher scenario
  needs to be winnable.

  **Scatter-packs lean on this too (F4.4), and none of it is built.** A ballistic SP releases its
  submunitions when the first ACCEPTABLE target comes within a pre-set release range (F4.41,
  acceptability per FD7.3) — today `ScatterPack.isReadyToRelease` fires on a flat eight-impulse
  timer, so the tactic does not exist. The target hex itself can never provoke release (F4.413):
  something valid has to arrive. The SP cannot guide its own submunitions, so another unit must
  take them at the instant of release or they go inert (F4.42/FD1.7) — that handoff is the real
  cost of the trick. Any friendly SHIP, not a shuttle or PF, may take a ballistic SP at release
  (F4.422). And a ballistic SP can be ordered to reach its hex and STOP rather than carry on
  (F4.43), which is the loiter.
- **EM C10.514** — a ship using erratic maneuvers may not lay or sweep a mine. `Game.placeTBomb`
  and `Game.dropMine` both exist, so this is a guard rather than new machinery. (C10.512, cannot
  guide seekers, is real work by comparison.)
- **G24.23, attracting drones** — then G24.1343, phaser downfire to avoid blinding.
- **G21.1/G21.2 crew quality adjustments.** The declaration is built and `crewQuality` is on
  `ShipSpec`; the adjustment lists are not. The ±3 natural EW is the first slice.

## Built, never seen in a browser

Complete in core, server and web, and never played through. Each wants a two-browser session
(Chrome + Edge) more than it wants code.

- **Aegis fire control (D13)** — complete all tiers 2026-10-07.
- **The fire declaration round** — all three phases plus the multi-ship EW fix.
- **Hangar Ops and the COI screen** — unverified since `ea8e82d` changed the DTO to send
  catalogue keys plus `designation`.

## On the wire but unshown

The cheapest category in the project, and the one that keeps producing real bugs. The fact is
already in the DTO; only the display is missing. Check this shape first whenever something looks
absent from the UI — the DAC arming display turned out to be exactly this.

- **Launch buttons are not disabled under EM.** `usingEm` is on the ship DTO
  (`GameStateDto.java:524`). Today a player clicks launch and gets the server's refusal back.
- ~~**No standalone "call declaration" button."**~~ **FALSE, and was when it was written.** The
  Fire Orders pad has had the button all along, wired to `ensureDeclarationOpen`, which requires
  no drafted orders — so the bluff has always been available. The second half was wrong for the
  same reason: `PASS_FIRE_DECLARATION` counts toward the commit total exactly as a sealed order
  does, so a round nobody fires in still resolves and still sets `fireDeclarationSpent`. D13.142's
  aegis skip — give up firing 1, take only the extras — has therefore always been reachable by
  calling a declaration and passing it.

  Left here as a correction rather than deleted, because this is the third claim in two days that
  something was missing when it was not (plasma fast-load, erratic maneuvers, and now this), and
  the pattern is worth seeing. `GameSessionFireDeclarationTest.aDeclarationNobodyFiresInStillResolves`
  now pins the contract, which is a real one: the aegis pads and the declaration round do not
  otherwise touch, and nothing was holding them together.

- **The deployment map draws faction circles, not counters.** `DeploymentPanel` hand-builds
  minimal ship objects before a real `Ship` exists, so `tokenArt` is absent and every ship
  changes appearance the moment the battle starts. Fixing it means `LobbyStateDto.ShipDto`
  carrying `tokenArt` resolved through `ShipLibrary` at broadcast time — small, but it is the
  lobby DTO reaching into the ship library for the first time. Parked by the owner 2026-10-09.

## Module R3 ships not yet entered

Surveyed 2026-10-11 against the R3 SSD book's contents list: **32 of its 81 ships are missing**,
and they are not one job. Page numbers are the PRINTED page; add 1 for the PDF page.

**Ready to enter — nothing new needed (17).** Klingon D7E survey cruiser p8, D7V strike carrier p9,
D6E survey cruiser p11, D5F anti-fighter cruiser p15, D5I ISF cruiser p17, D5N diplomatic cruiser
p19, RKL Sparrowhawk p22, F6 battle frigate p23, E5 battle escort p24, C8S space control ship p25,
AD6 heavy escort cruiser p30, E3D drone escort p34; Hydran SR Outrider p36 and LP Lord Paladin p56;
Lyran SCS Siberian Lion p62, SR Prairie Cat p65, MP military police p77. The first four Klingon
ones have token art already drawn and parked in `TokenArtClaimedTest`.

Worth doing D5N early for its own sake: it is one of the two Klingon hulls that DOES have a flag
bridge (G6.12's exception, with the D7N), so it is the live test of the Annex #7E conversion.

**Blocked on a weapon system (4).** MD5 Klingon war cruiser mauler p29 and STJ Lyran mauler p75
need E8.0 maulers — a case-insensitive grep of the whole main source tree returns nothing, and
D23.0 shock is parked behind the same rule. C9A stasis dreadnought p26 and C7A heavy stasis
battlecruiser p28 need G16.0 stasis field generators; `StasisFieldGenerator` exists ONLY as a
string in `DacPriority`, there is no weapon class, and SFGs are deferred "very late or never".

**Blocked on M8.0 minesweeping (4).** Klingon D5M p18, Lyran CWM p60 and DWM p74, Hydran NMS p40.
Nothing implements minesweeping — the only mention in the whole engine is a comment in
`ShuttleCatalog`. G33.45 says a ship qualifies as a minesweeper by carrying minesweeping shuttles,
and those are among the shuttle types missing from the data.

**Blocked on G14.0 tugs and pods (5).** Klingon D5H light tactical transport p16, Hydran LTT Mule
p42, Lyran LTT p66, LTV light carrier transport p76, SRC Prairie Lion survey carrier p80. No Pod
class exists. The book also carries pallets and pods in their own right (Lyran carrier/repair
pallets p67, Klingon pods in Lyran service pp69-70) which are not ships at all.

**A whole empire (2).** WYN ODR double raider p81 and KE4 frigate p82. There is no `data/factions/wyn`
at all, and the WYN fly captured and mixed-technology hulls, so this is a faction decision rather
than two files.

## Maulers (E8.0) — not started, but the battery design is SETTLED

Read in full 2026-10-12, with the Advanced Missions SSD book now in `data/ssd/` for the D6M.
**Maulers and shock arrive together**: D23.11 makes every mauler ship subject to D23.0, so neither
ships alone. Shock itself is already scoped — see the shock memory and Annex #7S.

**A mauler is barely a weapon in our sense.** E8.11: no box on the SSD, and it "cannot be destroyed
unless the ship is destroyed". It is never armed; it is an energy dump resolved at the instant of
firing, damage being energy scaled by range (E8.21/E8.22 — at range 4, one point per power). Once
per impulse (E8.12), auto-hitting unless EW applies, where E8.24 treats it as a 2–12 two-dice weapon
for die-roll shifts only. Our `Weapon` class assumes boxes, arming and cooldowns, and a mauler has
none of the three, so expect it to sit awkwardly in that hierarchy.

### DECIDED: one battery pool, not battery groups (owner, 2026-10-12)

E8.31-E8.323 divide a mauler ship's batteries into numbered GROUPS, each separately recorded,
discharged whole, fractions ignored. **We are not implementing groups.** The owner's reasoning, and
it is sound: in play the weapon is almost always fired by dumping everything, and the rule's own
worked example (E8.4) has a player juggling a full group of ten, a group of ten holding four, and a
single battery just to reach fifteen. Partially-charged small groups are bookkeeping noise. We also
hold batteries as whole points already, so E8.321's half-point-does-one-damage clause cannot bite.

**Instead: a single pool, and the player names how many points to spend.**

**But keep E8.323's rate limit, in pool form.** The group rule bars a group from feeding the mauler
twice within a quarter turn REGARDLESS of recharging — so across any quarter turn a ship can manage
exactly one pass through all its groups, which is its full battery capacity. The faithful
translation is therefore not an approximation but the same aggregate:

> In any rolling quarter-turn window, total battery power discharged into the mauler may not exceed
> the ship's battery capacity.

Without it, a single pool plus per-turn recharging lets a mauler fire appreciably more often than
the rules allow, which on a weapon this powerful is the one place the simplification would change
outcomes. The mechanism already exists: it is the rolling quarter-turn window built for the gatling
(E2.151, `Weapon.shotsInQuarterTurnEnding`), counting energy instead of shots.

### The rest of E8.3 is unaffected and still applies

Engine and reactor power may be coupled to the mauler, must be pre-allocated on the EAF (E8.331),
must all discharge on one impulse (E8.333) — and **the supplying system takes damage equal to half
the power supplied, rounded up** (E8.332). Power allocated and not used is simply lost, with no
damage (E8.334). That self-damage is the character of the ship and is not a candidate for
simplifying away.

### E8.22 damage chart (owner, 2026-10-12)

| range | damage scored |
|---|---|
| 0-1  | **double** the energy discharged |
| 2-5  | the energy discharged |
| 6-10 | **half** the energy discharged, fractions rounded DOWN |

Three bands and no die roll — E8.22 says the weapon simply hits, and E8.24's 2-12 exists only so
that EW has something to shift. Cross-checks against the rulebook's own worked example (E8.4), where
a Romulan scores fifteen damage from fifteen power at range four, inside the 1x band.

Two things follow. **Ten is the maximum range**, since the chart stops there. And the weapon is
brutal up close: at range 0-1 every battery point is two damage, which with a pool of batteries plus
coupled engine power is a very large single shot — worth remembering when the window rule above
looks over-cautious. Note E8.25 resolves range 0 as range 1 and takes no feedback damage, and that
the target must be genuinely in front.

### Before building

Suggested slicing: the battery/energy work first (it touches allocation,
which is core and server-validated), then the mauler, then shock, then the ships — D6M, MD5 (R3.75),
STJ (R11.43), the Romulan mauler hulls, and with shock the KillerHawk at Annex #7S's lowest rating
of 10.

## Ground bases (R1.14) and defense satellites (R1.15) — scoped and AGREED 2026-10-12

The Advanced Missions SSD book supplies what the ballistic-targeting entry below said we lacked:
that entry rejected giving planets a default "base" partly because "we have NO ground bases at all
— no concept in code, nothing in the data", naming R1.14 as the unbuilt system. The data half now
exists, pages 9-16 (printed; add 1 for the PDF page).

**Two different things, and they should be separate slices.**

**R1.14 GROUND BASES** sit on a planet's surface and share ONE chassis across every weapon fit: a
shield, two hull boxes, a bridge, APR, sensor 6|0, scanner 0|9, dam-con 4|2|0, five crew units, two
boarding parties, DK 4, EX 0, CR 0. Only the weapons and the BPV change — `GBD1` phaser-1 at 8,
`GBDP` phaser-4 at 14, `GBFB` fusion at 8, plus disruptor, plasma, plasma-F, photon and Hydran
fits. Energy is simpler than a ship's: shields ½+½, **life support 0, fire control 0**. Every weapon
they mount already exists, Phaser4 included.

**R1.15 DEFENSE SATELLITES** are a different shape: `DEFSAT`, size class 7, point value 20, sited in
high or low orbit, ALL weapons 360°, and a damage-point track in place of shields and hull. Drone
racks cannot be reloaded during a scenario and carry only type-I drones at the historically current
speed. Defer these; they are not what planetary bombardment needs.

### The one real change to existing code: SINGLE-SHIELD MODE

P2.73: small and medium ground bases have "a single shield (covering all arcs)". `Shields` hardcodes
six slots and `getShieldNumber` picks one by bearing, so six equal shields — the obvious shortcut —
would silently give a ground base SIX TIMES its real protection, since each facing absorbs
separately.

**Build it as a general property of the unit, not a ground-base special case** (owner, 2026-10-12:
"Single-shield mode may come in useful for other units in the future"). A flag that collapses every
bearing to one shield, so anything with 360° protection gets it without knowing about planets. This
is also the wiring-to-the-general-mechanism rule applied before the fact rather than after.

Note the larger ground bases are NOT this case: P2.731 gives a base with two non-planet hex sides
two shields at 3x strength, and one with three such sides three shields at 2x — the planet itself
covering the rest. So the flag wants to express "N shields covering all arcs between them", with 1
as the common case, rather than a boolean.

### The rest

Siting on a planet hex with no movement, and the EW asymmetry already identified: P2.52 gives a
target ON a planet two points of ground-clutter ECM while **P2.524 denies it to planetary bases** —
trivial while they are separate objects, which is why merging them was rejected. Then P2.713's
four-hex ballistic limit wires the new target into the seeker work already done.

**Agreed slicing:** single-shield mode, then the chassis plus one fit (GBD1), targetable by direct
fire; then the remaining fits as data; then ballistic ground attack; satellites last or never. That
completes two of the three ground targets the ballistic entry lists, and gives planetary bombardment
a defender that shoots back.

## Unstarted systems

Roughly largest first. Several have had their rules read and their design settled; those say so.

- **Turn-boundary save and reload.** Analysed, not started. A versioned snapshot taken at a clean
  energy-allocation boundary, preserving complete mutable state and the resolved ship-spec
  fingerprints below. The prerequisite for the campaign system.

  **Seeded dice are NOT a prerequisite for it**, and this file said they were until 2026-10-09.
  A snapshot does not re-derive anything: every roll before the save has already become ordinary
  stored state — a shield box is down, a lock-on is held — and the rolls after it need not match
  a timeline that never happened. What genuinely needs reproducible randomness is REPLAY, which
  is a different feature, and deterministic tests, which are their own reward (`DiceRoller` uses
  `Math.random()`, so a statistical test can pass by luck). Worth doing early on its own merits;
  just not a gate on this. Caught by an outside reviewer, and the error was in the compression —
  the underlying design note had the distinction right all along.

  **The refit model makes this easier and adds one hazard.** A ship can be reconstructed from
  `base hull + applied refits + scenario year + COI/loadout + mutable battle state`, which is far
  less to store than a whole ship. But that reconstruction reads the ship JSON *at load time*, and
  this repo edits ship JSON constantly — three new base files landed on 2026-10-09 alone. Edit a
  hull or a refit between sessions and a restored ship silently becomes a different ship, in a
  saved campaign, with nothing to say so.

  So **store a fingerprint of the resolved `ShipSpec` alongside the reference** and compare it on
  load: same hash, reconstruct; different hash, say which ship changed and let the player decide.
  Raised by an outside reviewer 2026-10-09 and the best thing in that review. The state inventory
  it must cover has also grown since the feature was first scoped — base hull plus refit set,
  scenario year and its automatic upgrades, fighter squadrons and individual loadouts, deck-crew
  assignments and unfinished hangar work, carrier drone and plasma stores, aegis firing state,
  bases, option mounts and commander's options.
- **The campaign system.** Owner's direction, not designed. Would lean on drone storage, crew
  quality and carriers. Rule pointers: G21.3 → U7.9/U2.0, G22 legendary officers → U7.8/U1.26.
- **The scenario system** — year, forces, BPV budget, loadouts, victory conditions. Deferred;
  ask for PDF scenario examples when starting. SH47's training subset is the best first target.
- **Fleet builder UI, terrain and deployment.** The S8.0 validator is done (12 rules, core plus
  endpoint); the rest of the screen is not.
- **ESG (G23).** A six-slice roadmap exists. Slice 1 is the ring that damages what enters it. Note
  it is a moving hollow damage-ring, not an expanding sphere. (G23.24 capacitors and G23.245's
  pricing are built.)
- **Tugs and pods (G14).** Rules now read. Decided: a pod is an attachable **unit**, never a
  tug+pod combination file — G14.34's mid-turn cost change, G14.353 independent status and
  G14.6's pseudo-pod each defeat that. Ten tug hulls are already in the data. The blocker is that
  `moveCost` and `sizeClass` are static JSON.
- **The Fire Orders pad** — replacing map-clicking with a written order pad. Five-slice plan,
  with a privacy constraint on seeker grouping.
- **Drone speed availability and cost (FD10.651).** Each speed phases Limited → Restricted →
  General. Must be kept **out** of the COI basis when built: S3.211 excludes mandatory upgrades
  from the budget they would otherwise inflate. Separately, special drones (ECM, armour, ATG,
  swordfish) are a missing **dimension**, not a missing picker option — `DroneType` is exactly two
  axes and the rules compose frame + speed + one module per space.
- **Chaff.** Core is correct and the D11.32 filter is built, but it is unreachable: no `chaffPacks`
  in any ship file and no web surface. The UI cannot be gated on "something is chasing you" —
  G4.231 makes a seeker's target secret. Design settled.
- **Phaser downfire (E2.25).** Read in full. `shotModes` and the `lastShotUnderAegis` idiom
  already exist, so it is cheaper than it looks. The UI should be a quiet defaulted opt-in, not a
  mode picker.

## Structural, carried opportunistically

An outside review on 2026-10-09 measured the five largest files: `Game.java` 4,715,
`GameBoard.tsx` 4,885, `GameSession.java` 3,237, `GameStateDto.java` 2,697,
`GameController.java` 2,265. Its conclusion — that complexity is outrunning decomposition — is
half right, and the half that is wrong matters: this repo runs 30–40% comment by line, so
`Game.java` is about 2,894 lines of actual code, and three new collaborators have appeared since
the architecture notes were written. The pressure is real but slower than the raw numbers say.

**Do not stop building rules to refactor.** Keep extracting while touching a domain, as the
resolver pattern already does, and extend the same habit to the other tiers:

- **Server**: split lobby/setup, the fleet catalogue, game actions and option-query endpoints out
  of `GameController`.
- **Session**: extract simultaneous-fire coordination, deployment and pregame state.
- **DTO**: per-object assemblers, keeping the wire format exactly as it is.
- **Frontend**: `GameBoard` by interaction domain. **This one is not like the others** — 4,885
  lines with no tests on any interactive path, and it is the screen the whole game is played
  through. Characterisation tests first, or don't start.

## Smaller rules gaps

- **P3.23 following, P3.4 large asteroids, P3.252.**
- **MRS, MLS, MSS and SWAC shuttles do not exist in the data.** `data/shuttles/common.json`
  mentions them only in its documentation comments. The role-eligibility model was built for
  exactly these, so adding them should be mostly a JSON edit. MRS (J8.0) is separately deferred
  by the owner, and the J8.11 spec table is unreadable in the PDF — it needs a hard copy.
- **Photon arming (E4.0) slices 2–5.** Slice 1 is built.
- **Fractional batteries.** Batteries hold whole points, but transporters (0.2/use) and movement
  costs are fractional.
- **E1.7 small target modifiers.**
- **The type-G rack's third reload set never materialises.** Found 2026-10-12 while entering the
  AD6, whose sheet says "RACKS ALWAYS HAD THREE RELOADS. THIRD RELOAD IS ENTIRELY ADDS." A type-G
  carries two by default and FD3.72's extra set is expressed as `y175Upgrades.racks[].extraReloads`,
  which the AD5 and AF5 have carried for some time — and all three ships report **two** reloads
  after `outfitForYear(.., 176, ..)`, so this is pre-existing and not about the new hull.

  The diagnosis, as far as it got: `DroneRack.addReloadSets(n)` raises `numberOfReloads` AND
  appends a set to `reloads`, but only `if (!set.isEmpty())` — it copies the rack's CURRENT
  `ammoList`, so an unloaded rack appends nothing. `getNumberOfReloads()` then answers
  `reloads.isEmpty() ? numberOfReloads : reloads.size()`, and with two base sets loaded it reports
  the list's length and the raised counter is never seen. Either the sets must be materialised from
  the rack's nominal load rather than its current contents, or the getter must prefer the counter.
  Both have consequences for ships that legitimately carry partial reloads, so read FD3.70/FD3.72
  before choosing.

  Separately and smaller: "third reload is entirely ADDs" is not expressible at all — a reload set
  does not record what is in it.
- **G6.0 security stations and Klingon mutiny.** Raised by the owner 2026-10-11 as "not sure it's
  worth the bother". Read in full; here is what it actually costs.

  **G6.12 is DONE** (2026-10-11), and it had to come first or the rest was unreachable. A flag
  hit now converts to a security station per Annex #7E's damage conversion chart; before that,
  every Klingon hull in the game carried security stations that no amount of damage could touch.

  **Then the mutiny itself (G6.2/G6.3)**, which is cheap: when the last security station is
  destroyed or captured, roll a die immediately and once per turn after. A "1" means a mutiny; roll
  again, 1-3 put down, 4-6 successful. Put down once (G6.24) and no further rolls are made. A
  successful mutiny means the ship cannot move or fire (G6.32) — the non-Klingons aboard were never
  taught how. Modifiers: G6.23 all control spaces destroyed, and G6.22 enemy boarding parties
  aboard, scaled by size class (every 4th, or 2nd on an SC4, 6th on an SC2, 10th on a starbase;
  PFs cannot mutiny at all). G11.25: no mutiny on a computer-controlled ship.

  **It would be the first built consumer of the G21.1 crew-quality lists**, which are declared but
  unimplemented — G6.224 points at G21.144, where a POOR crew mutinies on "1" or "2" and puts it
  down only on "1" or "2".

  **For PENAL ships none of that applies: R3.R52 supersedes G21.144 outright** (text supplied by
  the owner 2026-10-11, from Module R3's rules). Mutiny is AUTOMATIC once all security stations are
  destroyed or captured — no occurrence roll, no put-down roll. Then:
    - roll 2d6: that many boarding parties of mutineers are converted immediately from crew units;
    - the boom crew (a QUARTER of the total) and every original boarding party stay loyal;
    - boom separation, if the boom is still viable (G12.12), succeeds on 1-5;
    - **the ship can still move and fire**, which is the opposite of G6.32 — "as some of the
      technically qualified personnel would be part of any successful mutiny". It may only fire at
      a unit that fired at it, a seeking weapon within three hexes, a unit holding it in a tractor,
      or any unit within three hexes;
    - the NON-KLINGON player then takes control, and rolls one die: 1-3 it tries to disengage to a
      neutral or non-Klingon empire, 4-6 it tries to surrender to the forces it was fighting.
      Against a monster (SM0.0) it always disengages.
  So the penal case is not a simplification of the generic rule — it is a different rule that needs
  the enemy to take over a ship, which nothing here does yet.

  **R3.R53 booms**, for whenever G12.0 separation arrives: a penal boom is not a standard one. The
  second box on the sensor/scanner tracks is marked out (not the first) and every second box after,
  except the last. J-boom shields are D6J 13, D5J 10, F5J 9, E4J 7. The small warp engines under
  the boom are "emergency warp" (H2.5) and may be used for POWER but not MOVEMENT while the boom is
  attached — which is what `centerWarp` on our penal hulls represents. Nothing stops it being spent
  on movement, and that is now a deliberate simplification; see "Deferred on purpose". Supersedes G21.125 and D21.56. The E4J is the exception throughout:
  it has no warp-powered boom, so D21.543's improved catastrophic-damage escape does not apply.

  **Defer the deep end.** G6.4 (boom control, self-destruct, separation) and G6.5 (capturing a
  mutinous ship) need G12.0 boom separation, which does not exist here at all; G6.25's "let it
  succeed but keep the boom" needs the same. A first slice can stop at "the ship is dead in space
  and may surrender" and lose little.

  **Worth it for the penal ships specifically.** Without G6.0 a D6J is a D6 with more security
  boxes and a lower BPV — strictly less interesting than the hull it came from. The mutiny rule is
  what the ship type is FOR, and two more penal hulls (D5J, E4J) are queued behind it.
- **Shield boundary bearings** (3, 7, 11, 15, 19, 23) have split-shield rules not implemented.
- **E7.54** — the fusion holding size-class/PF limit.
- **D6.6 passive fire control**: seven clauses unimplemented, including D6.622 ECCM, D6.623 barred
  systems, D6.627 scout lending and D6.66's sensor-rating roll. D6.7 low power is deferred with
  its design settled.
- **Orion option mounts do not move the COI budget** inside `ScenarioLoader.applyCoi` — mounts are
  equipped after the budget is struck there. Documented, not fixed.
- **FD10.647**: extra COI drones carry no reloads and must hold the special-to-standard ratio
  themselves. Not started, deferred by the owner.
- **G24.217** — lending a scout channel to a fighter; and ECM drones. The scout channels
  themselves are built at every tier.

## Deferred on purpose

- **J-series emergency warp is treated as ordinary power (R3.53).** Owner's decision 2026-10-11:
  "kinda fiddly and an edge case that introduces more complexity than justifies the payoff", and
  the cost estimate agrees — `getTotalAvailablePower()` sums lwarp + rwarp + cwarp + impulse + apr
  into ONE number and allocation spends from that pool, so "these two points may fund anything but
  movement" means threading a per-source constraint through allocation.

  What the rule says: the small warp engines under a penal boom are "emergency warp" (H2.5) and
  "can be used for power, but not movement, while the boom is attached". Note that is not a rare
  state — the boom is attached in every game we can play, since G12.0 separation is unbuilt. So a
  penal hull permanently gets about 2 extra points of movement power it should not have, which at
  move cost 1.0 is roughly 2 speed on a 98-BPV ship.

  **Do not "fix" this by deleting `centerWarp` from the penal hulls.** The cwarp boxes are a
  damageable system in their own right — the DAC has a `cwarp` entry — so removing them would cost
  the ship two internal-damage absorbers and change how it dies, which is a worse and far less
  visible error than two points of speed. If this is ever revisited, the fix is a movement-eligible
  subset of the power pool, not a change to the ship data.


Not backlog so much as decisions. Listed so nobody re-opens them by accident.

- **Stasis field generators (G16.0)** — deferred to very late or never. Not the stasis *boxes* we
  already model. Reserves the Klingon D5A name, which is why the full-aegis D5 escort is the AD5.
- **MRS shuttles (J8.0)** — digested in full, then deferred. An MRS is not a `Fighter` (J8.432).
- **Drone drop selection** — which drones lose tracking when the control limit is exceeded.
  Currently first-in-first-served.
- **Token art for non-ship markers** — seekers, planets, mines, terrain. Ships are done, and so
  are CRAFT as of 2026-10-10 (a catalogue row names its counter, the same way a hull does).

- **A Q-ship's counter is a disguise, not decoration.** Raised by the owner 2026-10-10 and worth
  writing down before it is built the wrong way. A Q-ship's whole purpose in some scenarios is to
  look like an ordinary freighter until it opens fire, so drawing it as a Q-ship gives the ambush
  away before it happens — the picture would leak what the scenario exists to conceal.

  It is the same ruling already made for craft: **art follows what a unit APPEARS to be, never
  what it is.** A suicide shuttle draws as the admin shuttle it was built from because G4.233
  says so; a Q-ship should draw as a freighter until it reveals itself. So the work is not "a
  generic Q-ship marker" but a reveal state on the hull, with the counter following it — and
  until that exists, a Q-ship is better off with no art than with its own portrait. Hydran S-Q
  currently names `hydran/sq.png`, which does not exist, so it falls back to a circle and is
  accidentally correct.
- **Photon warp arming** — photons may only arm from warp energy. Owner unsure it is worth it.
- **Early Years (Y-prefix) content** is out of scope entirely. The Romulan WB and warp-targeted
  lasers are absent by decision, not by omission.
- **G13.361, EW-dependent cloak re-rolls, hidden movement.** Shelved with the rest of G13 complete.
- **Per-squadron vs per-carrier EW allowance**: J4.463's per-squadron cap *is* enforced and its
  per-carrier allowance deliberately is **not**. Do not wire up `allowedEwFighters`.
