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
- **G6.0 security stations and Klingon mutiny.** Raised by the owner 2026-10-11 as "not sure it's
  worth the bother". Read in full; here is what it actually costs.

  It is TWO rules. **G6.12 must come first or the rest is unreachable**: "Hits designated by the
  DAMAGE ALLOCATION CHART as 'flag bridge' hits will be scored against the security stations of a
  Klingon ship. For this reason, no Klingon ship has a 'flag bridge'." Today a `flag` hit calls
  `controlSpaces.damageFlag()`, which returns false on a hull with no flag boxes, so the DAC
  advances and the damage lands somewhere else. Every Klingon hull therefore has security stations
  that CANNOT be damaged — so a mutiny rule built on top would never fire in play. G6.12 is small
  and worth doing on its own merits; the exception is the diplomatic ships (D7N, D5N), which do
  have flag bridges.

  **Then the mutiny itself (G6.2/G6.3)**, which is cheap: when the last security station is
  destroyed or captured, roll a die immediately and once per turn after. A "1" means a mutiny; roll
  again, 1-3 put down, 4-6 successful. Put down once (G6.24) and no further rolls are made. A
  successful mutiny means the ship cannot move or fire (G6.32) — the non-Klingons aboard were never
  taught how. Modifiers: G6.23 all control spaces destroyed, and G6.22 enemy boarding parties
  aboard, scaled by size class (every 4th, or 2nd on an SC4, 6th on an SC2, 10th on a starbase;
  PFs cannot mutiny at all). G11.25: no mutiny on a computer-controlled ship.

  **It would be the first built consumer of the G21.1 crew-quality lists**, which are declared but
  unimplemented — G6.224 points at G21.144, where a POOR crew mutinies on "1" or "2" and puts it
  down only on "1" or "2". That is the penal connection: penal ships are poor-crewed, and the owner
  reports mutiny is automatic for them (that clause is in R3.36's ship description, which we do not
  have — we hold the R3 SSD book, not its rules text).

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
