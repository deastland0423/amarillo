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
- **A seeker cannot target a hex** (P2.311). Direct-fire hex targeting is built and routes to
  `bombardPlanet`, so the damage already has a destination; the work is letting a seeker carry a
  hex. A seeker's target is a `Unit` and terrain is a `Marker`, so this reaches the launch path,
  lock-on (`Set<Unit>`), the DTO and the UI.
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
- **No standalone "call declaration" button**, so the bluff cannot be made on its own —
  and it now blocks a second thing. Aegis pulse pads wait on `fireDeclarationSpent`
  (D13.141: firing 1 is the sealed volley and resolves first), but that flag only turns
  true when a round actually resolves. An impulse where you skip firing 1 under D13.142
  and want only the extras has nothing to resolve, so the pads never appear. Both need the
  same thing: a way to say "I am declaring nothing".
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
