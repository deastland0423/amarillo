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

## Orion ship data — MODULE R2 IS ENTERED BAR ONE (2026-10-20)

`data/ssd/Module_R2_SSD.pdf` arrived on 2026-10-20. Its Orion section runs printed pages 67-76
(PDF 68-77), and **nine of the ten hulls are in**: DBR 67, OK6 68, BCH 69, DW 70, HR 71, MR 72,
AR 73, LRS 74, DWS 75.

**Only the DBP (R8.25, p.76) is left, and it is a PF TENDER.** PFs (K1.x) are not modelled at
all, so it waits on that whole system rather than on a reading. The chart gives it 110 BPV and
6 boarding parties where the DBR has 12.

### What reading these taught, for the next book

- **A silent sheet describes the NORMAL case** (owner, 2026-10-20). The DBR prints "MOUNTS A, B,
  AND C ARE NOT CENTERLINE ADJACENT" because it is the exception; the BR prints "IT IS NOT
  NIMBLE" where the DBR prints "NIMBLE SHIP". So absence means mounts ARE adjacent and the ship
  is NOT nimble.
- **Read any apparent exception at full resolution before believing it.** The MR's FD4.4 note
  looks like "a maximum of FOUR drones per turn" at thumbnail size, which would have made it the
  only exception in the fleet to the three-rack cap. At 400 dpi it plainly reads THREE. An
  apparent exception is the most interesting thing on a page and so the easiest to believe.
- **Position and arc are different axes.** The DW's three option mounts all carry FA arcs, and
  only its printed note ("MOUNTS B AND C ARE WING MOUNTS") says two of them are wing mounts.
  Counting FA labels would have made an 80-point destroyer the best multi-mount platform in the
  fleet.
- **Tracks are per hull and must not be padded to a house shape.** Four steps on the DW, MR and
  DWS; five on the HR and SLV; six on the AR and BR; seven on the BCH.
- **A variant can be checked against the hull it converts.** The LRS is the LR and the DWS is
  the DW, each with special sensors in the wing mounts; the OK6 is a Klingon D6 forward of its
  boom. In all three cases `klingon/d6.json`, `orion/lr.json` and `orion/dw.json` confirmed the
  numbers the yard left alone — the best verification available, and worth seeking out.
- **Sensors take the hits of what they replaced** (G24.17), so `dacHitLocation` is per weapon.
  The column name is `torp`, never `torpedo`; `DacHitLocationVocabularyTest` now enforces that.

Reading one hull takes about one exchange with the owner, for the handful of things a render
cannot settle: a line code, a ruling on silence, and occasionally a box whose fill is ambiguous.

## Orion ship data — the Basic Set and Advanced Missions hulls are ENTERED (2026-10-20)

All six Advanced Missions hulls (PDF 140-145, printed 139-144) plus the CR from the Basic Set
book (PDF 49, printed 47) are in `data/factions/orion/`. **Reading rules, settled while doing
it** — point value prints **EPV/BPV** for a converted trader and a single number for a warship; a
**stippled** box is a PLUS-refit box and the sheet says so in a legend with a swatch, so a sheet
with neither has no plus refit and every box counts; the purple tints are a counting aid in
groups of five and mean nothing; a lone `L` or `R` beside a phaser pair **is an arc component**,
but only where the sheet prints one — the BR prints them and the CR does not, so the BR's rear
Ph-3 are `RA+L`/`RA+R` and the CR's are a bare `RA`; `OPT-FA` or `FA OPT` is a centerline mount
and a bare `OPT` by a wing is a wing mount; breakdown is the LOWEST breaking roll ("4-6" = 4);
"NO HET BONUS" means `bonusHets: 0`. Render with `pdftoppm.exe -f N -l N -r 150 -png` for layout
and `-r 400 -x -y -W -H` crops to count boxes; 600 dpi settles an awkward one.

**What is left here:**

- **The MINELAYER REFIT (R8.R1)**, cited on both the BR's and the CR's sheets and entered on
  neither. Its text is in neither the Master Rulebook nor either SSD book's text layer, so it
  needs the owner or a module. Two things the Master Rulebook does say about it: its racks go in
  the **shuttle bays** and are limited to **one mine per turn per BAY, not per box** (M9.21), and
  a ship given mine racks this way is **not thereby a minesweeper** (M9.16) — a real distinction
  now that `MineRole` exists.
- **Does FD4.4's three-rack cap bind the Orion BASE STATION?** The rule says "Orion *ships*", and
  `bs.json` is the one Orion with option mounts that is not a ship, so it is deliberately left
  without `rackFiringLimit` rather than given one on the strength of a note. Every Orion **ship**
  now carries it.
- **The UIM half of the disruptor note is still open.** The same Annex #8B rows that grant
  DERFACS also say "UIM from Y166 (5 pts)" for range-22-and-up. That one is a PURCHASABLE
  add-on, not a grant, so it is a different shape of job from the DERFACS rule built on
  2026-10-20 — it needs a place in the COI spend, not a year pass.
- **A DERFACS ship's fire preview is wrong, in the web tier.** Core resolves disruptor fire on
  the DERFACS hit chart (`DamageResolver`), but `hasDerfacs` never reaches the DTO and
  `weaponDamageTables.ts` holds only `DISRUPTOR_HIT_CHART`, so the client shows the standard
  numbers. This is NOT new — every Klingon D7 with its DERFACS refit, and the Orion and Kzinti
  bases, have always previewed wrongly — but Orions can now reach it, so it is likelier to be
  seen. It is the same failure the comment beside it records for ECM: "a player was shown one
  number and shot with another." Needs a DTO field, a second chart, and the wiring between.
- **The annex's "Plasma-D Rack" row carries no `weaponSpec`**, so it cannot be equipped and the
  validator refuses it with "no weapon implementation". That was true when the catalogue was
  written and is not any more — `WeaponFactory` has built `PlasmaRack` (FP10.0) since, and
  FP10.15 covers the Orion ones. Filling the row in is one line, and until it is, no Orion can
  actually buy the plasma rack its own SSD talks about.
- **The Y170 plasma carronade and the sabot refit** (Module R10) are out of scope by the owner's
  call, 2026-10-20. Noted on both ship files and left unmodelled.

## Plasma rack (FP10.0) — reloading is NOT built (found 2026-10-20)

Firing is complete across all three tiers and has been since 2026-10-03. **Reloading is not
built at all**, and nothing in the code says so, because every piece of state for it exists:
`reloadSets` is tracked, exported to the DTO, doubled by the Y175 refit and saved across a game
— and never consumed. `torpedoes` only ever decrements, at the two firing sites. Nobody outside
the class calls `setReloadingThisTurn(true)`. The server's reload path (`stagePendingReload`,
`ReloadStockpile`) is `DroneRack`-only. So a rack fires its four torpedoes and is dead weight
for the rest of the game while the UI reports reload sets that cannot be spent.

**BUILT END TO END (2026-10-20)**: `PlasmaReloadStore` is the ship's pool, `stagePendingReload`
plots a reload under FD2.421 (two torpedoes a turn, rack out of action, rate cannot be
increased), and `cleanUp` lands it at 8C exactly as `DroneRack` does. Saved and restored,
plotted from the EA dialog's "Plasma Reloads" panel, and the DTO reports the hold once for the
ship rather than once per rack. What is left:

- **FP10.31 interchangeability is BUILT as a FALLBACK** (owner, 2026-10-20: "Pull from the
  normal place for each. Only when the normal resource is out, do you pull from the other one").
  Each store draws on its own first and reaches across only when dry, so nobody is asked to
  choose. What is NOT built is the rule's "at the player's option" — a player cannot choose to
  spare their own hold and draw from the other while both have stock. That needs a UI and an
  intent, and the fallback covers every case where it would change the outcome today.
- **FP10.311** — handled exactly as drones; see J4.825 and FD2.44, both built for drones
  already, which is where the reuse should come from.
- **FP10.33 UNLOADING** — a type-D can be unloaded, and an ACTIVATED torpedo switches itself off
  when unloaded, needing fresh activation energy on its next rack. Cheap, but easy to miss.
- **FP10.313** — some bases use a multiple-magazine version with no reloads at all, the reloads
  living in the extra magazines (FD3.87, FD3.46). Matters because the Orion BS has a plasma-rack
  refit.

Two smaller ones from the same read:

- **FP10.13 aegis steps are deferred**, documented honestly at `PlasmaRack.boltRefusal`. Note
  that FP10.22's "one means per impulse" only starts to bite once they exist.
- **FP10.243 is enforced but uncited.** `Ship.countsAgainstRackLimit` already counts a
  `PlasmaRack`, so FD4.4's three-rack cap covers plasma racks — exactly what FP10.243 says ("An
  Orion ship would require OAKDISC fire control to use more than three plasma racks"). The
  behaviour is right and no rule number marks it, which in this codebase is how a built rule
  comes to look unbuilt.

FP10.244 (PFs) is moot: there are no PFs in the tree.

## Orion option mounts and refits — BUILT 2026-10-20/21, what is left

Mounts and refits are now bought where the ship is bought (G15.415). See the memory note
`project_option_mount_shipyard` for the design and the two rules that catch people.

**Left:**
- **`ShipDto` does not expose the option MOUNTS** — the empty ones. This line used to say the
  SSD popup "cannot show an Orion's fitted weapons", and that was wrong: a fitted weapon is a
  weapon on the ship, so it reaches the panel like any other, captioned "Disruptor-30 OPT-A"
  and drawn in its own arc. `OrionOptionMountDtoTest` pins that, in a running game, after the
  claim went unchecked. What is genuinely missing is the mount a player left EMPTY: the SSD
  sheet prints an empty OPT box and the panel prints nothing at all. Adding it means
  classifying the field in `ShipDtoPrivacyTest`; the fitted weapon is already public, so hiding
  the mount it sits in would conceal nothing.
- **Partly looked at in a browser now (2026-10-21).** The owner confirmed the fleets landing
  page, the cartel dropdown, the refit chips and the roster. NOT yet seen: the mount panel
  itself — the cartel gate, the disabled pickers, the "second mount, chosen in A" line and the
  confirm that clears mounts when a cartel is withdrawn. `amarillo-web` has no jsdom or
  testing-library, so no component behaviour is reachable from the suite; those are verified by
  reading only.
- **The cartel gate is CLIENT-SIDE ONLY.** The pickers refuse to fit a mount without a declared
  cartel (G15.44: "which weapons are available depends on which cartel the ship is operating
  under"), but `FleetValidator` raises nothing for a fleet carrying fitted mounts and no cartel
  — see `withoutACartelNoQuotaIsEnforced`, which deliberately says a force that has not declared
  is not second-guessed. A hand-edited JSON or a direct API call still produces the state.
  Making it a real guarantee means flipping that test and possibly invalidating saved fleets.
- **G15.442 — DONE 2026-10-21.** `OrionCartel.home` is a `List<String>`; the Cluster carries
  four home empires and an empty operating zone, and `CartelQuota` did not change at all. The
  caps "operating <= 20%, outside <= 10%" already restate G15.441's 70/20/10, so an empty zone
  yields 90/10 out of the same arithmetic — the exception is a SHAPE, not a branch.
- **G15.445 — the technology lag is not implemented.** A cartel cannot use a system until two
  years after its home empire fields it, three after an operating-zone empire, five after anyone
  else. Options are gated on the weapon's own `yearAvailable` only, so an Orion can field a
  weapon the year it appears anywhere.
- ~~The PPD has no weapon class.~~ **BUILT 2026-10-22** — its Annex #8B row has a recipe, so an
  Orion can buy one into two adjacent centerline mounts. See the PPD section below.
- **G15.42's non-weapon options** — batteries, APR, labs, cargo, tractors, transporters, repair,
  hull, barracks — are listed in the catalogue with no `weaponSpec`, so they cannot be equipped.
  "None of the non-weapon options changes the BPV of the ship," so they need a different seat
  from a weapon, not just a spec.
- **FP10.31's "at the player's option"**: a player cannot choose to spare their own hold and
  draw from the other while both have stock. The fallback covers every case where it changes the
  outcome today.
- **Named Orion variants** would still be nice for the ones history named (a CA+ reads better
  than "CA with the Plus refit"), but nothing needs them now that unnamed combinations resolve.

## ISC rear-firing plasma (R13.1C) — BUILT 2026-10-22, one clause parked

Most ISC ships carry type-F torpedoes aimed astern "to protect the ship from flank attacks or
during retreats". They are not ordinary launchers pointed backwards, and `iscRearFiring` on the
launcher is the anchor for the whole clause — one flag, because the rule states each restriction
as a CONSEQUENCE of being rear-firing rather than as a property of its own.

**Built:** no PPTs (folded into `canLaunchPseudo`, so every caller inherits the refusal); only
ONE may fire at a size-class-4-or-larger ship per turn, tallied on the SHIP because the budget is
per ship and not per launcher, with planetary bases on the restricted branch whatever their size.
Fixed in direction three or five with no swivels needed no code: `ArcUtils` already derives a
single launch direction from `L+LR` and from `R+RR` (D2.35).

**Needs no code, and is NOT a gap.** "This defensive control system... cannot identify drones"
disambiguates the phrase "fire-control system" for a reader; no plasma launcher in this engine
identifies drones in the first place, so the clause is satisfied by absence (owner, 2026-10-22).
Written down because a clause satisfied by absence looks exactly like one nobody got to.

**Parked by the owner (2026-10-22) as a fiddly edge case:** any or all may fire at size-class-5
or smaller units in a turn, but not two at the same target on the same impulse. Only bites when
two rear torpedoes are thrown at one PF in a single impulse.

## PPD (E11.0) — BUILT 2026-10-22, what is left

The ISC's Plasmatic Pulsar Device. Built across all three tiers: a ship arms one in the
energy-allocation dialog and fires it through the sealed declaration like any other weapon.

**What it does now.** Arms over two turns at 2-4 points (two points buys one pulse, E11.25);
fires a run of up to four pulses over four impulses at one target it cannot change (E11.30);
rolls 2d6 for a wave-lock each pulse until one takes, after which the rest score automatically
(E11.331); spreads each pulse over two or three shields as ONE volley that resolves before all
other fire (E11.35, E11.332); needs active fire control and a lock-on throughout and holds a
seeking-weapon control channel while pulsing (E11.15, E11.151).

**Two things that catch people, both pinned by tests:**
- **The chart runs BACKWARDS.** Nothing inside range 4, strongest at 4-10, reaching 40. Every
  other direct-fire table in the game runs the other way.
- **Losing a wave-lock and BREAKING one are different.** E11.34 drops the lock and rolls again
  on the pulses that remain; E11.54 ends the run and forbids a new roll. A weapon that treated
  them alike would either never stop or never recover.

**Left, in the order that matters:**
- ~~No server or web surface.~~ **BUILT 2026-10-22.** `GameSession.resolveFire` routes a PPD
  out of the volley to `Game.firePpd`, and the EA dialog draws its arming controls off the
  `isHeavy` flag. What the weapon was missing was its CAPABILITIES — `holdEnergyCost`,
  `supportsOverload`, `overloadFinalTurnOnly` — because an undeclared capability is one a
  player simply cannot reach. **Still never seen in a browser**, which is the real gap now.
- ~~E11.6 OVERLOADS.~~ **BUILT 2026-10-22** (07b985c2, 31be16a0). `supportsOverload()` returns
  true, E11.611 caps turn one at four points, and a HELD charge can be overloaded during energy
  allocation without losing it (E11.63). E11.612's underload — six points in the second turn for
  one extra pulse rather than two — is in. Reserve/battery power into a heavy weapon is NOT, and
  is a gap for every weapon rather than this one.
- ~~E11.17 FLEET LIMIT~~ **BUILT 2026-10-23** in `FleetValidator.checkPpdFleetLimit`, beside the
  Orion cartel quota as planned. The paraphrase this line used to carry — "four per ship plus one
  per three ships" — was wrong twice over, which is why the rule text is quoted in the javadoc:
  the flagship contributes **whatever PPDs it actually has**, capped at four, not a flat four; and
  the allowance is per FLEET, not per ship. Overall ceiling nine. Bases excluded from both sides
  of the sum. "The flagship (the largest ship present)" is read as the DESIGNATED flagship (S8.21)
  where the two could differ — the owner's call, 2026-10-23.
  - **NOT implemented, deliberately**: "each flotilla of six (not less) PFs counts as one ship".
    There is no PF anywhere in the game, so there is nothing to count. Revisit when PFs arrive.
  - "Fighters do not count at all" needs no code — they are not in `fleet.ships` and none carries
    a PPD — and is recorded only because an absent clause reads exactly like an oversight.
- ~~E11.16~~ **BUILT 2026-10-23** as `ShipLibraryIntegrityTest.noHullSmallerThanSizeClass3CarriesAPpd`,
  a data guard that walks every hull file, counts PPDs in base armament AND in refits, and fails
  the build naming the file. Proven both ways: a PPD added to the size-class-4 FFL and one added
  by a REFIT to the size-class-4 DD each turn it red.
  - **NOT generalised to the other five weapons carrying `maxSizeClass`, and that was the whole
    question.** The plan had been to reuse the Annex #8B bar for Plasma-S, Disruptor-30/40 and
    Hellbore as well. The data refuses it: SEVEN size-class-4 hulls carry a Hellbore, FOUR carry
    a long disruptor, and one size-class-5 hull carries a plasma-S - all correct. Annex #8B's
    double-dagger is a rule about what an Orion may buy INTO AN OPTION MOUNT on a small hull, not
    a property of the weapon. E11.16 is different in kind: stated about the device itself, in the
    PPD's own rules, applying wherever a PPD is installed. Generalising would have failed a green
    suite twelve times over against data that is right.
- **E11.39** general reinforcement against individual splash elements, **E11.14** no
  non-violent combat.
- **E11.38 NARROW SALVOES - DEFERRED by the owner 2026-10-23**, who has "never been a fan, but
  they are in the rules". Not a PPD gap: narrow salvoes are **not implemented anywhere in the
  game** - `narrowSalvo` matches nothing in core or server - so the PPD clause has no general
  mechanism to hang off yet. Whenever narrow salvoes are built, E11.38 comes with them.
- **E11.4x INTERACTIONS - read 2026-10-23, and they are NOT one job.** Grouping all nine as
  "none built" hid which ones need anything. Sorted by what they actually ask for:
  - **Satisfied by absence, want only a guard test**: ESG (E11.41) "does not interact with an ESG
    field" and plasma systems (E11.45) "no effect on plasma torpedoes". Both are statements that
    nothing happens, and the PPD already resolves outside the volley, so the risk is a future
    change quietly creating an interaction the rule forbids.
  - **Already handled by a general mechanism**: wild weasels (E11.46) - a WW "will not distract a
    PPD wave-lock" but raises ECM and "could force another dice roll", and E11.53's reroll on a
    worsened shift is exactly that, built. Likewise E11.49's first half, standard ECM shifts
    under EM, which flows through natural ECM already.
  - ~~Cloaking (E11.47)~~ **BUILT 2026-10-23, all five clauses.** E11.471 (a fade keeps the
    lock), E11.472 (effective range for the roll, true range for damage, G13.37 reduction taken
    from the splash elements first), E11.473 (a fully cloaked ship cannot be HELD), E11.474
    (a wave-lock does not void the cloak - a guard, since nothing implements the opposite), and
    E11.475 (a cloaked, cloaking or uncloaking ship cannot fire), which was a LIVE BUG found by
    probe: the volley gates on `cloakActionBlock` and the PPD had never met it.
  - **E11.48 DISENGAGEMENT - DEFERRED, nothing to gate.** "A unit cannot disengage by sublight
    evasion (C7.3) while held in a wave-lock. Other means of disengagement are unaffected."
    `DisengagementResolver` implements C7.1 (acceleration) and C7.2 (separation) only; its own
    javadoc lists C7.3 as a future home. So this clause waits on C7.3 and should be built WITH
    it, not before.
    - Two things for whoever builds C7.3. First, the rule's second sentence means C7.1 and C7.2
      must stay UNGATED - the current no-gate behaviour is already correct for them, and an
      over-broad gate would be the easy mistake. Second, C7.2 is structurally unreachable while
      wave-locked anyway: separation needs no enemy within fifty hexes and a PPD only reaches
      forty, so a wave-locked ship is always too close. In practice E11.48's "other means"
      means C7.1.
  - ~~SUSPECTED LIVE BUG~~ **CONFIRMED AND FIXED 2026-10-23.** E11.49's second half: "the
    (C10.512) restriction on guiding seeking weapons does not apply to maintaining the
    wave-lock." A probe test proved it real before anything was changed: a ship going to EM
    mid-run showed capacity 0 against a used count of 1, and the next impulse boundary put the
    game into CONTROL_OVERFLOW asking its owner to shed the channel their own wave-lock was
    holding. `Ship.getControlCapacity` now returns `pulsingPpdCount()` under EM rather than
    zero, which says both halves of the rule at once - seekers still go, the wave-lock stays -
    and keeps C10.512 expressed as a CAPACITY rather than an exception inside the overflow
    sweep. STARTING a run while already on EM is a separate question and was deliberately left
    as found: the exemption sentence says MAINTAINING.
  - **Correctly parked, counterpart system does not exist**: web (E11.42), stasis (E11.43),
    displacement (E11.44).

**Deliberately parked by the owner (2026-10-22), not gaps to chase:**
- **Stasis and displacement** (E11.43/E11.44/E11.545) — neither system is implemented and
  neither is expected soon.
- **Webs** (E11.42/E11.547) — no web or web caster exists, and no plan to add one.
- **Andromedan power-absorber panels** (E11.354) — out of scope, as Andromedans are.

**E11.312's effective-vs-true range - NARROWER than this file used to claim.** The old wording
said "we pass ONE range for both, because nothing in the engine separates them". Both halves were
wrong, and checking beat believing (2026-10-23):

- The engine DOES separate them. `DirectFire.fire(realRange, adjustedRange)` is the centralised
  two-range path, overridden by Photon, Disruptor, FighterPhoton, PlasmaLauncher, PlasmaRack, ADD
  and DroneRack. `PlasmaLauncher` documents the split against FP8.42/FP8.43 and exists because a
  scanner once cost a plasma-R bolt ten points of warhead it should have kept.
- The PPD does not use that path at all - it is routed out of the volley through `PpdResolver`,
  which takes `MapUtils.getRange` (the TRUE range) and applies EW as a penalty added to the die
  roll. **Damage off true range is already correct**, and the EW half is correct too: the PPD's
  hit number steps by exactly one per range band, so +N on the roll IS N bands of shift.

**The scanner half is now BUILT 2026-10-23.** `Game.adjustedRange` is the single home for
D6.21's `range + scanner`, which was previously written out in three places and stated as
universal in a fourth (`ADD`'s javadoc) while the PPD quietly ignored it. `PPD` now overrides
`fire(realRange, adjustedRange)` and `pulse(realRange, adjustedRange)` like every other
hit-or-miss weapon, `PpdResolver` supplies both and recomputes them per pulse, and the range
GATES stay on the true range as `Photon`'s do — E11.312 bars engaging at a true range of three
or less, and a scanner must not put a target back in reach nor carry one out of it.

**What is left of E11.312:** only the cloak bonus, which is E11.47's business anyway and should
NOT be folded into `Game.adjustedRange` — G13 has its own fire-adjustment chart and counting
D6.211's five hexes in both places would apply it twice. The no-lock doubling (D6.123) is
unreachable for a PPD, which needs a lock throughout (E11.15); it is also absent from the
firing path for EVERY weapon, which is noted at `Game.adjustedRange` and is a separate question
from this one.

## Eleven test fixtures allocate more energy than their hulls hold (found 2026-11-07)

Found while adding the server-side allocation budget check (D22.0). Enforcing it inside
`Game.submitAllocation` broke eleven tests across six files, because their fixtures spend more
than the ship has: `CloakRetentionTest` flies a Romulan KR that holds 37 power, spends 20 of it
on the cloak, and then buys speed on top. The tests are about retaining a lock, not about paying
for it, so the forms are shorthand rather than claims.

That is defensible as a fixture and worth knowing as a fact: **those tests assert behaviour at
allocations the game cannot legally reach.** Whether that invalidates anything depends on the
test — a cloak-retention roll probably does not care how the speed was paid for, but "fast
cloaker" cases may be describing a hull that cannot both cloak and go that fast. A Romulan KR
with 20 going to the cloak has 17 left, which at moveCost 1.0 is speed 17 before life support,
fire control and shields come out of it.

The enforcement therefore sits in the ALLOCATE handler, at the boundary a client can reach,
while the arithmetic stays in `Game.allocationOverspend` where the rules live. Fixing the
fixtures is a separate job: each needs a form that balances, and some may need a different hull
or a slower speed to make their premise legal. `AllocationBudgetTest` covers the check itself.

Known omissions in the spend floor, all held outside `Energy` and so not yet counted: ECM and
ECCM, suicide-shuttle arming and holding, wild-weasel charging. The floor can only under-count,
which is the safe direction — it catches the gross case and never an honest form.

## Tactical Maneuvers (C5.0) — COMPLETE as of 2026-11-08, ships and shuttles both

Found while fixing the maneuver that rolled an HET breakdown die and silently did nothing
(`TacticalManeuverTest` pins that, and now these too — 13 tests).

**C5.31 — warp and sublight in the SAME IMPULSE. BUILT.** A ship may use both kinds in one turn
"but NOT during the same impulse", and nothing enforced the second half: with both paid for,
`performTacticalTurn` spent the warp maneuver and then, on the very next call in the same
impulse, fell back to the sublight one — 120° on one impulse. `Ship.lastTacImpulse` records the
impulse of the turn a maneuver of either kind was made on, cleared by `startTurn()` like
`sublightTacUsed`, and `performTacticalTurn` refuses a second one on the same impulse. The
client disables the strip from `tacUsedThisImpulse` on the DTO, which is PUBLIC — a ship turning
in place is seen by both captains, exactly as `lastHetImpulse` beside it already is.

The first half of the rule is tested too (`bothKindsMayBeUsedOnDifferentImpulsesOfTheSameTurn`),
because a ban written as "one maneuver per turn" passes the same-impulse test and is wrong.

**C5.13 — the zero-energy turn. BUILT for SHIPS.** A ship with no power allocated to any movement
function gets one 60° turn free on attitude thrusters, on Impulse #32 only. Before this such a
ship had `tacBudget` 0 and could never turn at all — and it is the ship most likely to want to,
being the one parked on a stasis box studying it (SH47.474), whose movement line is empty by
design. `Ship.movementPowerAllocated` is set in `startTurn()` from the six movement functions on
the allocation form (warp and impulse movement, HETs, EM, and both kinds of Tactical Maneuver);
`isZeroEnergyTurnAvailable()` is that plus the free turn being unused. `zeroEnergyTurnAvailable`
on the DTO is PRIVATE, since it is a statement about his allocation form rather than about
anything on the map — unlike speed 0, which everyone can see.

**Two clauses of C5.13 needed no state, and the reasons are worth keeping** because both stop
being true later:
- *"did not move under its own power for the entire turn"* FOLLOWS from the allocation test here.
  A ship that allocated no movement energy has speed 0 and no way to buy a speed change, since
  plotted mid-turn changes (C12.31) are not built. A tractor or terrain dragging it is not
  movement under its own power and does not disqualify it. **The day C12.0 arrives this needs
  the second test for real.**
- *"cannot be combined with normal Tactical Maneuvers during the same game turn"* is automatic,
  because both kinds of normal maneuver ARE movement functions, so a turn that qualifies for the
  free one cannot have bought any. **That stops being automatic if C5.5 reserve-power maneuvers
  are built**, which would let a ship buy a maneuver it never allocated for.

**The SHUTTLE half of C5.13 is BUILT too** (same day, after the owner's ruling). "A shuttle
which does not move under its own power for an entire turn may make a zero energy turn of up to
180° on the last impulse of that turn." It is a separate mechanism from the ship's, not the same
rule with different numbers:

- **The test is SPEED, not the allocation form** — the owner's call, and the better fit: a craft
  has no allocation form, and C5.13 asks only whether it moved under its own power, which for a
  craft is exactly what its speed says. (For a SHIP speed 0 is weaker evidence, since a ship can
  pay for movement and sit still anyway, which is why that half reads the form.)
- **`getSpeed()`, deliberately not `getCurrentSpeed()` as well.** That is the field
  `ShuttleMover` consults to decide whether a craft moves this impulse. The two can disagree:
  J1.6223 gives a craft under tractor recovery the HOLDING ship's speed while `currentSpeed`
  keeps whatever it had, and a craft being DRAGGED at speed 0 is not moving under its own power
  — so the rule allows its turn and a test of both fields would wrongly refuse it.
- **"Up to 180°" reaches every facing on a hex grid**, so there is no angle to check — only that
  the craft stood still. A fighter gets this as well as its once-a-turn HET (C6.42): different
  things, and C5.13 says "a shuttle" without excluding fighters.

`Shuttle.zeroEnergyTurnUsed` with `startTurn()` clearing it, `ShuttleMover.performZeroEnergyTurn`,
`PERFORM_SHUTTLE_ZERO_ENERGY_TURN`, and a **Free turn** button on the craft's movement panel.
`ShuttleZeroEnergyTurnTest` has 11 cases and three of its guards were proven by mutation.

**Two traps it left behind:**

1. **`Fighter.startTurn()` must keep its `super.startTurn()` call.** The flag lives on `Shuttle`,
   one level up, and `Fighter` overrides the method — so without that call a fighter's free turn
   is one a game. The call did not exist before this work; it was added. This is the same shape
   as the bug `FighterTurnResetTest` documents, where `Fighter.startTurn` existed for months with
   nothing calling it at all. `theFightersFreeTurnComesBackNextTurn` fails if it is dropped.
2. **One known imprecision, left alone deliberately.** `Game.makeSeekerShuttleInert` zeroes the
   speed of a seeking shuttle that loses control mid-turn (G24.224), so one that flew six hexes
   and then went inert reads as having stood still and would be granted a free turn it did not
   earn. Harmless — an inert seeking shuttle's facing decides nothing — and the alternative is a
   per-turn movement flag on every craft for that one case. Revisit it if a craft ever gains
   another way to stop mid-turn.

## C7.11 asks about WARP POWER, and we test the acceleration ceiling (found 2026-11-07)

`DisengagementResolver.queueAccelDisengageCandidates` offers C7.1 to a ship that ran at its
C2.21 acceleration ceiling. The rule asks for something else: "the starship wishing to
disengage by acceleration must move (for the entire turn) at the maximum possible practical
speed which it can, as restricted by available warp engine power (which may have been reduced
by combat) and the game imposed speed limit of 31."

So the test should be `speed >= min(31, hexes its available warp power can buy)`, with
C7.124's exclusion — "the first sentence of (C7.11) does not include any energy spent for life
support, shields (not reinforcement), or fire control. The ship may make these expenditures and
then calculate its maximum speed based on the remaining energy." The acceleration ceiling is a
real restriction too, so the honest expression is the minimum of the two.

Today's test is NARROWER than the rule: a ship with power to spare but no acceleration headroom
qualifies under C7.11 and is not offered. Longstanding, and not introduced by the 2026-11-07
acceleration fix — but that fix is what made it visible, because correcting the cap meant the
post-cleanUp value could never be reached and C7.1 stopped firing altogether until the check
was pointed at `Ship.accelerationCeilingThisTurn` instead.

Note for whoever builds it: four DisengagementTest cases and one in CloakRetentionTest depend
on the current behaviour, and the cloak one depends on it in a way worth reading — a ship at
its ceiling is offered C7.1 every turn, and a fixture that does not answer hangs rather than
fails.

## C12.33's within-turn minimum is not tracked (found 2026-11-07)

"The lowest speed during the previous 32 impulses determines the maximum speed to which the
ship can accelerate." `Ship.speedPreviousTurn` is the speed at the turn's END, not the lowest
held during it. Exact today, because plotted mid-turn speed changes (C12.31) are not built and
the drops that do happen — emergency deceleration, a tractor — leave the ship at the lower
speed when the turn ends. It stops being exact the day C12.0 arrives, and the fix then is a
running minimum updated wherever speed changes mid-turn, rolled into the history at cleanUp.

## SH47.5 is not implemented: securing the box does not win (found in play 2026-11-07)

DEFERRED by the owner twice: on the day it was found — "the players know who won" — and again
**2026-11-10, this time with a condition attached: do not build it for SH47.** The owner's words:
"the next time we start working on objectives again (probably another scenario) we can revisit
what common logic would help them."

**So this entry is NOT ready to pick up on its own, and that is deliberate.** Building the three
pieces below now would shape them around the only scenario that needs them, which is the exact
failure the owner has objected to repeatedly and which `project_cargo_objective_model` exists to
prevent. The design in outline is sound and should be read before the next objective scenario is
started — but the second scenario is what tells us which of these three is general and which is
SH47-shaped. Pick this up WITH that scenario, not before it.

A worked example of why waiting helps: `winsOnSecure` reads as obviously general until you ask
what a DELIVERY scenario wants, where securing is reaching a specific hex rather than any exit,
and the flag has to become a destination rather than a boolean. One more data point decides it.

**What happened.** The Kzinti carried a Stasis Box off the map by acceleration and the scoreboard
said "Stasis Box 1 — secured" under its side. The game then ended on the generic last-side-
standing check in `Game` ("All opposing ships destroyed, disengaged, or captured") and handed
the FEDERATION the win, with an "Astounding Victory" banner scored in victory points — in a
scenario whose box is deliberately worth `points: 0` because SH47.5 is not a VP rule.

**Why nothing fired.** `ScenarioSpec.VictoryConditions` has exactly two fields, `type` and
`notes`. `notes` is prose for humans; nothing anywhere branches on `type: "SPECIAL"`. The
machinery underneath is all present and correct — `secureObjectivesFor` is called by all three
exits (map edge, acceleration, separation), the objective is marked secured permanently, and
the scoreboard already shows it. Only the reading-back is missing.

**The design, kept general rather than SH47-shaped** (the owner's standing concern about
one-scenario code paths):

- `winsOnSecure: true` on the OBJECTIVE — "the side that gets this off the map wins outright".
  A victory ROLE on the objective, which is where `project_cargo_objective_model` already says
  it belongs. Any later "capture the thing and run" scenario uses the same flag.
- `tiebreak: "FEWEST_INTERNALS_SCORED"` on the victory conditions — SH47.5's last clause, "if no
  boxes exist, the player who scored the most internal hits on the other loses the scenario,
  having ruined his career by provoking an incident". Internals per team are already tracked
  (`internalsScoredBy`). Reusable for any don't-start-a-war scenario.
- `GameEndResult` holds ONE `winnerTeam` and must hold a list: SH47.5 says in terms that with
  two boxes "it is possible that both players could win".

**One ruling still open — but it is now a ONE-LINE DATA CHANGE, not a code decision.** SH47.41
says "The Kzinti can only disengage from 01xx map edge." That can be read as restricting ALL
disengagement to that edge, which would exclude C7.1 by acceleration entirely. The engine allows
acceleration through a safe direction, which is defensible (C7.0 lists acceleration as one of
four means, and SH47.5 just says "successfully disengages").

**RULED AND APPLIED 2026-11-09** (ae16a011). The owner read the clause the strict way, in two
parts. First, it restricts ALL disengagement, so both sides of `sh47.json` now carry
`"disengagement": ["MAP_EDGE"]` — no C7.1 acceleration, no C7.2 separation, a ship out of options
must cross its own edge. Second, the restriction is a REFUSAL and not a death sentence: both
sides state `"exitEdges": ["RIGHT"]` / `["LEFT"]` and `destructionEdges` is now EMPTY. It used to
hold the other three, which enforced a harsher rule than the text prints — SH47.41 never says an
illegal exit destroys the ship, where SH80.41 does say so when it means it. `Sh47LoadsTest
.eachSideMayLeaveByOneMapEdgeOnly` fails if anyone puts the lethal edges back, and says why.

This closes the half of the question that was data. The other half is the scoring above, and it
stays open — the playtest that found this found TWO bugs stacked: an illegal exit the engine
allowed, and a legal win it could not record.

## Per-scenario disengagement rules (C7.0) — BUILT 2026-11-09

Asked for by the owner: "for a scenario, we should be able to specify what kind of disengagement
is allowed (separation, leaving the map, acceleration). And there are some missions that only
allow disengagement by acceleration in certain directions."

**Two new fields on `SideSpec`, and they are deliberately different shapes:**

- `disengagement` — a set of `DisengagementMethod` (`ACCELERATION`, `MAP_EDGE`, `SEPARATION`).
  **Empty or absent means ALL OF THEM.** That default is load-bearing: every authored scenario
  and every saved game in `data/` says nothing here, and if absent meant "none" each of them
  would silently become a battle nobody can leave. Same choice R1.7's acceleration flags made.
- `accelDisengageDirections` — the A–F directions C7.1 may be used ALONG; empty means any.

**The second field is NOT `destructionDirections` with the sign flipped, and that is the whole
design.** A direction missing from the permit-list is simply not a way out, and the attempt is
refused. A direction in the kill-list destroys the ship that takes it. SH47.41's "can only
disengage from the 01xx map edge" is the first kind, and expressing it with the kill-list — the
only way available before — states a harsher rule than the scenario does. Both may be set at
once, and the destruction check runs first, because a direction that is permitted and lethal is
a trap the scenario meant to lay.

**Where each is enforced**, and why not all in one place:
- `Game.sideMayDisengageBy(ship, method)` is the single policy lookup; the resolvers call it
  rather than being handed two more maps through their constructors. A ship with NO OWNER is
  unrestricted — there is no side to read a restriction from, and refusing would break every
  fixture built without players.
- C7.1: checked in `queueAccelDisengageCandidates`, so a denied ship is never OFFERED the
  disengagement. A prompt that can only be refused is worse than no prompt. This stacks with the
  hull's own `canDisengageByAcceleration()` (R1.7) — a plain freighter still cannot, however
  permissive the scenario.
- C7.2: `canDisengageBySeparation`.
- Map edge: in `ShipMover`, and it **REFUSES THE MOVE** rather than destroying the ship. Keeping
  that distinct from `destructionEdges` is the point of the field; a scenario saying "you must
  fight this out" must not become one saying "and die if you try to leave".

**The briefing had to change with it, and this is the trap to remember.** `exitEdges` on the
`/player-aid/briefing` endpoint is DERIVED as the complement of `destructionEdges`. A side barred
from the map edge still has an un-lethal edge in that arithmetic, so without a fix the panel
promised an exit the engine refuses — a derived field contradicting the rule it came from, the
same shape as the privacy bug where a derived field leaked the one it was meant to withhold. The
endpoint now sends an empty `exitEdges` for such a side, and sends `disengagement` RESOLVED so no
client has to know that an absent field is the permissive case.

**Validation is free and loud.** `ScenarioSpec`'s mapper is a plain `ObjectMapper`, so a method
name the enum does not have throws at load rather than deserializing to null. That is deliberate:
the enum holds only the three BUILT methods, so a scenario permitting sublight evasion (C7.3,
unbuilt) is refused instead of being silently ignored. **Whoever builds C7.3 adds it to the enum**
— and `automatic` (C7.4) is deliberately absent, being a scenario taking a ship off the board
itself rather than a player action with anything to gate.

**No save-format change.** `SavedGame.scenario` holds the whole `ScenarioSpec`, so the new fields
ride along, and an old save lacking them defaults permissive — which is exactly how it behaved
when it was written. Neither version number moved.

**PER SIDE, not per battle, and proven so.** The owner's condition on accepting the feature:
"so long as we are able to have separate accelDisengageDirections for each side in the battle".
Both fields live on `SideSpec`, so that is the shape — but every other direction case restricts
one side only, which means a map keyed wrongly, or a last-side-wins bug in the load loop, would
pass all of them. `eachSideGetsItsOwnPermittedDirections` and its reversed twin are the cases
that fail: two ships both facing A, one side permitted A and the other D, one offer and not two.

`ScenarioDisengagementTest` (17 cases) and five more in `BriefingEndpointTest`. Three mutants
were run, each modelling a different wrong implementation:
- permit everything → the four denial cases fail;
- make "absent" mean "none" → the default cases fail. **This is the one worth remembering** —
  it is the mistake that would quietly re-rule every existing scenario;
- one direction list for the whole battle, as if the field sat on the scenario → the three
  per-side cases fail.

**A trap that applies to EVERY per-team scenario rule, found while pinning SH47.** These two
fields, `destructionEdges` and `destructionDirections` are all keyed on
`ship.getOwner().getTeamName()` — and `Game.setupFromScenario` attaches NO OWNER to the ships it
builds. Core alone holds a side's name and a side's ships and never joins them; `GameSession`
does it, setting a seated player's team from the side that lists the hull.

So **a battle run through core with no players gets no per-team restriction of any kind.** That
is fine for the two-browser game that exists today, and it is a trap for whoever adds a
single-player or AI path: four scenario rules would silently stop applying, with nothing
failing. Not changed here, because making `setupFromScenario` seat owners would alter team
resolution for every core-only game at once — a wider blast radius than this entry's subject.
`Sh47LoadsTest.theEngineDeniesAccelerationAndSeparationToBothSides` seats them the way a session
does, and says so; its first draft did not and failed, correctly.

**SH47 now states the ruling** (owner, 2026-11-09): `"disengagement": ["MAP_EDGE"]` on both
sides. SH47.41 restricts ALL disengagement, so neither ship may outrun the battle or separate
away — which is what the 2026-11-07 playtest exposed, the Mystic having carried a Stasis Box off
by acceleration.

**`exitEdges` — the edge permit-list — BUILT the same day**, on the owner's call, so SH47 now
reads exactly as printed. Which EDGE a side may use used to be expressed as three LETHAL edges,
which the file's own comment had always flagged as harsher than the text: SH47.41 never says an
illegal exit destroys the ship, where SH80.41 does say so when it means it.

- `SideSpec.exitEdges`, empty or absent falling back to "any edge not in `destructionEdges`" —
  which is what every other scenario still relies on, and is asserted rather than assumed.
- Enforced in `ShipMover` BEFORE the tractor links are released, because a refused move must
  leave the ship exactly as it was.
- Destruction still wins where both are set: an edge permitted and lethal kills, same
  precedence as the direction pair.
- **The briefing's derived `exitEdges` now yields to a stated one** (`feedback_derived_vs_stated`).
  This is not hypothetical: SH47 states a list AND carries an empty kill-list, so the derived
  complement would be all four edges and the panel would promise three exits the engine refuses.
- No web change — the payload key was already `exitEdges`, so a better source flows through the
  same field.

`sh47.json` is now `"disengagement": ["MAP_EDGE"]`, `"exitEdges": ["RIGHT"]`/`["LEFT"]` and an
EMPTY `destructionEdges`. If anyone is tempted to put those destruction edges back, the scenario
text is the reason not to, and `Sh47LoadsTest.eachSideMayLeaveByOneMapEdgeOnly` now fails if they
return.

**Still open here:** C7.3 sublight evasion remains unbuilt, and `DisengagementResolver`'s own
header still names it as the future home.

## ObjectiveResolver: the next extraction (named by a reviewer 2026-11-07)

Game.java is 6,941 lines (measured 2026-11-07 — `wc -l` it rather than trusting this). Sixteen
collaborators have been extracted and the file still grew by ~850 lines since the last outside
review, most of it the SH47 objective work landed this month.

Objectives are now a domain in their own right and a natural next extraction: searching counters
(SH47.471/.472), revealing them, studying (SH47.474), guarding (D7.83), transferring between
ships, recovering by tractor (J1.621), securing on exit, dropping on destruction, scoring, and
save/restore. Roughly ninety lines in Game mention an objective.

Follow the established pattern: one domain per commit, extract OPPORTUNISTICALLY when the
objectives code is next touched rather than as a planned refactor, keep the pending/shared lists
declared in Game and passed to the constructor by reference, keep phase transitions in Game
behind package-private hooks, and leave one-line public delegates so the server and tests do not
move. The SH47.5 victory work above is the obvious moment — it touches securing and scoring,
which are two of the pieces that would move.

## Ready to start

Each of these was named by an earlier session as the next sensible slice in its area, with the
groundwork already in place.

- **Orion multi-mount option weapons (G15.4) — DONE.** Core 2026-10-20, both pickers
  2026-10-21. `ShipSpec.adjacentMounts` declares runs per hull (`[["A","B"],["C","D"]]`);
  adjacency means CONSECUTIVE within a run, so a row of three gives A-B and B-C but not A-C.
  `OptionMountLoadout.seatFor` finds a window covering the chosen mount, every mount in it free
  and position-legal, and `equip` seats the weapon on all of them: the far half holds the SAME
  weapon object, marked `isContinuation()`, so it reads as occupied rather than empty. BPV and
  option name stay on the primary — one weapon is one purchase however many boxes it fills — and
  the weapon joins the ship's list ONCE (a list holding it twice would fire it twice).

  The pickers know it too: the server sends the adjacency runs and `mountsRequired`, and
  `optionMountSeating.ts` applies the same window rule for the fleet builder and the COI form.
  Its position test is the server's own per-mount `legalOptions`, not a second copy of G15.43.

  **Still open here:**
  - **A two-mount weapon cannot be UNSEATED** — because nothing unseats a one-mount weapon
    either. There is no remove path at all, only `equip`. The pickers work around it by
    rebuilding the choice map, which is fine for a fleet being built and would not be for a
    ship in play.
  - **`OptionMount.Position` has no WYN hull-side value.** A Plasma-G is legal in two adjacent
    hull-side mounts and illegal in two adjacent wing mounts, so hull-side cannot be squeezed
    into WING. No WYN ship is in the data, so nothing is wrong today.
  - **The swivel question — ANSWERED 2026-10-21, and it was worse than transposed.**
    `WeaponFactory` derives a plasma launcher's launch directions from its arc (D2.34) and a
    stated list wins, so `launchDirections: ["FA"]` pinned the swivel torpedo to the FIXED
    one's single direction and cancelled what it cost +1 for. R8.R3 says the difference is the
    ARC: FA against FP, one launch direction against D2.34's three. The override is gone and
    `swivel` is stated instead. Removing it exposed a second bug beneath — the derivation read
    `ws.arcs`, null on a catalogue option because an option's arcs come from the MOUNT, so
    every option-mount plasma had derived ZERO launch directions.
  - **The PPD's position — SETTLED.** R8.R3: "Hellbore, PPD: Not Allowed" in wing mounts, so it
    is `positions: ["CENTERLINE"]` like the Hellbore. **The ESGs are still open**, narrowly:
    R8.R3's wing table assigns ARCS, and an ESG has none (it generates a field, G23.0), so its
    absence from that table is not evidence either way. Both ESG rows still permit either
    position.

- **R1.32 Base Augmentation Modules.** Module R1 SSD book p.78, not yet read. All nine Base
  Stations declare the fighter, hangar and PF modules out of their baseline, so this is what
  finishes them. Build module **purchasing** with fighters as one entry in a catalogue — the
  sheet draws fighter modules because it has to draw something, and there are many kinds. A
  module is bought, not declared, so the refit model is close but wrong for it: you may buy none,
  one, or several of the same kind.
- **Ballistic targeting (F4.0) is BUILT** — see the ground-base section below for what landed and
  what did not. Kept here only as a pointer, because this entry used to hold the full reading of
  F4.0 and somebody looking for it will come here first. Still open: the SCATTER-PACK has no
  ballistic door (it needs F4.41/F4.42/F4.43, which is a feature in its own right — see the
  scatter-pack section), F4.3 wild boars, F4.5 tractors, and firing at a bare hex has no UI.
- **Mine racks (M9.0) — INVENTORY, RATE AND PURCHASE BUILT 2026-11-10. Damage (M9.13) and the
  in-play transfer (M9.19) are what is left.**

  `com.sfb.systemgroups.MineRack` is the box; `Ship.getMineRacks()` holds them, built empty from
  `auxiliary.mineRacks` (now a real `ShipSpec` field, unparked from `ShipJsonKeyGuardTest`).
  `MineResolver.dropMine` draws from a rack in preference to the loose allocation, because M9.12
  gives rack mines no reloads and spending the loose stock first would strand the scarcer kind.
  `CoiLoadout.rackNsms`/`rackTBombs`/`rackDummyTBombs` buy the contents and bill
  `ForceCost.RACK_MINES`, so the force-total work of the same day is what made this reachable.

  **Capacity is counted in SLOTS, not mines, and that is load-bearing.** M9.11: four large mines
  a box, two small substituted for one large. A lone T-bomb therefore occupies a WHOLE slot with
  its partner cradle open — three large mines still fit beside it, not three and a half — and a
  mine count cannot express that. `MineRackTest` pins the arithmetic.

  **M9.21's rate split is now wired, and `laysOneMinePerRack()` finally has a caller.** For a
  minesweeper or minelayer a rack IS a launcher: one mine per rack per turn, no bay consumed, no
  hatch cooldown. For everyone else the rack is only an inventory and the hatch is the
  bottleneck.

  **A bug found by reading M9.21 against the code rather than by a failing test.** The converted
  hull's rate is "one per turn per shuttle bay", and the bay cooldown this reused is TWO
  IMPULSES — so a one-bay hull could have laid sixteen rack mines in a turn with every other
  check satisfied. Now tallied per ship per turn against the bay count, because the rule counts
  BAYS: two racks behind one bay is one mine a turn, from whichever rack.
  `aConvertedHullLaysOnePerTurnPerBayHoweverLongTheTurnIs` holds it.

  **REACHABILITY, stated plainly: the per-rack rate has no hull to exercise it.** No ship in the
  data declares `mineRole`, because none is a minesweeper or minelayer — and the owner ruled
  (2026-10-19) that the Romulan large Q-ship, the only hull with racks, takes the BAY rate, being
  neither (M2.78/M2.79) and M9.16 being emphatic that an S7.0 conversion is "not considered a
  minesweeper for any purpose". So the fast path is correct, tested and unreached until a sweeper
  or layer is entered. `aHullWithNoDeclaredRoleLaysAtTheBayRateNotThePerRackRate` records the
  claim and is where it stops being true.

  **STILL OPEN:**
  - **M9.13 damage.** "Cargo hits MAY be scored on mine rack boxes and MUST be scored on them if
    no other cargo boxes are available. Shuttle hits must be scored on shuttle or mine rack boxes
    if any of either type exist. See Annex #7E." `MineRack.destroy()` exists and correctly loses
    the mines WITHOUT detonating them (the rule says so in a parenthesis, and four NSMs going off
    inside the hull would end the ship) — but nothing calls it. The DAC dispatches on system
    names in `Ship.applyInternalDamage` ("cargo" reaches `hullBoxes.damageCargo()`), so the
    mandatory halves are a change there; the "may" halves are a DAC CHOICE and want the existing
    `enterDacChoicePhase` mechanism.
  - **M9.19's in-play transfer.** "A ship with mine racks can load or unload one large mine from
    any one of its racks during one turn ... The rack must be taken out of service for the entire
    turn." `MineRack.takeOutOfService(turn)`/`isOutOfService(turn)` exist and are honoured by the
    laying path; nothing sets them, and the FD2.42 procedure the rule cites is unread.
  - **M9.23.** Captor and sensor mines, and any control, chain or deadman switch mine, "cannot be
    laid during a scenario". None of those kinds is modelled, so there is nothing to refuse yet.
  - **M9.15.** Small explosive mines removed from the racks "can also be used as T-bombs on the
    following turn" — a one-turn delay nothing implements, and it needs M9.19 first.
  - **No UI.** The whole system is server-side. The racks are not in the DTO, the Hangar Ops or
    EA dialogs do not show them, and no button lays from one, so a player cannot reach any of it
    in a browser. This is the largest remaining risk here, and it is the same shape as the chaff
    entry: core correct, unreachable.

  **The original entry follows, accurate except where the above supersedes it.** The Romulan
  large Q-ship is the first hull in the data to carry them, and `auxiliary.mineRacks` WAS parked
  rather than read.

  **The laying ACTION is already built and correct** (established 2026-10-19). A rack mine is
  laid exactly as a bay-dropped one is: M9.0 says "large or small mines can be laid directly (in
  the hex occupied by the ship); see (M2.0)", and M2.12 puts the counter in the ship's own hex.
  So `Game.dropMine` already does the placement, records `PlacementMethod.DROPPED`, arms by
  RANGE rather than by the T-bomb's timer (M2.31: the layer must move two hexes off), arms
  immediately if the layer dies (M2.32), and honours M2.34's sequencing. Because `DROPPED`
  already exists as a placement method, **the saved-game format carries rack mines with no
  change at all**. EM is guarded too (C10.514, 2026-10-19).

  **What was left was three things, none of them the laying** — the first two are now DONE:
  1. ~~*Inventory*~~ DONE. `MineRack`, built from `DroneRack`'s shape for capacity-that-varies,
     but deliberately WITHOUT its reload list: M9.12 gives these racks no reloads, and copying
     that part would have invented a rule.
  2. ~~*Rate, and whether the bay is involved*~~ DONE, and the bay requirement is now
     conditional exactly as this predicted.
  3. *Damage location* — M9.13, still open. See the list above.

  **M9.21 splits three ways**, and only the first gets the fast rate:
  - minesweepers and minelayers: one mine **per rack** per turn, no bay consumed, no quarter-turn
    spacing, no M2.111 conflict with shuttle launches;
  - ships converted under S7.0: one per turn **per shuttle bay**, regardless of how many racks;
  - Orion option-mount racks: one per rack, but Orion racks *in* bays are one per bay per turn.

  **OWNER'S RULING 2026-10-19: the Romulan large Q-ship uses the BAY RATE.** It is neither a
  minesweeper (M2.78) nor a minelayer (M2.79) — those are separate entries from M2.76's Q-ship —
  and M9.21 says "Only minesweepers and minelayers use these rates". So its two racks are
  inventory only and the bay is the bottleneck: one mine per turn, not two. M9.16 supports it:
  "Ships modified by (S7.0) to carry mines in the shuttle bay are not considered minesweepers
  for any purpose."

  ~~**Cost is a separate prerequisite, and not this entry's to solve**~~ — SOLVED 2026-11-10,
  earlier the same day. The mines in a rack are not part of the ship's BPV (M9.17) and are bought
  outside the 20% cap, which is now `ForceCost.RACK_MINES`; `CoiLoadout.rackMineCost()` prices
  them from Annex #6 (NSM 8, transporter bomb 4, and a dummy charged as a small mine because
  M9.11 withdraws M3.224's free ones). A hull with no racks is REFUSED rack mines rather than
  charged for them (M9.14), since paying for stock you can never lay is worse than a refusal.

  **M9.11:** minesweepers and minelayers carry mine storage boxes. "Each such box holds FOUR large
  mines. Two small mines can be substituted for one large one." So a rack is 4 NSMs or up to 8
  T-bombs — the owner's analogy is a type-G drone rack holding type-I against type-VI drones, and
  `DroneRack` already models capacity that varies by what is loaded, so it is the pattern to copy
  rather than a new idea. **M9.12: there are NO reloads for mine racks**, and the mines in them do
  not come with the free dummies a T-bomb gets (M3.224).

  **M2.76 is the per-ship rule, and it reaches the COI system.** "The Romulan large Q-ship carries
  two mine racks, each of which holds four NSMs. The mines on the Romulan large Q-ship are not
  included in its BPV, but can be purchased in excess of the Commander's Options (S3.2)." So the
  hull is 8 NSMs of capacity starting EMPTY, its BPV 80 is correct without them, and when mines
  become purchasable they must be bought OUTSIDE the Commander's Options budget rather than against
  it.

  **And there is already a pattern that does the opposite**, which is what makes this worth writing
  down. `CoiLoadout.extraTBombs` buys transporter bombs at `COST_TBOMB = 4.0` each and sums them
  into the COI cost like every other option — correctly, for T-bombs. Rack mines are a different
  purchase under a different rule (owner, 2026-10-12: "I know you can buy T-Bombs in the COI, but
  that's different than buying mines for the mine rack"), so copying `extraTBombs` for them would
  silently charge against a budget M2.76 says they sit outside.

  **M9.13 makes them a damage location**, which is the part that reaches beyond minelaying: cargo
  hits MAY be scored on mine rack boxes and MUST be when no other cargo boxes remain, and shuttle
  hits must be scored on shuttle or mine rack boxes where either exists (Annex #7E). Mines on a
  destroyed rack are destroyed and do NOT detonate. **M9.14:** a non-minelaying ship cannot carry
  mines at all except under M3.1 or M2.7.

  What exists today: `Game.dropMine` takes "TBOMB", "DUMMY_TBOMB" or "NSM", `MineResolver` runs
  arming and detonation, and `auxiliary.nuclearSpaceMines` gives a ship its NSMs. What is missing
  is the RACK as an inventory and as a hit location.
- **Costs that count against the FORCE total but escape the per-ship COI cap — THE CATEGORY IS
  BUILT 2026-11-10. `com.sfb.properties.ForceCost`.**

  **Three things this entry had wrong, all found by reading the rule and grepping before
  building.** They are kept here because each was a plausible-sounding claim that cost nothing to
  check and would have cost a migration to act on.

  1. **Drone speed upgrades were NOT parked — they were already built, S3.221 exception and
     all.** `Ship.droneSpeedBpv` existed, `FleetValidator.costOf` summed it into the force total,
     `CoiBudget` was deliberately blind to it, and `CoiLoadout.droneUpgradeCost(ship, year)`
     already implemented S3.221's exception: "Faster drones available as limited or restricted
     weapons in years prior to their general introduction are purchased as Commander's Options",
     so speed bought AHEAD of availability is an ordinary option INSIDE the cap. The mechanism
     this entry asked for therefore existed, named after its first member. What was actually
     needed was to generalise it, which is what landed.
  2. **(S2.14) splits the category in two, and this entry never cited it.** The Gross Adjusted
     BPV — the basis victory percentages are taken of — "includes the cost of drone speed
     upgrades (FD2.22), the effect of crew quality (G21.0), the value of Legendary Officers
     (G22.0)" and does NOT include "Commander's Optional Purchases (S3.2)". M9.17 buys a
     minesweeper's mines under S3.2. So rack mines are in the force total and OUT of the victory
     basis, while the other three are in both. The single "second spend" this entry proposed,
     summed into the GABPV the way `droneSpeedBpv` was, would have made a loaded minelayer worth
     32 points more to destroy than the chart says — which is the exact failure
     `ForceCostTest.rackMinesDoNotMakeAMinelayerWorthMoreToKill` reports under a mutant.
  3. **Annex #6A prices crew quality as a PERCENTAGE, and Poor Crew is −20%.** So a member of
     this category can be negative and fractional: it is an adjustment, not a spend, and the
     `int` the first member got away with would have been a lie. Annex #6A also names a FOURTH
     member this entry missed — "Legendary Officers, if used, are purchased as part of the base
     force, not as Commander's Options."

  **What landed.** `ForceCost` is an enum of four members, each carrying `countsInGabpv()`;
  `Ship` holds an `EnumMap` of them with `getForceCost`, `setForceCost`, `getForceCostTotal`
  (what the quartermaster pays) and `getGabpvForceCost` (what the enemy scores).
  `FleetValidator.costOf` sums the total, `Game.calculateVictoryPoints` sums only the GABPV
  subset, `CoiBudget` still never reads either, and `getDroneSpeedBpv()` survives as a delegate
  so its callers and tests did not move. `ForceCostTest`, 12 tests, sweeps every member against
  both axes so a fifth member cannot be added and silently missed.

  **THE TRAP, and it is in a javadoc as well as here: do NOT derive `CREW_QUALITY` from
  `ship.getCrew().getCrewQuality()`.** Four Klingon penal hulls declare `crewQuality: "poor"`
  and for all four the discount is ALREADY IN THE STATED BPV. `d6j.json` says it outright: "epv
  123, bpv 98, and the 98 is live here BECAUSE crewQuality is poor." Worse, the chart's split is
  not the rule's number — the E4J is 62/40, a 35% gap where Annex #6A gives 20%. The two are
  different acts: a penal ship does not PURCHASE a poor crew, it comes with one and its chart
  entry is already discounted, while Annex #6A prices a player CHOOSING a quality for a scenario.
  Only the second belongs in this category, nothing sells it yet, so the member is correctly
  never set. `ForceCostTest.aDeclaredPoorCrewIsNotChargedAgain` fails if anyone wires the obvious
  thing.

  **Still open, and now cheap:** `RACK_MINES` has a member and no filler — it wants the mine
  rack inventory from the M9.0 entry above, plus M9.17's scenario waiver. `CREW_QUALITY` and
  `LEGENDARY_OFFICERS` want a purchase path (a `ShipSpec`/fleet-builder field), not a derivation.

  **A divergence found while reading S2.14, NOT fixed and not a bug today.** S2.14 also says the
  GABPV does not include "Fighters, PFs, SWACS", because S2.21 scores them as units in their own
  right — "any units (fighters, MRS shuttles, extra marines, etc.) on a ship which is destroyed
  count as destroyed as a result of enemy action". `Game.calculateVictoryPoints` instead folds
  each ship's fighter BPV into that ship's GABPV, and gives fighters no scoreboard row of their
  own. So it is one row per ship by design, not a double count, and the totals are close to
  right. Scoring fighters separately is a real feature (rows, statuses, who shot which fighter)
  and belongs in its own slice; until then the comment in that method should not be read as
  claiming S2.14 compliance.

  **The original design note, which was sound where it was not superseded:**

  **The category.** S3.21 names the pattern for Annex #6A items: they "could be purchased as
  Commander's options" but "are normally purchased as part of the overall force structure of a
  patrol scenario and **are not within the percentage limits given here**." S3.211 then defines
  the basis the percentage is taken from — "the ship, its refits, and its fighters, **but not
  including the cost of mandatory drone speed upgrades or crew quality adjustments**" — so these
  items are excluded TWICE: not in the basis, not in the cap. SH.432 says it again for drone
  speed, and M9.17 for mines: "the cost of mines in mine racks is part of the overall force
  total, not a part of the cost of the minesweeper."

  **Three members as first identified** (a fourth, Legendary Officers, was missed; and the
  first of these turned out to be built already — see above):
  1. **Rack mines** (M9.17, M6.31). Annex #6 costs: a transporter bomb **4**, a nuclear space
     mine (minelayers only) **8**, one NSM on a Romulan M2.73/.74/.76 ship **8**, a PA mine
     **4**, a Y-transporter bomb **3**, a Y-NSM **7**. M6.31's cheaper chart is for mines ALREADY
     PLACED on the map before the scenario — small explosive 1, large 3, small captor 3, large
     captor 6, +1 for a chain, deadman or command detonator — and it says outright that mines
     carried on ships "cost considerably more". Same NSM: 3 on the board, 8 in a rack.
  2. **Drone speed upgrades** (FD2.223 medium, ½ point per drone; FD2.224 fast, 1 point per
     drone; FD2.226 halves it for type-VI). See the drone-cost entry elsewhere in this file.
  3. **Crew quality adjustments** (G21.0), named by S3.211 alongside drone speed. The declaration
     exists on `ShipSpec`; the costs do not.

  **The code is already the right shape, and needs ONE new figure.** — SUPERSEDED: it needed a
  figure PER MEMBER plus S2.14's flag, for the reason given above. The rest of this paragraph is
  accurate and is what the build followed. `FleetValidator.totalCost`
  is hulls + carried fighters + every ship's `getCoiSpend()`, and `checkBudget` holds it to the
  agreed budget (S8.11) — its javadoc already says "option points come out of the agreed total
  rather than on top of it". Separately, `checkCommanderOptions` caps each ship's `coiSpend`
  against `CoiBudget.allowanceFor`, whose basis is `effectiveAdjustedCombatBpv` =
  `getEffectiveBpv() + carriedFighterBpv`. So:
  - add a SECOND spend on `Ship` — summed into `totalCost`, ignored by `checkCommanderOptions`;
  - leave `effectiveAdjustedCombatBpv` alone, which already excludes the category by omission,
    exactly as S3.211 requires.

  **Do NOT fold these into the ship's BPV.** It looks equivalent — the force total comes out the
  same — and it is not. That figure is the basis for the ship's OWN 20%, `CoiBudget`'s
  `D_PERCENT_BONUS` takes S3.223's extra tenth from it, and S2.2 victory points read BPV. Adding
  24 points of NSMs to the large Q-ship's 75 would quietly grant it about five more points of
  option budget and change what destroying it is worth.

  **Two clauses to carry across when building it.** M9.17 lets a scenario waive the mine cost
  entirely ("perhaps where a minesweeper or minelayer is surprised by enemy units while going
  about its normal business"). And a single carried NSM sometimes IS in the BPV — M2.71 for the
  old Romulan sublight ships and M2.75 for the SMALL Q-ship — so the rule is not "Romulan hulls
  include their mines" but "rack mines are never included, while one bundled NSM on certain hulls
  is".

- **R1.7 acceleration limits — BUILT 2026-10-19.** Kept here only because this entry was wrong
  in a way worth not repeating: it said "every freighter and Q-ship sheet carries the same line".
  They do not, and that error survived into the first implementation.

  The sheets state the limit in **two forms**, and only one is C2.21's shape. A Q-ship reads "no
  more than five movement points **or double its current speed**. It can disengage by
  acceleration." A plain freighter reads "cannot accelerate by more than 3 movement points per
  turn. This ship **cannot** disengage by acceleration." So a freighter has a HARD cap with no
  doubling arm, and no C7.1 at all — two rules, not one rule with two numbers. Reading the
  Q-ship sentence onto every sheet let an F-S at speed 20 reach 31 where it may reach 23. Owner
  caught it from the Basic Set SSD book, page 3, within the hour.

  Three facts in `auxiliary`, all three defaulting to permissive: `maxAccel` (5 on all thirteen
  Q-ships, 4 and 3 on the two plain freighters), `accelDoubling` and `disengageByAccel`. The two
  booleans are SEPARATE, and the ARMED FREIGHTERS prove they had to be — owner, from the sheets,
  2026-10-19: every armed freighter says "it can disengage by acceleration", and the large one
  also says "can accelerate by 5 movement points per turn" with no doubling clause. Three
  combinations across one family: plain freighter hard-capped with no C7.1, **armed freighter
  hard-capped WITH C7.1**, Q-ship doubling with C7.1. A single flag cannot express the middle
  row — which is how the distinction was lost the first time, when `slow: true` stood for the
  pairing, was read as a vaguer spelling of `maxAccel`, and was unified away in `e391c0ed`. Note
  the history: `slow` sat on the four large Q-SHIPS, which do double and can disengage, so the
  flag was already being applied to the wrong hulls.

  **The permissive default is the only thing holding thirteen hulls up**, since no Q-ship JSON
  says anything about either boolean. `AccelerationLimitTest.everyQShipDoublesAndMayDisengageByAcceleration`
  guards that by walking the library and counting to thirteen — owner's ruling, 2026-10-19: "All
  the Q-ships are secret military conversions. So they can all double and they can all disengage
  by acceleration." A hull copied from a freighter instead of a sibling Q-ship fails it by name.
- **The EM restriction set (C10.51/C10.52) — RE-AUDITED 2026-11-09, and now COMPLETE bar the
  systems that do not exist yet.** Built and cited: **C10.511** (cannot launch —
  `LaunchCoordinator.emLaunchBlock`, one helper for every launch path), **C10.512** (cannot guide
  seekers — on both `Ship` and `Fighter`, with `checkControlOverflow` sweeping a ship that starts
  EM while already guiding), **C10.514** (mines, in `MineResolver`), **C10.516** (the carve-out
  that a fighter under EM CAN use chaff), **C10.521** for scout channels
  (`Ship.scoutChannelBlockReason`), labs (`SeekerControl`), transporters (four doors in
  `BoardingResolver`), tractor beams (two in `TractorResolver`, with C10.525's negative-energy
  carve-out), ESGs and passive fire control, and **C10.522** (EW lending).

  *(Audit note: grepping `C10.521` finds little and makes the systems list look unbuilt. Several
  guards cite the parent `C10.52`. Grep both.)*

  **This entry was wrong in three places when re-read on 2026-11-09**, which is the real lesson
  in it. It said transporters and tractor beams had "no EM guard at all" — both were guarded, in
  five places between them. It said C10.415/D19.25 had "zero citations" — built, in
  `DamageResolver` and `Ship`. And it said ESGs "cost nothing yet" because they were unbuilt —
  ESGs then landed on 26 hulls, and that sentence turned from true into the only live gap in the
  list without anyone touching the file. **An audit is a claim with a date on it. Re-grep before
  working from one.**

  **ESGs (C10.521, G23.313/G23.314) — BUILT 2026-11-09, and NOT as a guard.** This is the part
  worth reading before touching it, because `emActionBlock` is the wrong shape for all three of
  its behaviours:
  - G23.314 says the field "CAN be announced while under EM", so an announcement must not be
    refused;
  - an ACTIVE field is **dropped involuntarily** when EM comes into force, announced at that
    moment (G23.313) — so the hook is in Stage 6E beside `applyEmAnnouncement`, not on the next
    impulse's ESG sweep;
  - an announcement that **matures** while EM is still running is **voided and its energy lost**
    — `ESG.voidOnEm`, which is not `cancelAnnouncement` (G23.33's voluntary withdrawal keeps the
    energy; this is the penalty for leaving it too late). The energy goes even with a capacitor:
    G23.242 lets a capacitor keep the remainder of a RELEASE, and a void is not a release.

  `EsgErraticManeuversTest` has 7 cases, three guards proven by mutation. One of them passed on
  the unfixed code until it was strengthened — `canAnnounce()` is also false while a field is up,
  so the test had to assert the field was DOWN first for the refusal to mean the lockout.

  **C10.522 (EW lending) — BUILT 2026-11-09.** "A unit using EM cannot lend EW points. A unit can
  receive lent EW while performing EM." Both halves matter and the second is the trap: an
  over-broad "no EW dealings under EM" would break a legal play — a fighter jinking while its
  squadron-mate feeds it ECM. C10.523 narrows the first sentence for this craft specifically, an
  EW fighter (R1.F7) under EM having no scout capability though it keeps active fire control.

  Two checks, because the two lending paths share no code: `SquadronEwResolver.refusalReason` for
  an EW fighter lending to squadron-mates (J4.92) and `carrierRefusal` for a carrier lending to
  its squadron (J4.93). No web work — `ewLendRefusal` already carries whatever
  `Game.ewLendRefusalFor` returns, and the panel already renders it. `SquadronEwEmTest` has 6
  cases; 5 fail with the guards neutralised and the sixth is the exception clause, which must
  pass either way.

  **What is left is only what does not exist yet**: web generators, stasis field generators,
  maulers and web casters for normal web are unbuilt systems, so C10.521 costs them nothing
  today. **Whoever builds any of them owes it an EM check** — and should re-read this entry
  rather than trust it, given the above. C10.523's MRS and SWAC are likewise unbuilt (`SquadronEwResolver`
  says so where it refuses them as lenders), and C10.524's tactical-intelligence degradation
  waits on D17.

- **C10.415 / D19.25: EM negates passive fire control — BUILT.** Kept because the ANALYSIS
  below is still the right reading and the entry claimed for weeks that it was unbuilt with
  "zero citations" — there are citations in `DamageResolver` and `Ship`, and the firing case is
  handled. What remains unbuilt is D19.3's EARNED PFC bonus, as the analysis already says.

  **The rule.** C10.415: "Using EM will negate the effects of passive fire control (D19.25) and
  prevent its use. If the fire control is not active, it would be considered **inactive**
  (D6.614) rather than **passive**." D19.25 adds: a ship without active FC conducting EM "gains
  no benefits from PFC (D19.3) and cannot fire or launch any weapons while using PFC", the time
  spent on EM does not count toward earning PFC benefits (D19.31), and any use of EM cancels the
  PFC bonus so it must be earned again afterwards.

  **What is reachable here is FIRING, and only firing.** Our model has no PASSIVE/INACTIVE
  distinction: fire control is the boolean `Ship.activeFireControl`, so "passive" is simply
  `!activeFireControl`. What passive FC currently BUYS a ship is three things — firing at range
  5 or less (`DamageResolver`, D19.23), launching self-guiding drones only (D19.221/D19.22), and
  holding a tractor or firing ballistically (D19.224). Of those, launching is already barred
  under EM by C10.511, so the live gap is that **a ship conducting EM with no active fire
  control can still fire at range ≤5 and should not be able to fire at all.**

  **D19.3's earned PFC bonus is not implemented** (no `D19.3` or `D19.31` citation anywhere), so
  the two clauses about accruing and losing it are moot until it is.

  **Design note: this one is NOT a guard.** `Game.emActionBlock` is the wrong shape for it,
  because passive fire control is a MODE rather than an action. It wants the C10.512 treatment —
  express the rule as the capability yielding nothing, the way `getControlCapacity()` returns 0
  under EM — so that every consumer of passive FC is covered at once rather than the fire path
  alone. Probably a single predicate that answers "is this ship on passive fire control?" with
  false while it is conducting EM, because the ship is INACTIVE instead.
- **FD4.4 Orion rack-firing cap — BUILT 2026-10-20.** Kept as a pointer because the rule is
  easy to restate wrongly: it caps RACKS USED in a turn, not drones launched. A rack that fires
  twice spends one of the three, so three type-C racks lawfully put six drones up — which is why
  Orions favour them and why the SAL's Y175 refit converts A→C. It buys rate; FD4.4 governs
  permission. Drone and plasma-D racks share the one allowance ("three total, not three of
  each"); anti-drones and seeking shuttles are outside the count.

  `auxiliary.rackFiringLimit` (absent = no limit beyond each rack's own rate),
  `Ship.mayFireAnotherRack`, and `LaunchCoordinator.rackLimitBlock` at all four launch doors.
  OAKDISC clears it to null, which `setFields` already supported. Declared so far on the Orion
  SAL only — **the other Orion hulls still need it**, and until they have it they fire every
  rack.

- **G24.1343, phaser downfire to avoid blinding** — unbuilt, and the next scout slice.
  **G24.23, attracting drones, is BUILT** and was still listed here as the thing to do next:
  G24.231's draw, G24.234's range band and G24.235's ask-the-owner round all exist, with
  `channelAttractedDrone` on the DTO and a pending-answer flow in Game. See also
  `project_phaser_downfire`: the rule is E2.25's LOW-POWER clause, not E1.x, and `shotModes`
  already exist, so it is cheaper than it looks.
- **G21.1/G21.2 crew quality adjustments.** The declaration is built and `crewQuality` is on
  `ShipSpec`; the adjustment lists are not. The ±3 natural EW is the first slice.

## Ship explosions (D5.0) — destruction in combat BUILT; self-destruction BUILT but with no UI; (D5.31) a decided non-goal

A destroyed ship blows up and damages what is near it. Raised by the owner on 2026-11-05 off
Annex #1's unread explosion-strength column; the data landed the same day and the mechanism on
2026-11-06.

**BUILT (2026-11-06), core only.** `ExplosionResolver`: (D5.12) a ship destroyed in combat
explodes immediately, (D5.13) who explodes and who does not, (D5.41) the strength-dependent
radius and the facing shield, (D5.42) cloaking ignored, and (D5.32) a ship killed in another hex
exploding in its own right. `ShipExplosionTest` covers it, with the Federation FFG (9) and the
Klingon F5 (10) as the fixture because they straddle the threshold in real chart data.

**Why the owner wanted it:** "Ship explosions can be a big deal in large fleet battles. I've
focused fire on a small ship in order to destroy nearby drones and fighters with the explosion."
That tactic is the reason the blast reaches seekers and shuttles and not only ships.

**The trigger is `Game.cleanupDestroyedShips`,** which explodes a wreck and then removes it, in
that order, because the blast needs the wreck on the map to give each victim a bearing to it
(D5.41). That ordering is also what stops a double explosion, and a flag guarding the same thing
was deleted when mutation testing showed no test could fail with it ignored.

**The blast application is SHARED with mines.** `ExplosionResolver.applyBlast` is the mine
detonation loop with the radius as a parameter; `MineResolver` now calls it with radius one.
(G13.552) stayed behind at the mine, because the flash-cube exposure of a cloaked ship is
written for mine explosions specifically and (D5.42) says nothing about it.

**The one trap, if you touch this.** `markShieldDamage` QUEUES bleed-through rather than
applying it, so a blast kills nobody by the time it returns — the deaths happen when the queue
is next drained. `DamageResolver.resolveInternalDamage` therefore loops: drain, explode, and go
round again if the explosions queued anything. Remove that loop and an explosion's internal
damage silently waits for the next volley.

**The decided non-goal, and then the real gaps:**

**(D5.31) WILL NOT BE BUILT — owner's ruling, 2026-11-06.** Two ships destroyed in one hex are,
by the rule, one combined explosion of their summed strength; we resolve them as two, which
over-damages slightly. "Very edge case, and involves somehow waiting to see if other ships are
destroyed." That second half is the real cost: combining means knowing the whole wave of
destructions BEFORE resolving any of them, and bleed-through is QUEUED rather than applied — so
the ships a blast kills are not destroyed until the queue drains, after the blast that killed
them has been resolved. Honouring it would mean holding every explosion back to the end of 6D4.
`ShipExplosionTest.twoWrecksInOneHexExplodeSeparatelyByDesign` pins the behaviour so it is not
later read as a bug. Do not half-fix this.

**Genuinely still open:**
- **(D5.2) docked units add their strength** to the exploding unit, unless they escape (D21.5),
  and a base the ship is docked TO does not add. Needs the docking model.
- **(D5.1)/(D5.5) self-destruction — BUILT in core and server 2026-11-06, NO UI YET.**
  `Game.declareSelfDestruct` takes the declaration during the allocation
  window and files an EMPTY Energy Allocation Form for the ship, because (D5.1) says it makes
  no entries and can do nothing else — without that the turn stalls waiting for a form the
  rule forbids. Resolution is at the top of `beginImpulses`, which IS Annex #2
  phase 3. `Game.selfDestructRefusal` holds (D5.51): legal only at two crew
  units or fewer, or when out-boarded, or as the last ship of its side, or for an Orion
  (G15.1). The server action is `SELF_DESTRUCT` and deliberately validates
  nothing beyond finding the ship — a second copy of the gate is how the pre-G7.6 tractor cap
  drifted. `SelfDestructionTest`, 9 tests.

  **What is left.** The web has no button, so this is reachable only over the API. It belongs
  in `EnergyAllocationDialog` as a per-ship control sending `SELF_DESTRUCT`
  instead of `ALLOCATE`, with a confirmation, since it is irreversible.
  **The DTO cannot currently say whether it is legal**: `selfDestructRefusal` is
  core-side only, so either the UI offers the button and surfaces the refusal, or a
  `canSelfDestruct` flag goes on the ship DTO. That dialog is 1,636 lines with
  no tests on any path, so take characterisation seriously.

  **Still unbuilt, and both only ever PREVENT a declared destruction:** (D5.11) interruption
  by mutiny (G6.41, unbuilt) or by enemy boarding parties (D7.7), and (C13.4812), under which
  an internally docked ship cannot self-destruct at all. A ship that declares today always
  goes up.
- **(D5.55) terrain**: planets block explosions (P2.547), webs limit them (G10.72).
- **(D5.14)** a separated section deducts its explosion strength.
- **Server and web**: nothing was needed — the blast reports through the existing internal
  damage log, which already reaches the client. A ship's explosion strength is NOT shown
  anywhere in the UI; the SSD panel would be the place if it is wanted.

**The rule, read rather than remembered.** (D5.12): when a ship is destroyed in combat it
explodes IMMEDIATELY, by the self-destruction procedure, and the strength is the chart's, "not
modified by the existence or non-existence on the unit of any unexpended weapons (including
mines or drones)" — so a magazine full of drones adds nothing. (D5.2) gives the two triggers:
self-destruction, or "an excess damage hit after all excess damage boxes are destroyed".

**(D5.41) is the whole geometry, and it is smaller than a mine's.** Basic explosion strength
(BES) of nine or less affects ONLY the exploding unit's hex. Ten or more affects that hex and
the six around it — radius one, never further, however big the ship. The BES is applied whole to
every unit in the zone, "on the shield facing the exploding unit", so this needs a bearing per
victim and not a flat hit. (D5.42): cloaking is ignored in both directions.

**(D5.13) says who does NOT explode**, and it is most of what we have built lately: fighters,
seeking weapons, mines and small ground bases (P2.76) do not explode when destroyed. Ships, PFs
and bases do. See [[feedback_ship_vs_unit]] — this is a rule that says "unit" and then excludes
the units.

**Chain reactions are specified and are not symmetric.** (D5.31): a ship in the SAME hex that
the explosion destroys adds its BES to the original and the two are treated as ONE combined
explosion, because they went up together — so the combined strength can cross the 9/10 radius
boundary. (D5.32): a ship in a DIFFERENT hex that is destroyed counts as a separate explosion.
Docked units add their strength (D5.2) unless they escaped (D21.5), but a ship docked TO A BASE
does not add the base's.

**What already exists, checked 2026-11-05.** The trigger point is built: excess damage runs out
in `Ship.java` around line 4259 ("excess damage — SHIP DESTROYED") and again at 4302 when every
system including excess is gone (C3.14), and `BattleStatus` already distinguishes destruction by
excess damage. `tables.excess` is the box count and is in every ship file. So this hooks into a
place that exists rather than needing a new one.

**THE NUMBER NOW EXISTS. 355 of 356 hulls carry one** (2026-11-05), read off Annex #1's "D5.2"
column in `data/rules/Master_Annex_File.pdf`, which IS Module G3's annexes — no other book was
needed. `ShipSpec.explosionStrength` is an `Integer` and `ExplosionStrengthDataTest` now demands
one from every hull, with a parked list of exactly one entry: the Hydran GEN-F, which the chart
does not list apart from the GEN.

**Read by geometry, not by eye.** The chart pages are text, not scans, but rotated 90 degrees,
so `pdftotext -layout` pairs each ship's label with a NEIGHBOUR's numbers — which looks like
data rather than like an error, and is the trap in this file. Poppler's `-bbox-layout` gives
word coordinates; a printed row is then a column of constant x and a printed column a band of y,
and the header column (anchored on its own "D5.2" entry) gives the bands. The scripts are gone
but the method is the point, and it applies to every other column in the annex.

**What made it trustworthy was pulling the BPV column alongside** and comparing it to each ship
file's own `bpv`. 291 of 313 agreed exactly; the disagreements are listed below. That check
caught a real error in its first run: the annex's key says **"Split BPVs are read
economic/combat"**, so a chart cell of `108/98` is `bpv 98, epv 108` — the opposite of the
obvious reading, and the FWV had been entered backwards an hour earlier.

**Three cross-checks on the column itself**, since a BPV match proves the ROW and not the
column: mean strength by size class came out 29.1 / 17.2 / 9.5 for classes 2 / 3 / 4 across
eleven separate faction charts; the Orions sit above everyone despite small hulls, which is
(D5.2) saying their nuclear suicide bombs are included; and a Lyran page nobody had looked at
matched the written values ship for ship.

**Where the odd ones came from.** Bases take 11 from the GENERAL BASES chart, which prints
"11+" — the "+" is (D5.2)'s own arithmetic, a docked module adding ITS strength, so 11 is the
base's own number. Q-ships come from a table indexed by EMPIRE NAME rather than type code
(page 17). Freighters share rows by class (F-AL covers all four armed-large variants). Ground
bases are listed at ZERO, which is the chart agreeing with (D5.13) in the data — and the reason
the guard's floor is 0 rather than 1.

**The BPV disagreements, all resolved** (2026-11-06). Of the 21 hulls whose `bpv` did
not match the chart, **sixteen were never wrong**: each is our unrefitted hull plus the exact sum
of its own declared refit costs, so the chart lists the fully-refitted ship while we hold the base
hull and the refits as data (S3.24). All sixteen are ISC, which is just the empire whose hulls
carry the most standard refits — ISC CA 142 + 43 = 185, CC 198 + 22 = 220, DN 264 + 16 = 280, and
so on without a single point left over. **Do not "fix" these.** Any future BPV check against Annex
#1 must add the declared refits before comparing, or it will report the whole ISC list every time.

**One was a real error and is fixed**: the Orion BS had 200/75 entered as bpv/epv. The annex key
reads *"Split BPVs are economic/combat"*, and the owner's gloss (2026-11-06) gives the reason —
"a ship with special equipment replacing weapons is still just as expensive to build as the
original ship (EPV) but isn't as effective in combat (BPV)" — so **the larger number is the EPV**.
The Orion freighters show it plainly: F-L 61/18, worth carrying and not worth fighting. Every
other split Orion hull was already right, which is what made this one findable.
`RefitRaisesBothPointValuesTest.theEconomicRatingIsNeverBelowTheCombatOne` now sweeps the library
for it, and catches a base's variants along with the base. Note the Andromedans invert this
(BS 200/400) — out of scope, but do not take them as a counter-example to the rule.

**The remaining four were ours being wrong, and the owner has fixed all four** (2026-11-06):
Klingon D7C 136 → 139, Kzinti BC 135 → 128, Lyran CA 131 → 133, Romulan SNE 68 → 78. The BC was
the one caught from the side, by the sample builder in `KzintiShips` having said 128 all along —
two sources against the file. The owner's verdict on the rest: "All of your reads on BPV were
correct."

**The cross-check now comes out clean: 308 hulls, 292 exact and 16 explained by declared
refits, nothing unexplained.** Worth re-running after any data pass; the method is in the
geometry note above, and remember to add the refits before calling anything a mismatch.

Two tests pinned the old prices and moved with them: `CarrierPriceByYearTest` (the BC, twice)
and `UimAvailabilityTest` (the D7C, three times). The D7C's base is now a named constant there,
because the failure read "expected 131 but was 134" and named neither the hull nor the reason —
those tests are about the year-gated adjustments, not the price.

**Also unverified, for a different reason**: the Federation BS (107) and Hydran BS (114) against
the GENERAL BASES row's 120. Base explosion strengths came off the general chart rather than a
faction chart, so no BPV cross-check ever ran on them — and there is only one generic BS row in
the annex, so the annex does not contradict them either. Per-faction base BPVs legitimately
differ; these two are probably from their R-sections and probably fine.

**Also unresolved, from the original reading:** docked ships are partly shielded from an
explosion and may partly block one (C13.52/.66/.74), which belongs with the (D5.2) docking work
above.

## Built, never seen in a browser

Complete in core, server and web, and never played through. Each wants a two-browser session
(Chrome + Edge) more than it wants code.

**Ship explosions (D5.0) came OFF this list on 2026-11-06**, verified in a live two-browser
game within hours of being built. The owner's log, kept because it exercises the threshold
case: a strength-10 hull (an F5-family frigate, the lowest value that reaches beyond its own
hex) exploded at <3|3>, put 10 on the facing shield of a ship one hex away, and killed six
drones. One point lower and it would have touched nothing outside its hex — so the one live
test happened to land on the only value where (D5.41) being wrong would be invisible.

- **Aegis fire control (D13)** — complete all tiers 2026-10-07.
- **The fire declaration round** — all three phases plus the multi-ship EW fix.
- **Hangar Ops and the COI screen** — unverified since `ea8e82d` changed the DTO to send
  catalogue keys plus `designation`.

## On the wire but unshown

The cheapest category in the project, and the one that keeps producing real bugs. The fact is
already in the DTO; only the display is missing. Check this shape first whenever something looks
absent from the UI — the DAC arming display turned out to be exactly this.

- ~~**Launch buttons are not disabled under EM.**~~ **FALSE as of 2026-11-09, and built better
  than this asked for.** `LaunchOrdersPad.emBlocked` gates every launch control and prints a
  banner naming C10.511 and the craft it covers. It reads the server's `emEffective`, NOT
  `usingEm` as this bullet suggested — C10.24 says a tractor SUSPENDS EM without cancelling it,
  so a held ship is still "using EM" and may still launch, and deriving that in the view would
  be the client deciding a rule. The fourth claim in this file to be found false by grepping it.
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

**Ready to enter — nothing new needed: TEN LEFT, not seventeen** (re-surveyed 2026-11-06).
The list was checked hull by hull against `data/factions/` and five of the original seventeen
were ALREADY ENTERED: Klingon D7E, D7V, D6E, D5N and AD6. Two more went in on 2026-11-06, the
D5I and the D5F. Re-check before quoting this list again — a survey ages.

**Still missing (10):** Klingon RKL Sparrowhawk p22, F6 battle
frigate p23, E5 battle escort p24, C8S space control ship p25, E3D drone escort p34; Hydran SR
Outrider p36 and LP Lord Paladin p56; Lyran SCS Siberian Lion p62, SR Prairie Cat p65, MP
military police p77. Page numbers are the PRINTED page; add 1 for the PDF page.

**The D5N's flag bridge is settled**, so nobody needs to re-open it: that hull is in, and its
file records the finding. (G6.12) names the diplomatic ships as the Klingon exception that HAS
a flag bridge, but neither the D5N's sheet nor the D7N's draws a flag box — the boom runs
BRIDGE, EMER, SCTY and stops. So no flag is declared and a flag hit converts to a security
station like any other Klingon ship.

**What the D5I cost, as a measure for the rest:** one page render, about twenty minutes, and
the hull is 95% its parent (the D5). The 5% is the whole job — the ISF fit has phaser-2s
forward where the D5 has phaser-1s, disruptors of range 22 where the D5 has 30, and NO
DERFACS. All three are what a copy of the parent gets wrong, and two of them were caught by
the owner reading the sheet alongside rather than by the draft. `KlingonD5iTest`
pins them. **Enter these by diffing against the parent hull, then read the sheet for what the
diff missed.**
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

## Advanced Missions SSD book — what is still in it

Contents list read 2026-10-12. **144 SSDs**, printed pages 2-144; PDF page = printed + 1. Grouped
as general units (2-32), then Federation 33-50, Klingon 51-72, Romulan 73-100, Kzinti 101-112,
Gorn 113-124, Tholian 125-138, Orion 139-144.

**A full per-ship diff has NOT been done** — unlike the Module R3 survey above, which was done
ship by ship. What follows is what was actually checked against `data/factions/`, so treat the rest
of the book as unsurveyed rather than as present.

**CORRECTION 2026-10-12: there is no missing drone ground base.** This entry previously claimed the
book had a DRONE planetary defence fit we had skipped, and that our phaser-1 "Ground Battery" stood
in its place. Reading the page settled it: printed p12 is titled "R1.14 & R1.15 DRONE PLANETARY
DEFENSE SYSTEM" and carries TWO SSDs. The R1.14 half is `GBD1`, the ground-based phaser-1 — four
phaser-3s and a phaser-1, BPV 8 — which is exactly the fit we already have. The contents line covers
both halves, and reading it as one fit is what produced the phantom gap. **Our nine ground bases are
the book's nine.**

**What IS missing from that page is R1.15 DEFENSE SATELLITES** (`DEFSAT`, point value 20, size class
7), which are a different thing entirely: five free-standing satellites in high and low orbit, each
with a PH-2, a drone rack and a PH-3 and its own damage track — not a base on a planet face. Sheet
notes: all weapons 360°, "drone racks cannot be reloaded during a scenario", and all drones are
type-I at current speed with no special warheads. Used by the Federation, Klingons, WYNs and
Kzintis. Satellites were already scoped as "last or never"; this does not change that, but it is at
least the right feature to defer.

Also on that sheet, and still open: the SMALL GROUND BASE energy line — shields 1/2+1/2, life
support 0, fire control 0.

**DONE 2026-10-12: all six Q-ships (R1.7, pp27-32)** — Kzinti, Romulan and Tholian, large and
small. They really are one converted freighter twice over: every large hull is shields 20, chull 6,
cargo 20, armour 7, warp 4+4, impulse 2, APR 10, battery 3, and every small one is shields 10,
chull 4, cargo 10, armour 3, warp 2+2, impulse 1, APR 4, battery 3. Only the cargo differs.

Three things the set turned up, all recorded where they belong: every Q-ship now breaks down on 2
(three said 1), the acceleration limit is one dead key rather than two, and mine racks got their
rules read (M9.0).

**Still open and NOT modelled from these sheets:** the Romulan **sabot refit** (Y180, +2 large and
+1 small, speed-40 torpedoes under FP11.0 in Module R10 — a module we do not have); the Tholian
**snare refit** (Y183, +6 large and +3 small, letting web generators operate as web snares under
E13.3 in Module C2 — likewise); the note on both Tholian sheets that **web generators are destroyed
on FLAG hits**; and the Romulan large Q-ship's **mine racks**, parked as `auxiliary.mineRacks`.

(Separately, the Hydrans have `S-Q` but no large Q-ship, which is not from this book.)

**The Romulan K9R, conjecturally (R4.40, p97).** We have `K9RB` and no `K9R`. The owner's ruling
2026-10-12, which is the reason and should not be re-litigated: "the K9R was transferred to the
Romulans after the B refit had already been applied, thus the Romulans never utilized the K9R
(pre-refit). However, if — for completeness — we want to retrofit the ship file to allow both the
conjectural K9R and the K9RB I wouldn't object." So it is optional completeness, not a gap: a
declared refit switch on the existing file, never a second hull.

**Large blocked groups, same blockers as the R3 survey.** Tugs, pods and pallets are everywhere in
this book — Federation carrier and starliner pods (41-42, 50), Klingon pods and five tug variants
(52-58), Kzinti pods and transport tugs (103-106), Gorn fleet tugs and pods (113-115, 123-124) — and
all of it waits on G14.0. Minesweepers across six empires wait on M8.0. The Klingon D6M p70, Romulan
Falcon Mauler p75 and KRM Mauler Cruiser p92 wait on E8.0 below. The Klingon D7A p51 and D5A p64
wait on G16.0 stasis, which is deferred very late or never. The monitors (R1.22, pp25-26) and the
eight armed freighters (R1.20/R1.21, pp17-24) are unblocked but unsurveyed.

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
covering the rest.

**Two separate things, and an earlier draft of this entry ran them together** (caught by the owner
2026-10-12, asking whether small bases always have one shield — they do):

1. **SHIELD COUNT is fixed by size.** Small and medium: exactly ONE shield, "covering all arcs, as
   part of their design", always, wherever it sits. Large: the P2.731 rules above. Nothing about
   placement changes a small base's shield count.
2. **EXPOSURE ARC is fixed by siting** (P2.721, P2.747) — the arc into which it can fire AND from
   which it can be hit:

| siting | arc |
|---|---|
| planet surface | 180°, which is how R1.14 designs them (P2.721) |
| large asteroid, centred | **360°** |
| large asteroid, on a hex side | 180° |

So the same `GBD1` is a 180° unit on a planet and a 360° one on an asteroid, while carrying one
shield in every case. The ship FILE says "one shield"; the DEPLOYMENT says which way it faces and
how wide. Keep those as two fields, or the asteroid case forces a second ship file for the same
base — the mistake [[project_ship_lines]] records for factions and [[project_fighter_lines]] for
Lyran fighters.

Large bases cannot be placed on large asteroids at all, "although of course they can be placed in
asteroid hexes" (P2.747).

**A base on a large asteroid is probably NOT restricted to ballistic attack** (owner, 2026-10-12).
P2.7 introduces its whole block with "In the case of bases on planets, certain modifications
apply", and P2.713's ballistic route, four-hex limit and no-ECM/WW clause are among them — so an
asteroid-sited base falls outside and should be attackable by ordinary seeking weapons like any base
in space. Two supports: P2.713 says seekers "CAN be fired… using ballistic targeting", granting a
route rather than requiring one, and P2.734 bothers to say ground bases can be hit by warp-seeking
type-VI drones "as any base in space could", which only needs saying because ordinary seekers cannot
reach a planet-sited one.

CAVEAT, so this is not mistaken for a quotation: P2.747 itself sits inside the planet-scoped
section. The reading is the owner's judgement and a sensible one, not a line of text. Settle it from
P3.4 if that module is ever to hand.

Worth noting for the ballistic slice: P2.732 lets a ground base use a WILD WEASEL to distract
seeking weapons — "except those using ballistic targeting (P2.713)". We have wild weasels, so that
clause is nearly free, and it is the defender's answer to everything except the one attack that was
built for ground targets in the first place.

**Not a blocker for the first slice.** Ground bases on PLANETS need nothing from P3.4, which is
unbuilt and already on the smaller-gaps list. Asteroid siting is a later placement option that
arrives with large asteroids.

### Arcs need NO new machinery (checked 2026-10-12)

The owner's model — "a base on the B side of a planet should behave as if it were a ship pointing
in direction B" — is exactly right, and it already works:

- `ScenarioLoader` maps a heading letter to a direction with `(ch - 'A') * 4 + 1`, so A=1, B=5,
  C=9, D=13, E=17, F=21. Give the base facing 5 and it is a unit pointed at side B.
- Arcs are computed relative to the unit's own facing — `getRelativeBearing(trueBearing,
  attacker.getFacing())` then `ArcUtils.inArc` — so an `FH` weapon covers the forward hemisphere
  around whatever direction it faces. Nothing knows or needs to know that it is a base.
- The planet blocks the rest by itself. `Game.losBlocked` is P2.322, "planetary surface lies
  strictly between the two hexes", and the geometry falls out right: the base's OWN hex is an
  endpoint rather than strictly between, so it fires outward freely, while a line to anything on
  the far side crosses other surface hexes and is blocked.

Those three together produce P2.747's "the arc into which it can fire (and from which it can be
hit)" with no new code. So slice 4 is placement and immobility, not arcs.

### The rest

Siting on a planet hex with no movement, and the EW asymmetry already identified: P2.52 gives a
target ON a planet two points of ground-clutter ECM while **P2.524 denies it to planetary bases** —
trivial while they are separate objects, which is why merging them was rejected. Then P2.713's
four-hex ballistic limit wires the new target into the seeker work already done.

**Agreed slicing:** single-shield mode, then the chassis plus one fit (GBD1), targetable by direct
fire; then the remaining fits as data; then ballistic ground attack; satellites last or never. That
completes two of the three ground targets the ballistic entry lists, and gives planetary bombardment
a defender that shoots back.

**DONE: slices 1-4.** Single-shield mode (`373275de`), the chassis and GBD1 (`8b2fbbe7`), the other
eight fits (`3940fb1b`), and siting via `Game.placeGroundBase(base, planet, side)` (slice 4). A
ground base now stands on a planet face, looks outward, cannot move, fires into the hemisphere away
from the planet and is hidden from the far side — with no rule of its own for any of it.

### Slice 6, SHOWING A GROUND BASE — BUILT 2026-10-12

Owner's direction: "I don't want to crowd the planet. Maybe all we need is a tooltip that will show
the state of the bases. And if you click on a planet the side bar could show the details for all
bases on the planet? That way the main map stays clean." Agreed, and it sidesteps the shield-arc
question entirely — a base that is not drawn as a counter needs no arcs.

**What it must not do: nothing.** D17's intel levels list "size-5 ground bases can be distinguished
as to TYPE" at a high level, which implies presence is known before type is. So a face with a base
on it wants a small cue; what is on it is detail behind the tooltip and the sidebar. Hiding them
outright would be unfaithful as well as unplayable.

**And checked 2026-10-12: there is nothing to withhold.** The D17 level-L wording above reads like
a reason to hide a base's type until identified, which is what the first draft of this entry
assumed. It is not, for two reasons. **D17 tactical intelligence is not implemented at all** — one
comment in the whole source mentions it — and the `isIdentified` flag we do have lives on
ShuttleDto, SuicideShuttleDto, ScatterPackDto, DroneDto and PlasmaTorpedoDto, never on a ship.
Meanwhile `shipType` and `typeName` are classified SHIP_PUBLIC for every hull on the board, on the
stated grounds that a counter announces its class. A ground base IS a Ship, so it announces
"Ground-Based Photon" exactly as a D7C announces itself: consistent rather than a leak. Build the
display with no special secrecy, and revisit only if D17 is ever implemented — at which point it is
one rule for every unit, not a ground-base quirk.

**Four pieces.**
1. **The DTO stops assuming six shields.** `for (int s = 1; s <= 6; s++)` must loop over
   `getShieldCount()`, or a ground base reports one shield of 12 and FIVE AT ZERO — which reads as
   a ship that has had five blown off. This is slice 1's "six is hardcoded" assumption again, on
   the serialisation side, and it is the one piece that is core rather than client.
2. **Terrain becomes selectable.** `HexGrid` currently filters it out of selection outright
   (`o.type !== 'TERRAIN'`), so clicking a planet does nothing today.
3. **Ground bases are suppressed as individual counters** and aggregated onto their planet. They
   arrive in `mapObjects` as ordinary SHIP objects because that is what they are, so the client
   needs something to recognise them by — `isBase` plus the `GRBASE` line, or a flag of their own.
4. **A sidebar panel for the selected planet**, listing bases by face with their state, and a
   tooltip summarising. Privacy comes free: they are Ships, so `redactForEnemy` already covers them
   and an enemy sees what he would see of any ship.

Draw order already favours this — `HexGrid` paints `[...terrain, ...ships, ...]`, so anything drawn
for a base lands on top of the planet rather than under it.

**DONE, and piece 1 was a real bug rather than a tidy-up.** The DTO serialised six shields for every
unit, so a ground base arrived as one shield of twelve AND FIVE AT ZERO — which on a panel reads as
a ship that has had five of its shields blown off. It loops `getShieldCount()` now, and
`GroundBasePlacementTest` pins both halves: one shield for a base, six for a cruiser.

The other three went in as designed. Ground bases are no longer drawn as counters — six of them on a
radius-zero planet would bury it — and what the map gets instead is one small marker per occupied
planet hex, carrying a count when a hex holds more than one. They stay fully present everywhere
else: the hex picker names them "Ground base B: Ground Battery", the hover tooltip gives the face
and their state, and the launch pad lists them as targets.

The tooltip swaps facing and speed for the FACE, and drops the "Moves:" line. A base looks out from
its face by definition (P2.62) and never moves, so a heading and a speed of zero only invite the
reader to wonder when it will. It keeps everything else — shields, damage — because the owner's
whole requirement for this slice was "a tooltip that will show the state of the bases", and an early
return that dropped them was caught before it shipped.

**No separate sidebar panel was needed.** The design called for one listing every base on a selected
planet; the hex picker already does that job, because clicking a hex with several units in it has
always offered a chooser, and a ground base is an ordinary unit to it. Building a second surface
would have duplicated it.

### Slice 5a, BALLISTIC TARGETING (F4.0) — BUILT IN CORE 2026-10-12

Worth building for its own sake, not just for ground bases (owner, 2026-10-12): it is what
`Game.clearAsteroidPath` has always implied, and it is the prerequisite for scatter-packs, whose
`ScatterPack.isReadyToRelease` fires on a flat eight-impulse timer where F4.41 wants release on an
acceptable target entering a pre-set range.

**The rule, in full.** F4.11: designate a HEX, not a unit; the weapon pursues it by the most direct
route (F2.2) and on reaching it EVADES it (C1.3223), holding as nearly as possible to the course
opposite its approach — and if it enters a THIRD hex adjacent to the target without ever entering
the target, it begins evading from there, which is the anti-loop clause. F4.12: released from
control the instant it launches and never counts against the control limit, the one exception being
a scatter-pack's submunitions, which must be guided at the release point or go inert (FD1.7). F4.13:
one hex, never a sequence. F4.14: normal range, except P2.713. F4.21: it NORMALLY NEVER EXPLODES —
the uses are detecting a minefield, clearing asteroids, saturating defences and deception. F4.22 is
the hook for ground attack: a seeker fired at targets on a planet explodes against them only if it
satisfies P2.713. FP4.23: a ballistically launched plasma torpedo cannot acquire its own target.

**THE DESIGN SEAM, which is the thing worth having decided.** `Seeker` exposes `setTarget(Unit)` and
`getTarget()`, and `SeekerMover` calls `getTarget()` at EIGHT sites across its 638 lines — once per
seeker kind, drone, scatter-pack, suicide shuttle and plasma torpedo, each with its own movement
path. Do NOT branch on "is it ballistic" at those eight sites. Add a single `destination()` to
`Seeker` returning a `Location` — the target unit's hex, or the ballistic hex — and let the movers
steer by that. Only ARRIVAL and DETONATION then need to know which kind of seeker it is, which is
exactly where the rules differ.

Expect to touch all four seeker kinds, so do it in one session rather than leaving the mover half
migrated: a ballistic seeker that pursues but never evades, or an ordinary one that loses its target
mid-refactor, is worse than not starting.

**DONE, and the seam held.** `Seeker.destination()` is a default method: a pursuing weapon reports
its target's current hex, a ballistic one the fixed hex it was aimed at, and `SeekerMover` steers by
that through one new `steer()` helper. All four seeker kinds move correctly because none of them
knows the difference. `BallisticCourse` holds the F4.11 state — the target hex, whether the weapon
has started evading, and the SET of adjacent hexes entered (a set, not a tally: a weapon that
re-enters the same hex three times has not entered three of them).

Evasion is "hold the current facing", with the reasoning recorded at the site because the rule's
wording invites the opposite reading: "a course directly opposite its approach course" sounds like
turning round, but turning round would carry it back TOWARDS the hex it is supposed to be evading.
Flying straight on is the reciprocal of the approach AND opens the range every impulse, which is
what C1.3223 actually asks for. The mutation test is in `BallisticTargetingTest` — disable the
evasion branch and the drone orbits its hex at range 0-1 forever.

**One real bug surfaced on the way**, caught by a test written for F4.12: `placeLaunchedDrone` set
the launcher as controller unconditionally, so a weapon that is "released from control immediately
on launch" was handed straight back to its launcher's guidance. It now takes no controller and no
control channel.

**EVERY seeker kind now has a ballistic launch door** (owner's requirement, same day): a ship's
drone rack, a fighter's drone rail, a plasma tube, a plasma rack, a fighter's type-D rail, a pseudo
torpedo, and a suicide shuttle. They were not seven copies — each door's body now derives an `aim`
hex and an `aimName` from either a target unit or a course, so the arc checks, the facing and the
messages are shared and only the gates that genuinely differ were written twice.

Three things fell out of that:
- **FP4.23 has a home.** "A ballistically launched plasma torpedo cannot acquire its own target" is
  enforced by giving it no target and no controller, so there is nothing to re-acquire with. A
  plasma is otherwise self-guiding and would quietly have behaved like an ordinary one.
- **A fighter with no guidance equipment can launch ballistically.** J4.222 exists because the
  Kzinti AS cannot guide its own drones and must hand them to a carrier; a ballistic drone needs no
  guidance from anybody (F4.12), so the AS can throw one with no friendly unit involved at all.
- **A plasma RACK may fire ballistically in offensive mode only**, and that is a RULING rather than
  a quotation — flagged here because it should be checked. Defensive mode buys its rate of fire
  with FP10.212's restrictions on the target's size class and range; a hex has neither, so a
  defensive ballistic shot would take the rate without paying for it.

**What is NOT built yet, and should not be assumed:**
- **The SCATTER-PACK has no ballistic door**, deliberately. F4.41 releases its submunitions "when
  the first acceptable target is within their pre-set release range" and F4.413 says a hex cannot
  provoke that release, while `ScatterPack.isReadyToRelease` runs off a flat eight-impulse timer.
  F4.42's guidance handoff at the release point and F4.43's go-to-the-hex-and-stop are also
  unbuilt. Wiring a door without them would produce an SP that releases on a timer into empty
  space, which is worse than refusing. This is a feature, not a door.
- **No server action and no UI.** This is core only.
- **F4.3 type-III "wild boar" targeting** (FD5.25, D19.222) is untouched.
- **F4.5 tractors**: a tractored ballistic weapon should go inert, and a released one should have
  its target hex offset by however far it was dragged. Not implemented.

**Slice 5b, ballistic ground attack: BUILT IN CORE 2026-10-12.** P2.713's four sentences, wired
where each belongs — the four-hex limit and the active-fire-control requirement gate the LAUNCH (one
`groundAttackRefusal` shared by every weapon), the ECM and weasel immunity lives at the moment of
impact as `SeekerMover.impactEcmShift`, and the explosion is in `noteBallisticProgress`. A
`BallisticCourse` can now name a ground TARGET as well as a hex, and it holds the base rather than
its hex because a radius-zero planet keeps all six faces' bases in one hex.

Doors exist for every weapon that can carry a warhead to a surface: a ship's drone rack, a fighter's
drone rail, a plasma tube, a plasma rack, a fighter's type-D rail, and a suicide shuttle. The pseudo
torpedo deliberately has none — it does no damage, so bombarding with one is a contradiction.

**Two things that bit, both recorded in the commit.** `chooseSeekerFacing` refuses to steer any
seeker into a planet hex, which is right for every seeker but this one; without a narrow exception
for the one hex it was aimed at, the drone circled the planet until its endurance ran out, which
looks like a flight rather than a fault. And the first ECM-immunity test passed with the rule
REMOVED — six points of ECM forced onto the base did not survive to the impulse of impact, so the
shift was zero either way. That is why the immunity is a named method now.

**Reachable in play since 2026-10-12.** `LAUNCH_SEEKER_BALLISTIC` is one action for both shots: a
request carrying only a hex is an ordinary ballistic shot, a request that also NAMES a target is a
bombardment. The handler routes by weapon class to whichever core door fits, and a bombardment does
not send a hex at all — the base's own position is the hex, read server-side so the client cannot
get it wrong.

In the launch pad a ground base now appears with its FACE ("Ground Battery (face B)") and a
`bombard` chip, and every seeker order against it is drafted as a bombardment rather than an
ordinary launch — because P2.713 is the only way a seeking weapon reaches one. Out of reach or on
passive fire control, the row greys and says which: greyed and not hidden, because a hidden row
cannot tell "no base there" from "not from here", and the reason is what teaches the four-hex limit.
The mirror lives in `groundBombardment.ts` with its own tests, the same relationship
`weaponDamageTables.ts` has to the damage rules; core still refuses authoritatively.

**Still open on 5b:** firing at a BARE hex has no UI — the server action takes one and the rules
give four uses for it (F4.21), but the pad is target-driven and there is nowhere to click a hex yet.
That is the other half of the chooser design below, and it wants the map-click work that slice 6
brings. P2.713's last sentence, "submunitions from a ballistic MW drone cannot
target a ground base", has nothing to bite on while MW drones are unbuilt. P2.732's weasel exception
holds structurally rather than explicitly: our weasel redirect keys off the target UNIT and a
ballistic weapon has none. Also still open and smaller: the sheet's energy line (shields 1/2+1/2, life support 0,
fire control 0), P2.52/P2.524's EW asymmetry, and the asteroid siting that waits on P3.4.

**The target chooser, agreed with the owner 2026-10-12 and not yet built.** Slice 5b needs a UI
seam as well as a rule: "we might need to put up a chooser pop-up if you select a hex that contains
one or more bases, to allow you to designate if you mean to hit a base. This could also help gate
the range 4 limitation on targeting bases" (owner). Then: "on a planet you will also need to
identify which side of a planet the base is on (Ground Base A, Ground Base F, etc.), so a player
won't mistakenly target a base that can't be hit."

So when a seeking-weapon order picks a hex holding one or more ground bases, the client offers a
list rather than guessing:

- **the hex itself stays an option** and stays first. F4.21 says a ballistic seeker normally never
  explodes, and the hex is the target for minefield detection, asteroid clearing and saturation.
  Collapsing the choice into "you must have meant the base" would remove four legitimate uses.
- **each base is listed by its face** — "Ground Base B", "Ground Base F" — which is what
  `Ship.planetFace` was stored for. A radius-zero planet puts all six faces in ONE hex, so without
  the label the list would be six identical rows.
- **entries that cannot be taken are shown DISABLED WITH THE REASON, never hidden**: beyond four
  hexes (P2.713), on a face the firer cannot see (`visiblePlanetSides`/`losBlocked`), or the firing
  ship is on passive fire control (P2.713 again). Showing the reason is what teaches the four-hex
  rule; hiding the row just looks broken, and the player cannot tell "no base there" from "not from
  here".

The gating is the server's call in the end — the client only has to stop the player from
discovering the rule by having an order silently do nothing.

## Scatter-packs: the full targeting model (FD7.3) — NOT started, current handling is DELIBERATE

Owner, 2026-10-12: "I've been using the 'select a target and release in 8 impulses' as a simple way
to implement scatterpacks. But full implementation will be much more complex." Agreed, and the
current handling is correct as far as it goes — this entry exists so nobody later mistakes the
simplification for a bug and 'fixes' half of it.

**The insight to hold on to is that there are TWO targeting decisions, not one** (owner's framing):
the targeting of the SP ITSELF, which may be ballistic; and the targeting of the DRONES it carries,
which may inherit the SP's target or follow criteria programmed before launch. We currently collapse
both into "the SP has a target unit, and its drones get that same unit".

**FD7.31 states it as four questions the owning player must record BEFORE launching:**

1. **What is the primary target** — "enemy ship, or other unit, or object; **or hex if ballistic
   (F4.4)**, note that a hex cannot trigger release". This is the first targeting level, and the
   ballistic half of it is already modelled: `BallisticCourse` exists and the movers fly it. What is
   missing is the door and F4.413's consequence — a ballistic SP reaches its hex and must NOT
   release there, because only a valid target can trigger release.
2. **At what range do the drones release** — minimum TWO hexes (FD7.333, and it "cannot be
   decreased"), maximum 35, always true range. Our eight-impulse timer stands in for this.
3. **How much damage to the SP forces a premature release** — counted after launch only, not
   counting unrepaired damage carried into it (FD7.31 q3, FD7.3342). Nothing models this.
4. **Do all the drones go at the prime target, or randomly at different ones** — and if random
   (FD7.34), **what size class will they accept**? This is the second targeting level and it is
   entirely unbuilt. FD7.341 has the player NUMBER each drone before launch, and they acquire in
   that order.

**Two clauses make the first level cheaper than it looks, now that ballistic targeting exists:**

- **FD7.332: "if the target is destroyed, the SP pursues the hex where the target was destroyed"**
  (where residual radiation and wreckage exist). That is a conversion from unit-pursuit to a
  ballistic course, and `destination()` plus `BallisticCourse` already express it — it is close to
  free. Note the sting in the tail: drones released at a destroyed primary target go INERT (FD1.7),
  with the wild-weasel explosion period the one exception (J3.21, FD7.345).
- **FD7.3331/FD7.3332** give the SP two of the plot types from C1.322 that we already reason about
  for ballistic evasion: it will not voluntarily enter a hex adjacent to its target (station-keeping
  plot, C1.3224), and if the target closes to adjacent it switches to an EVASIVE plot (C1.3223) and
  backs off until it is two hexes away again.

**And the release itself is harsher than our timer suggests.** FD7.31: "If an SP meets its release
conditions, it will release its submunitions even if there is no unit that can control them at that
point. This will result in the submunitions being lost if they are incapable of gaining their own
lock-ons." F4.42 says the SP cannot guide them itself and F4.422 lets any friendly SHIP (not a
shuttle or PF) take control at the release point, needing the channels of FD7.363. D19.223 caps it
at one SP launched per turn, or within a quarter turn of one on a previous turn, and a ship on
passive fire control cannot control an SP at all.

**Why no ballistic door was wired when every other seeker got one** (2026-10-12): a door without
questions 2-4 would produce an SP that flies to a hex and dumps its drones there on an eight-impulse
timer, into empty space, with nothing able to guide them. F4.413 explicitly forbids the hex
triggering that release. Refusing the launch is the more honest state until FD7.31 is built.

## Ground bases in a scenario file — DONE 2026-10-12

A ground base names the PLANET it is built on and the face, and states no hex:

```json
"terrain": [ { "type": "PLANET", "hex": "2116", "name": "Nivram" } ],
"sides": [ { "name": "Defenders", "faction": "Klingon", "ships": [
  { "type": "GBD1", "shipName": "Nivram Battery B", "planet": "Nivram", "planetFace": "B" }
] } ]
```

`startHex`, `startHeading` and `startSpeed` are ignored and should be omitted: the hex is the
planet's, the heading is the face (P2.62) and the speed is zero. The face takes "A"-"F" or 1-6.

**Why not a hex.** It would state the base's position a second time, and the two can silently
disagree — nudge the planet one hex while tuning a scenario and the bases stay behind in empty
space, where every ground-base rule quietly stops applying because all of them key off the face.
One source of truth, and it matches the engine, whose siting call has only ever taken a planet and
a face.

**Ownership comes from the side the base is listed under**, which is the owner's ruling: "typically
one of the factions will own the planetary batteries, otherwise what player would be deciding where
it shoots?" Planets themselves carry no owner — a field was written and reverted the same hour
because nothing would have read it.

**A base that cannot be sited is left OFF the map and the reason is printed**, rather than dropped
into space: no such planet, two planets with that name, an unreadable face, or core's own refusals
(a gas giant under P2.222, an asteroid under P3.4). A base placed without a face would look present
and behave like nothing in particular, which is the harder failure to notice.

## Ground combat (D15.0) — THE RULE EXISTS; stop designing it

Found 2026-10-12, after a design conversation had got most of the way to re-inventing it. The
owner's own verdict: "this is probably all covered in the actual rulebook. I don't know why I'm
trying to freestyle planets and troops." It is, and the geometry we had reasoned our way to is the
geometry the rule states — which is reassuring about the model and a reminder to grep first. See
the read-the-rule-text memory, which exists for exactly this.

**D15.1 GROUND COMBAT LOCATIONS, and it is what we guessed.** "Unless specified otherwise in the
scenario, there are six 'ground combat locations' (GCLs) on a class-M planet, ONE FACING EACH HEX
SIDE." Troops on different faces do not interact, troops land to get there, and "these units cannot
be attacked from space; they can only be attacked or destroyed by landing ground units or shuttles
and using the ground combat routines". Every ground unit or shuttle on a planet "must be at a GCL or
in the remote areas (D15.7) of a GCL". An asteroid gets one GCL, rarely two.

**What we had wrong.** The currency is not a count of troops. **D15.11: each GCL has three CONTROL
STATIONS**, which "can never be destroyed; they can be captured" — and capture is the victory lever,
with D15.111 letting a player surrender a station to pay off casualty points. A scenario condition
should read control stations, not boarding parties on the ground.

**And bases are explicitly carved out.** D15.1's note: "This rule (D15.0) assumes that there is no
base on the hex side. If there is a base, use the procedures in (P2.75) instead of those in
(D15.11). Some scenarios require the player to secure a GCL or achieve other goals before attacking
a base within it." So our ground bases sit under P2.75, NOT under D15.11 — the two interlock rather
than overlap, and P2.75 is unread.

**What we already have that fits.** `Terrain.getSideManifest(face)` is a per-hex-side Manifest
holding crew, captured crew, boarding parties and commandos — "survey parties, garrisons" — with
landed craft and ground bases deliberately tracking their own face separately so nothing is counted
twice. That IS a GCL's inventory, built before anyone knew the rule's name. The gap is that a
Manifest records quantities and not WHOSE they are, and a GCL can be contested, so manifests want
keying by (face × side) rather than an owner field.

**The size of it, from the section list:** D15.1 locations, D15.11 control stations, D15.12
defensive systems (including GDS), D15.13 attacker fire support, D15.14 definitions, D15.15 tactical
intelligence, D15.2 operations, D15.3 a four-step combat resolution procedure with its own casualty
table, D15.35 a worked example, D15.36 the link back to SFB, D15.4 combat restrictions, D15.7 remote
areas, and D15.8x the unit types — militia, commandos, civilians, combat engineers. This is a
subsystem on the scale of boarding combat, not a victory condition.

**Before building any of it:** read D15.0 through D15.87 and P2.75 in full. Note D15.0's own warning
about scope — it is "an abstract system... cannot be used to invade an inhabited planet or one with
any significant amount of ground-based military power", and ships may not fire from orbit in support
except transporter artillery.

## Accounts, seats and durable games — ANALYSED 2026-10-15, agreed order A → B → D

Owner's direction: **multiplayer is the base assumption** — N players join a game and units are
assigned per player where that makes sense — and a game should be **persistable and resumable
later**. The question asked was the effort and risk of moving to a model where players log in and
the game instance is separate from any one user token. Analysed against the real code; the
conclusion is that the engine is already in the right shape and the cost is elsewhere.

### What is already true, and surprised the analysis

- **`Unit.owner` is a core `Player` object** (name, teamName, faction, units) — never a token.
  `grep` for `playerToken|hostToken` across `amarillo-core` returns **0**. Core already has no
  idea that web sessions exist, so "the game instance is separate from any one user token" is not
  a migration; it is the status quo in the engine.
- The token concept lives in exactly **four server files**: `GameSession`, `GameSessionService`,
  `GameController`, `LobbyStateDto`.
- **N players already works.** `GameSessionService.joinSession` applies no cap, assignment is
  per ship to a player, and `broadcastState` already sends each player their own REDACTED
  snapshot on a per-token destination. Nothing in the server assumes two.

### The actual problem: the token does double duty

One opaque string is currently both **identity** ("who are you") and **seat** ("which chair in
this game"). Accounts split it into three: a durable account id, a per-game seat id, and a binding
of game + account → seat. The nine token-keyed collections in `GameSession` — `players`,
`readyPlayers`, `coiDoneTokens`, `pendingCoi`, `pendingDeployment`, `deploymentDoneTokens`,
`pendingAssignments`, `declarationCommits`, `activityCommits` — **do not change shape**; only what
mints their key changes.

**The real argument for accounts is not security, it is durability.** The seat is a token in
`localStorage` today. Make games last weeks and clearing a browser orphans a fleet permanently,
with no way to prove it was yours. A durable game needs a durable way to prove ownership of ships.

### Slices

| | work | size |
|---|---|---|
| A | Split seat from identity inside `GameSession` | ~1 session, one file |
| B | Lightweight durable identity — account id + display name, **no password** | ~1 session |
| C | Real login via OAuth (Google/Discord) | days, plus permanent upkeep |
| D | Turn-boundary persistence (see the separate entry under *Unstarted systems*) | the big one |
| E | N-player assignment UX and the setup step rail | moderate |

**Agreed order: A → B → D. C is optional** and should stay optional. A durable id with a display
name delivers N players, resumable games and durable ship ownership without storing a single
password.

**A AND B ARE BUILT** (commit a73d1a06) and verified in a browser on 2026-10-17, by the only test
that proves them: deleting `amarillo_session` while KEEPING `amarillo_account`, so the Resume
button disappears and the join-by-code path has to do the work. It returned the player to their
own seat with their ships, and the host back as host.

**Resume and Join are different paths, and testing one does not test the other.** Resume reads the
saved token straight out of `localStorage` and never calls `joinGame`; it is also the convenience
the landing page offers first, so it is what a tester naturally uses — and it exercises none of
the account work. Join-by-code is the path that has to work when the token is GONE, and the path
that used to be destructive, minting a new seat and stranding the player's ships. Test it by
clearing the session and keeping the account.

### Risks, ranked

1. **Auth is the only genuinely dangerous new thing** — password storage, CSRF, reset flows, rate
   limiting. There is no attack surface worth naming today. It is **avoidable**: OAuth never stores
   a password, and slice B buys persistence and N players with no auth surface at all.
2. **Ship state export is the bulk of slice D, not a side task.** `Ship` has no inverse of
   `init(Map)`; nothing writes damaged state back out. `ShipDto`'s ~124 fields show where the
   values live.
3. **Refit fingerprint drift.** A ship reconstructs from hull + refits + year + loadout, read from
   ship JSON *at load time*, and this repo edits ship JSON constantly. Store a hash of the resolved
   `ShipSpec` and compare on load.
4. **Wrong order.** Accounts without persistence buys little, because sessions are an in-memory
   `ConcurrentHashMap` and every game dies on restart anyway.

### Two real defects found while reading, worth fixing regardless

- **`GameSessionService.createSession` never checks for a code collision.**
  `sessions.put(sessionId, session)` would **silently replace a live game**. The code is four hex
  characters taken from a UUID substring (~65k values), so collisions begin appearing in the low
  hundreds of concurrent games. Cheap fix; data-loss consequence.
- **That four-character code is the only access control.** Brute-forceable in seconds. Acceptable
  while every player is someone you invited; not acceptable on a public site, and it is the reason
  a public games-in-progress list cannot simply publish join codes.

### Note on the playtest loop

The owner playtests with **two different browsers** (Chrome and Edge) signed in as two different
people, to get the full experience from both sides. Separate browsers mean separate `localStorage`,
so the single-seat slot (`SESSION_KEY` in `App.tsx`) never bites — but it **would** bite two tabs
in one browser. If logins ever arrive, keep a fast path for this loop or every playtest costs two
OAuth round-trips.

## Saved games: what is built and what is left — BUILT 2026-10-15/16

A save is **a scenario plus accumulated damage**, taken at a turn boundary. The scenario is
EMBEDDED in the save, not referenced by id: half the battles here are assembled from saved fleets
and their `ScenarioSpec` never existed as a file. Once a battle is underway a fleet game and a
scenario game are the same thing — both reach `GameSession.adopt(spec, id)` and `Game` never
learns which path it came from — so there is one save format and one restore path, not two.

### Save versions, and the golden save that is deferred until one is bumped

Two version numbers, both hand-written constants, both still **1** and bumped exactly never since
the commits that introduced them: `Game.SAVE_FORMAT_VERSION` versions the STATE map and
`SavedGame.ENVELOPE_VERSION` versions the envelope around it. Nothing derives them. Every save
file that has ever existed in this repo says 1, so the newer-format refusal built on 2026-11-06
has never fired and cannot fire today.

**The check runs ONE WAY.** `version > ours` is the only comparison, in both places, so a bump
stops an OLDER build reading a file this one writes. It does nothing for the direction that
actually happens here — a NEW build reading an OLD file — because **nothing branches on the
version to migrate.** The numbers are a gate and nothing more. That means a breaking change
leaves every existing save loading and coming back subtly wrong, bumped or not.

Version 1 is nevertheless honest: the fifteen commits that have touched `exportState` since
mid-October were almost all ADDITIVE, and an added key with a forgiving default is read correctly
by an old reader that simply misses it. A bump is owed when a key's MEANING changes, when one is
removed or renamed, or when a field moves between owners.

**DEFERRED, by the owner's decision, until a version is bumped: a golden save fixture.** A real
save file, written by the build before the bump, committed, and loaded by a test. Every save test
in this repo writes and reads with today's code, so writer and reader move together and a renamed
key passes — a committed file cannot move, which is the whole value of it. Three things to settle
when it is written: it cannot live in `data/saves` (gitignored as player data); `amarillo-core`
has no test resources directory yet; and the embedded scenario NAMES hulls rather than copying
them, so the fixture will legitimately begin reporting changed hulls as the ship data evolves,
and must assert facts that do not depend on current hull JSON.

`SaveFormatVersionTripwireTest` is the reminder: it fails the build the moment either constant
changes, and its class comment carries the decision — migrate old saves, or add a floor and
refuse them, but not neither. The owner asked to be notified when this comes up.

**Load order is the format**, and it is not optional:
`setupFromScenario(saved.scenario)` builds the pieces, then `applyState(saved.state)` overlays
the battle. `SavedGame.restore()` does both.

**`applyState` runs in two passes**, and the ORDER within pass two is the OWNERSHIP CHAIN:
destroyed ships, ownership, then **craft in flight**, then **seekers**, then the **references
among them** (fighter lock-ons and control, ship lock-ons, tractors). Each kind must exist before
the next kind looks for it by name. Every departure from this has cost something real: resolving
lock-ons before the seekers existed dropped exactly the lock a player's point defence was about
to use, and reviving craft after the seekers left a fighter-guided drone with no controller —
the name was in the save and there was nothing of that name to find.

**One shared resolver, `Game.unitsByName()`,** backs every name-based reference. Four separate
things resolve units by name and each one that built its own map got it wrong the same way. A new
kind of reference now gets the whole map by default instead of whatever its author thought of.

### Built

- Ship scalars, `Shields`, `HullBoxes`, `PowerSystems`, `Crew` (D1).
- `Weapons`: arming state for Photon, Disruptor, Fusion, Hellbore and PlasmaLauncher, capacitor
  SIZE and stored ENERGY separately, firing history (D2). Weapons are keyed by
  **type+designator**, never list position — a positional key is a pointer by another name.
- Game envelope: clock (one number; turn and local impulse derive from it), map, phase,
  `seekerSeq`, ownership by team, destroyed hulls by name (D3).
- Drones and plasma torpedoes in flight, with `BallisticCourse` saving WHICH adjacent hexes were
  entered rather than a count, because F4.11 counts distinct hexes (D3b).
- `SavedGame` envelope, `data/saves`, and `GET /saves`, `POST /{id}/save`, `DELETE /saves/{id}`.
  A save is refused mid-turn and says which moment to use.

### Left

**The format is complete.** `KNOWN_GAPS` is empty, `DtoDiff` filters nothing, and every unit
type is either restored or reported by name. What remains is operational:

- **No auth on the save endpoints.** Anyone who knows a game id can save, list or delete its
  saves. Deferred deliberately while the accounts work settles.
- **Not yet seen in a browser.** Mines, sensor damage, carrier hangars, craft in flight and the
  craft-based seekers are all proved by tests and none of them by a playtest. A two-browser run
  with a carrier, a laid minefield and a scatter-pack is what would actually exercise them.
- **Turn-boundary only, by design.** A save is refused mid-turn. Per-impulse state (pending
  damage, DAC choices, fire declarations) is empty at a boundary and is therefore not in the
  format at all — saving mid-impulse would be a different and much larger job.

### Verified in a real game, 2026-10-18

Played through turn 1, saved during allocation for turn 2, reloaded, continued. The save file
carried: the turn ABOUT to be played (2, not 0), partially-spent drone racks with their drone
TYPES intact, three seekers in flight, hull fingerprints, and — the point of the exercise —
weapon firing history whose two records agree (`lastImpulseFired: 28` beside
`recentShots: [28]`, where a pre-fix save had `-9` beside `[30]`).

A second run the same day, after the four closures below, carried **launched shuttles in flight,
drones targeted on a SHIP, drones targeted on other DRONES, and weapon cooldown cycles** across a
save and reload with nothing lost. The drone-chasing-a-drone case is the one worth noting: it is
what forced the two-phase seeker revival and the shared `unitsByName()`, and it had only ever
been proved by a fixture before this.

**Still unverified in a browser**, and tests are the only witness: mines (laid, dummy and armed),
sensor/scanner damage, a carrier hangar with losses in it, scatter-packs, and a fighter's own
lock-on and guided drones.

### Closed 2026-10-18, after the entry above was written

- **Mines.** The last silent loss: a laid field simply ceased to exist. Mines are constructed
  rather than overlaid (type, placement method, `isReal` and `placedOnImpulse` are final), their
  layer resolves through `unitsByName()` and may lawfully resolve to nothing, because a minefield
  outlives the ship that laid it and `MineResolver` already reads a null layer as "not nearby".
  M3.224's dummy bluff travels, which is safe because a save is server-side and `MineDto` shows a
  client only `active` and `revealed`.
- **A ship's stock of UNLAID T-bombs**, found by unfiltering `MineDto`: it was rebuilt from the
  hull JSON, so a reload handed back every bomb the battle had spent.
- **`SpecialFunctions`**, which was not saved AT ALL — the gap entry said `.controlUsed` and
  blamed `ControlSpaces`, a class that does not contain that field. The SENSOR, SCANNER and
  DAMAGE CONTROL tracks were being rebuilt from the hull, so a damaged ship came back repaired
  (a Fed CA went from a shot-out sensor 5 to a factory-fresh 6). Only the POINTER into each track
  travels; the steps are the hull's. `controlUsed` is deliberately NOT saved — it is a count kept
  in lockstep with `controlledSeekers`, so only the list travels and the count is rebuilt from it.
- **Suicide shuttles and scatter-packs.** Both wrap a craft, and the reason for deferring them
  died with `Shuttle.fromState`. J3.0's arming state is the weapon; a scatter-pack's payload was
  a COUNT, which is the drone-rack mistake from the other side, so each drone now travels as a
  drone and the pending payload stays separate (FD7.3).
- **A lesson about the restore of any reference**: the controller was being set through a chain of
  `instanceof` tests that knew only `Drone` and `PlasmaTorpedo`, so a new seeker kind came back
  unguided. `Seeker` declares `setController`; it goes through the interface now. A new kind must
  not have to find and extend a list like that.

### Done earlier in the save work

- Putting a save back into a session (a restored battle opens as a NEW game, ships unclaimed,
  reassigned by whoever is in the room — which is what lets a battle be continued by different
  players, and handles captured hulls correctly).
- `DroneRack`: contents, reload sets, anti-drone rounds and the per-turn flags. This was the
  exploitable one — racks used to come back FULL.
- The ship-spec fingerprint, compared on load and reported by name.
- **All the remaining system groups** — tractors and their links, EW circuits, labs,
  transporters, cloak, UIM, DERFACS, drone and cargo stores, reload stockpiles, ready racks, box
  cycles. `StateIo` centralises the defensive JSON reads they all needed.
- **Carrier hangars and craft in flight** (shuttle slices 1–3). A bay box carries the craft
  that is actually in it, so a reload no longer refills a hangar the battle emptied; craft in
  flight are rebuilt from their catalogue type with a saved ROLE to tell a wild weasel from the
  admin shuttle it is built from; and a fighter keeps its own lock-on (D6.121) and the drones it
  is guiding (J4.222).
- **Session lifecycle**: `endSession` plus a sweep of abandoned games, so a restored battle no
  longer leaves its pre-save twin alive and joinable.
- **Delete button for saves**, wired to the endpoint that already existed.
- **Bases**, verified rather than assumed (`BaseSaveTest`). A ground base is the one unit whose
  position is NOT in the save: R1.14 names a planet and a face, so `setupFromScenario` re-sites
  it and the hex, heading and face are all reconstructed. The one way that fails — a scenario
  whose planet no longer matches by name — is reported rather than silent.

### Two testing lessons worth not relearning

**A round-trip test only detects omissions in state the fixture actually CHANGES.** An untouched
system compares equal and proves nothing. This bit three times — shields-only damage made the
whole gap list look satisfied; zero commandos hid a dropped field; a drone launch failed silently
until the fixture asserted its own result ("Drones can only be launched during the Activity
phase"). A fixture that does not verify it created the state is a test that proves nothing.

**The GRANULARITY of a tolerated gap matters more than the list.** Keying gaps by top-level DTO
field meant `mapObjects`, which holds every ship — a save could have lost every shield on every
ship and passed. Filter the unconvertible classes out of the compared graph instead, and key what
remains by path fragment. Switching to fragments immediately surfaced four real losses the coarse
version had been hiding.

## Named individuals aboard a ship (D7.82/D7.83) — the route to Vak Kaleen

**(D7.8371) control-room guards: FIXED 2026-11-06.** Owner caught the claim that control rooms
were not guardable "by design". They are: *"A single boarding party, assigned as a guard, is
sufficient to protect all connected control boxes of a single type ... Different types of control
boxes (security, flag, aux, emer, bridge) can never be protected by a single guard."* So it is one
post PER TYPE covering every connected box of it — the same group shape as a warp engine, and
(D7.8375) therefore holds the combat-damage casualty roll until the WHOLE group is gone.

`SystemTarget.Type` had no control room at all, so the bridge of every ship in the game was both
unguardable and un-raidable. Those were one bug, not two: the raid picker and the guard picker
both read `getTargetableSystems`, so adding the five rooms there fixed both through all three
tiers with no UI change. Note the Annex #7E flag-to-security CONVERSION is deliberately NOT applied
to a raid — that is how the DAC reallocates a hit on a hull with no flag bridge, and a raider
naming a room is not the DAC.

**The bigger find: Vak Kaleen is an instance of a GENERAL rule, not a scenario special.** The whole
of SH80.46 is already covered by rules we have not built:

- **(D7.838)** *"Up to six individuals may be specified as being in a given compartment (or room, a
  designation not specifically translatable into specific systems boxes on the SSD), and one
  boarding party can guard that one room. For example, several legendary officers could be
  designated as being in the 'bridge'."* This is the representation: a named person occupies a
  ROOM, and a room is explicitly looser than an SSD box.
- **(D7.824)** *"Hit-and-run raids may be made to remove specific individuals or objects from a
  given ship ... When trying to capture a person or object, the result 'System destroyed and BP
  returned safely' is read 'target captured and BP returned safely.'"* That is the CAPTURE
  mechanism, and it reuses the hit-and-run result table we already have.
- **(D7.827)** assassination raids: the raider names *"a given SSD box or room where an individual
  might be located (G22.13)"*, and any identified person present is killed.
- **(D7.826)** crew units, deck crews and boarding parties CANNOT be raided — so the individual is
  a different kind of thing from the personnel around them.

SH80.5's terms line up exactly: 200 points to the Federation if Kaleen is captured (D7.824), 100 if
he is killed (D7.827). The scenario invents nothing. **This supersedes the earlier plan of modelling
him as an `Objective` retrieved by transporter** — the transporter path is still right for the
ally-to-ally handoff SH80 allows, but capture and death belong to the raid table.

**What to build, in order:** an occupant list on a room (D7.838, up to six, with identity); raid
outcomes reading capture/assassination against it (D7.824/D7.827); then (G22.13) legendary
officers, which is the same feature with a different source of names. The injury/death cup of
SH80.461-.467 still needs the printed page — ask the owner to re-upload it.

## Moving an objective between ships (G25.0) — built 2026-11-06

Prompted by Vak Kaleen (SH80.46), but built as the general mechanism: a scenario objective
could be picked up off the map and carried off it, and nothing in between. Reading (G25.0)
first changed the design three times, so it is worth recording what the rules actually say.

**Done, all tiers:**
- **Ship-to-ship transfer is the SAME action as pickup**, with a carrier as the source rather
  than a hex. `pickUpObjective` used to refuse anything already aboard a ship; it now
  routes the carried case through the same transporter operation. The receiver is always the
  acting ship. `PICKUP_OBJECTIVE` on the server needed no change at all.
- **The carrier's shield is the consent handshake.** The receiver auto-lowers its own facing
  shield (G8) because it owns it, but it cannot lower the carrier's — so the carrier's must
  already be down. No new protocol, no new DTO, and no way to drop an enemy's shield by
  "giving" them something. The same rule means a prize CAN be stolen off an enemy who has a
  shield down, which is correct and is tested.
- **(G25.211) size cap.** A transporter moves ten cargo spaces and "two transporters cannot
  ever be used to move a single object of any size." So a big enough item can never be beamed
  — that is the rules reason some objectives MUST go by shuttle, not a scenario preference.
  `Objective.cargoSpaces` is a BOXED Double: unstated is not the same as weightless, and
  a primitive would have read every silent scenario as zero and passed.
- **(G25.214) you cannot just drop it.** "It is not possible to transport (or drop) cargo ...
  into space and have that cargo then 'exist' for some purpose ... A special scenario rule
  might provide a specific type of item that could be left in space for a time." Jettison
  therefore DEFAULTS TO REFUSAL and `canBeLeftInSpace` is a per-item scenario grant.
  This killed the first design, which was to let any ship drop an objective and have the other
  one collect it — the rule forbids exactly that.
- Fixed on the way past: the transporter path set the carrier but left the old free hex on the
  objective. `getEffectiveLocation()` hid it until something asked for the raw one.

**Sizes come from Annex #7K** (it IS in `Master_Annex_File.pdf`, PDF page 157): small objects
0.05, probe 2, large mine / UIM / cloaking device 4, defence satellite 10, stored shuttle 25,
available-for-flight shuttle 50, PF 125, PF available for flight 250.

**Still open, in cost order:**
- **SHUTTLE_PICKUP** — the mode for anything over ten spaces, and the only way to move the big
  items at all. Needs `Objective.carrier` widened from `Ship` to `Unit` so a shuttle can
  hold one. (G25.22) gives the rate (a deck crew loads 16 spaces/turn) and (G25.135) the limit
  (shuttles carry only cargo divisible into four-point units, so one indivisible 25-space item
  does not fit a shuttle either — it needs a tractor).
- **(G25.23) DIRECT TRANSFER** — blocked on something bigger: it requires the two units to be
  DOCKED (C13.0), and docking is not modelled anywhere in core. Rates are a table by docking
  condition (ship-to-ship 16 spaces/turn), it happens in Marines Activity Stage 6B7, and
  (G25.236) says it costs no power.
- **(G25.25) contents** — "the contents of a given point of cargo ... cannot be detected unless
  a special scenario rule ... defines otherwise." There is no contents field yet; Vak Kaleen is
  the first thing that will need one, and the first PRIVATE field on ObjectiveDto.

## Published scenarios: what SH80 needs (measured 2026-11-06)

The owner asked how close we are to playing the published scenarios. Measured against
`data/scenarios/sh80.json` — "Dictator's Diplomat", the most demanding file we have,
with three sides, two of them allied — rather than by reading the loader.

**It already loads and sets up.** `Sh80LoadsTest` puts all three sides on the map with
their real hulls at their printed stations, headings and speeds, at weapon status, with the
D7N's UIM refit applied. Nothing had to be added to make that work.

**So a published scenario is PLAYABLE TODAY with its special rules enforced by the players**,
which is how a face-to-face game works anyway. The prose reaches them: `specialRules`
travels through `LobbyStateDto` and `PreGame.tsx` lists it before the game
starts. The question is only how much a rule could enforce for them.

**Structured and ENFORCED today:** start hex, heading, speed and weapon status per ship;
per-ship refits and faction overrides; deployment zones; terrain and terrain plans;
objectives; reinforcements with arrival windows and secret selection; COI budgets and
loadouts; option mounts; `maxTurns`; and — the one that matters most for SH80 — per-side
`destructionDirections` and `destructionEdges`, which `DisengagementResolver`
reads. A ship leaving the wrong way really is destroyed.

**The transcription had THREE errors, found only by reading the printed scenario against the
file (2026-11-06).** All three sat in (SH80.41) — the one part of this scenario the engine
actually enforces — so the automation was faithfully applying the wrong rule:

- the **Federation carried no restriction at all**, so the Lexington could run out in any
  direction, where SH80.41 allows only A;
- the **Klingon listed F as fatal**, where "B, C, E, or F" are all legal exits;
- the **Romulan used map EDGES**, the wrong model as well as the wrong content: the rule
  restricts the DIRECTION of disengagement, and the map floats in any case.

Also unset: `commanderOptions.maxDroneSpeed`, though (SH80.432) says all drones are
"medium," Speed 20 and the field already existed.

**The lesson is that a transcribed scenario needs checking against its printed page the way a
ship does against its SSD.** None of the three looks wrong in the file, and the engine cannot
tell — it enforced them exactly as written. Any other transcribed scenario in
`data/scenarios/` should be assumed unchecked until someone has the page beside it.

One clause is deliberately NOT encoded: the Klingon "cannot disengage by any means in
direction B or C unless a Romulan crew unit has transferred". Listing B and C would forbid
outright what the scenario permits once the transfer happens, so it stays with the players and
the file records why.

**Carried but with NO consumer in core**, which is the honest gap:
- `mapType` — SH80 wants FLOATING, and the scenario tracks how far the xx30 row has
  shifted. Nothing reads the field. This is the biggest single item for this scenario.
- `lengthCondition` — "STANDARD" (last side standing) is not computed.
- `victoryConditions` — a type string plus free text. `VictoryCalculator` exists
  and does the standard arithmetic, but S2.201 MODIFIED terms are not read from the file.

**What SH80 asks for that no scenario field could express yet**, in rough order of cost:
- **ALLIANCE.** Two sides that "cannot damage one another in any way (unless the target has
  been captured)". There is no allied-sides concept — sides are independent teams.
- **A destruction REGION, not just an edge.** "All territory below the xx30 hex row is
  Tholian territory. Any ship entering this area, even if pushed there by tractor, is
  considered destroyed immediately." `MapRegion` already exists for deployment zones, so
  this is a region with a different verb rather than a new concept.
- **Conditional disengagement.** The Klingon cannot leave in directions B or C "unless a
  Romulan crew unit has transferred to the Klingon ship" — a disengagement restriction that
  depends on game state.
- **A named character in a box.** Vak Kaleen occupies one of the bridge boxes and has his own
  movement, injury and capture rules (SH80.46-SH80.467). Nothing models a person as a thing
  that sits in a system box, and this is the one item that is a genuine feature rather than a
  field.

**The cheap order, if someone wants published scenarios to run themselves:** the destruction
region first (MapRegion is there), then the floating map, then alliances. Victory conditions
and named characters are their own projects. SH47's training subset needed none of the four
and has been built — see the next section.

## SH47 "The Stasis Box" — BUILT 2026-11-07, bar two clauses

`data/scenarios/sh47.json`, guarded by `Sh47LoadsTest`. The first published scenario in this
repo meant to be PLAYED rather than measured: Fed DD *Xerxes* at 4230 and Kzinti CL *Mystic* at
0101 — opposite corners of the printed map — at speed 4, WS-0, in a standard P3.1 asteroid
field, with each side able to leave by one map edge only.

Three things about it are worth keeping:

- **The asteroid field came for free, and exactly.** `TerrainGenerator` already implements
  P3.11/P3.12 — eighteen counters in the hexes the rule names, each rolled one hex, every hex
  within two becoming an asteroid hex. SH47.46's shuffle changes what four of the counters ARE
  and never where they sit, and the rule says so outright: removing a Stasis Box leaves its
  asteroid hexes behind. So the geometry the engine lays out already IS this scenario's field.
  No seed in the file, deliberately — the field should differ from playing to playing, the way
  a reshuffled stack of counters does.
- **Edges here, directions in SH80.** SH47.41 fixes the map and names map edges outright
  ("the Federation can only disengage from 42xx map edge"), so this file uses
  `destructionEdges` and must not use `destructionDirections`. SH80.41 restricts the DIRECTION
  of disengagement on a floating map, so it is the exact opposite. Same two fields, opposite
  verdicts, and only the scenario text decides which. One caveat recorded in the file: marking
  the other three edges as DESTRUCTION is harsher than SH47.41, which never says an illegal
  exit kills the ship. It is the only enforcement there is, and the alternative is a Xerxes
  free to carry a box out of the Kzinti's edge and win.
- **The map is 42x30, and `ScenarioSpec` defaults to 32 rows.** The scenario is what proves
  the size: every set-up hex it names is a corner — 4230 and 0101 for the two players, 0130
  and 4201 for the third and fourth of SH47.64. Two other files in `data/scenarios` state 42x32
  explicitly. Worth deciding whether the DEFAULT is wrong rather than restating it per file.

**What the subset leaves out — the fog layer, which is the scenario's whole point:**
- **Hidden counters BUILT 2026-11-07, NSMs and all.** SH47.46's box hunt works: eighteen
  face-down counters on the map, two Stasis Boxes among them, four withdrawn from the stack
  unseen so neither player knows whether two exist, one, or none (~66/31/2.6%). A ship searches
  one by entering its hex; the counter goes and what it held takes its place. `SearchMarkerTest`
  and `Sh47LoadsTest` cover it, and `sh47.json` uses it.

  **The model is a counter, not a hidden objective, and that was a correction.** The first cut
  hid the OBJECTIVE and left it out of the DTO — which gets the secret backwards, because
  SH47.471 has a ship search a counter "by entering the hex with the counter" and it could not
  choose to do that if the counters were invisible. Positions are public; the assignment and
  the COUNT are not. Worse, it put the box in a list every DTO builder then had to remember to
  filter, and that failed twice in a day: once through `mapObjects`, once through the live
  scoreboard, the second caught only by reading the rule back against the DTO. A counter's
  payload is in no list until it is found. `SearchMarkerDto` has no fields at all, and
  `MapObjectPrivacyTest` carries a deliberately EMPTY ruling for it so the first field added
  fails the build — that test iterates its rulings, not the DTO hierarchy, so a class with no
  entry is invisible to it.

  **No `revealed` flag on a marker**, because one never exists revealed: examining it removes it
  and materialises the payload in the same step, so the live list IS the unsearched set. That
  drops SH47.473's "it remains face up" on purpose — on the table the counter IS the marker for
  the asteroid field centred on it, which is why SH47.46 has to say removing a box leaves its
  asteroids behind. We expand the field into independent hexes at setup, so the counter has no
  second job and deleting it loses nothing a player can act on.

  **The spec/server line is where the privacy lives.** `ScenarioSpec` carries the counter
  positions and the bag's shape — both printed in the rulebook — and never the assignment,
  because the lobby broadcasts the spec to both players. The draw happens in
  `ScenarioLoader.loadSearchMarkers` when the Game is built, and the save records the hand so a
  reload cannot re-deal and move the box.

  **The terrain output did not change**, which is what kept this cheap: 22 less 4 is 18, so
  every counter position is filled whatever the draw does. `keepingTheCountersChangesNoTerrain`
  pins it.

- **The NSM counters BUILT (SH47.48).** Two of the twenty-two are nuclear space mines, and
  finding one puts a live mine in its hex. It needed no new placement path: a DROPPED mine arms
  by M2.34 once its layer is more than a hex away, and `MineResolver` reads a NULL layer as
  `Integer.MAX_VALUE` — "not nearby" — which is the right answer for something left there a
  month ago, and is exactly SH47.48's "the mines only roll for triggering AFTER they have been
  revealed". The one-impulse gap before its first roll is the rule, not an ordering accident:
  SH47.48 says a unit "might uncover a mine ... and then use a emergency deceleration ... and
  thereby avoid triggering the mine", an escape that exists only because the uncovering move is
  free. M2.14 size-class settings came with it, generally — a set on the mine, empty meaning
  all (M2.141), non-contiguous allowed (M2.144). SH47 sets 1-6, and note the direction: a LARGER
  size class is a SMALLER unit, so "size class 6 or larger" excludes drones at 7.
- **SH47.474's two-turn study BUILT.** One accumulator, two accrual rules declared in the
  scenario: `TURNS` (a complete turn in the hex, which is SH47) or `RESEARCH_POINTS` (the G4.11
  chart at range 0, so lab boxes set the pace — and the first production caller
  `calculateResearchPoints` has ever had). SH47 uses TURNS because its own balance section
  forbids the alternative: a Fed DD has EIGHT labs to the Kzinti CL's four, and SH47.71 swaps
  the CL for a Kzinti DD with TWO, so paying study out of labs would turn the lever meant to
  help the weaker player into its opposite. Progress is per ship and WIPED on breaking off,
  which is what makes SH47.8's weasel advice worth following.
- **SH47.476 BOTH HALVES BUILT.** Guarding: D7.83 reads "specific individuals or objects", so a
  carried objective is a guard target via an `OBJECTIVE:<name>` code through the ordinary
  `assignGuard` — Energy-Allocation timing, the roster, D7.833's one-per-object, D7.834's
  release on capture and D7.831's harsher chart all for free. A post cannot be put on something
  the ship is not carrying, and cannot outlive its charge (one sweep at the turn boundary, not
  a release call on each of the four ways a box leaves a hold). Raiding: D7.824 re-reads the
  D7.81 chart — "'System destroyed and BP returned safely' is read 'target captured'" while
  "'System and BP destroyed' is applied as stated". D7.825 makes the permission a scenario grant
  (`raidCapture`/`raidDestroy`, both default false), and D7.85 is why they are TWO flags: "hit
  and run raids can destroy, but not capture, a cloaking device". Which permitted attempt is
  made stays the raider's choice (D7.824/D7.827), so the list offers one row per attempt and the
  server gates each one.
- **SH47.472 craft examination BUILT.** A shuttle or fighter searches a counter by being on it
  at impulse 32 and rolling a 1 or 2. Impulse 32 is a CONSEQUENCE, not a separate rule: the roll
  needs the craft's movement for the TURN to be over, so a craft gets one attempt a turn where a
  ship gets one an impulse, and a counter a cruiser clears in one impulse takes a shuttle three
  turns on average. Simply being there is enough — the rule's own "the shuttle may remain in the
  hex ... to try again" only works if sitting still counts as ending movement there (owner's
  reading). Every craft on the counter rolls; two shuttles are two pairs of eyes. A shuttle can
  turn up an NSM as readily as a box, which is correct: a shuttle is size class 6 and SH47's
  mines accept 1-6.

  Two traps found writing it, both worth keeping. **`TurnTracker.getLocalImpulse` maps impulse
  0 to 32** — "if the div is 0, we're actually on the 32nd impulse", right mid-game and wrong
  for a clock that has not started, so a game at impulse 0 reports impulse 32. The production
  path now checks the absolute clock too. And **an assertion that cannot pass is worse than one
  that cannot fail**: the first draft asked `getSearchMarkers().isEmpty()`, which can never be
  true when the fixture lays all eighteen counters, and it reported a working feature broken.
- **SH47.5's incident tiebreaker.** "If no boxes exist, the player who scored the most internal
  hits on the other LOSES the scenario and ruins his career by provoking an incident."
  `VictoryConditions` has no way to express a loss condition that fires only when another
  condition is unreachable. **This bullet was wrong on both of its other claims until
  2026-11-10, and both were checkable in one grep.** It said nothing tallies internal hits
  scored per side — `Game.internalsScoredBy(team)` does, with `InternalsScoredTest` and an
  `internalsScored` column already on the scoreboard DTO. And it said "the rest of SH47.5 is
  built: disengaging with a box wins, and two boxes can mean both players win" — neither is
  built: `winsOnSecure` does not exist anywhere in the tree, and `GameEndResult` is still
  `record GameEndResult(String winnerTeam, String reason)`, one winner. See the SH47.5 entry
  above, which is the accurate one; the two entries contradicted each other for three days.
- **`Objective.revealed` has no user.** The hidden-objective path (a box hidden in the open
  rather than under a counter) still works, is still tested, and the reveal sweep still handles
  it — it is one rule, "a ship in the hex finds what is in the hex". But SH47 hides its boxes
  under counters instead, which is the stronger mechanism because an unexamined box does not
  exist to be leaked. Give it one more scenario; if nothing claims it, cut it along with its DTO
  omission and scoreboard filter. Raised by the owner's standing objection to single-use paths,
  and it is mine.
- **Never seen in a browser.** Everything in this section is server-side and well covered by
  tests, and none of it — the "?" counters, the mine reveal, the study log, the guard post, the
  raid rows — has been looked at on a real map. That is the state in which a rendering or wiring
  mistake hides, and it is now the largest risk here by some distance.

Already built and used by the file: SH47.475 (`survivesCarrierDestruction`, whose code comment
cites the clause), disengage-with-the-box-wins (a safe-edge exit calls
`game.secureObjectivesFor`), SH47.431's 20% COI, SH47.432's slow drones via
`commanderOptions.maxDroneSpeed`, SH47.44's no-refits, and P3.21/P3.22 asteroid collision
damage.

One small thing found while writing the file: **deployment overwrites a scenario-fixed speed.**
SH47.2 says "heading at player's option, speed 4", which a deployment zone of a single hex
(`MapRegion.circle("4230", 0)`) expresses perfectly — except that `applyDeployments` writes the
submitted `startSpeed` over the spec's, and the deployment screen lets the player choose it. So
the headings are fixed in the file instead. Letting a scenario pin speed while leaving heading
and hex to the player is a few lines, and SH47 is the case that wants it.

## Unstarted systems

Roughly largest first. Several have had their rules read and their design settled; those say so.

- **Turn-boundary save and reload.** BUILT. See *Saved games: what is built and what is left*
  above, which is the authoritative entry; what remains there is operational, not format work.

  This bullet used to restate the design at length and listed five things as unfinished —
  restoring into a running session, the remaining system groups, mines and craft in flight, and
  the ship-spec fingerprint. All five are built: `GameSessionService` hands a restored save to
  `GameSession.adoptRestored`, and `Game.exportState` writes `seekers`, `activeShuttles` and
  `mines` alongside a resolved-`ShipSpec` fingerprint. An outside reviewer found this entry
  still claiming otherwise on 2026-11-07, which is the second time a duplicate in this file has
  outlived its subject. **One subject, one entry** — a second copy is a second thing to keep
  true, and it never is.
- **Event-log replay, and seeded dice.** Not started; wanted by the owner (watch a battle back).
  This note used to live inside the turn-boundary save entry, where it was answering a claim
  that seeded dice gated saving. They do not, and the distinction is the useful part:

  A SNAPSHOT re-derives nothing. Every roll before the save has already become ordinary stored
  state — a shield box is down, a lock-on is held — and the rolls after it need not match a
  timeline that never happened. REPLAY is the feature that genuinely needs reproducible
  randomness, because it re-runs the battle from the start and every roll must come out the
  same. Deterministic TESTS want it too, on their own merits: `DiceRoller` uses `Math.random()`,
  so an unseeded statistical test can pass by luck — see [[feedback_prove_test_fails]].

  So: seeding is worth doing early, and it was never a gate on saving. The original error was in
  the compression, not the design; an outside reviewer caught it on 2026-10-09.

- **The campaign system.** Owner's direction, not designed. Would lean on drone storage, crew
  quality and carriers. Rule pointers: G21.3 → U7.9/U2.0, G22 legendary officers → U7.8/U1.26.
- ~~**The scenario system**~~ **LARGELY BUILT — this entry was stale (corrected 2026-11-06).**
  `com.sfb.scenario` holds ScenarioSpec, ScenarioLoader, Deployment, TerrainGenerator,
  FleetLoader/FleetValidator, CoiBudget/CoiChoices/CoiLoadout and SavedGame. There are ELEVEN
  scenario files in `data/scenarios/`, including a published one (sh80.json) whose spec
  carries year, numPlayers, mapType, lengthCondition, specialRules, sides, victoryConditions,
  shuttleRules and commanderOptions.

  What is actually open is narrower, and the section *Published scenarios: what SH80 needs*
  below measures it against a real file. Do not plan this as a greenfield feature.
- **Fleet builder UI, terrain and deployment.** The S8.0 validator is done (12 rules, core plus
  endpoint); the rest of the screen is not.
- ~~**ESG (G23).**~~ **BUILT — this entry was stale (corrected 2026-11-06).** `EsgResolver` is a
  real collaborator citing G23 sixty-two times, and `EsgMineTest` exercises it. It is still a
  moving hollow damage-ring rather than an expanding sphere. Left as a correction rather than
  deleted, because "unstarted" said of something with its own resolver is the exact failure mode
  this file warns about at the top.
- **Tugs and pods (G14).** Rules now read. Decided: a pod is an attachable **unit**, never a
  tug+pod combination file — G14.34's mid-turn cost change, G14.353 independent status and
  G14.6's pseudo-pod each defeat that. Ten tug hulls are already in the data. The blocker is that
  `moveCost` and `sizeClass` are static JSON.
- **The Fire Orders pad** — PARTLY BUILT, not unstarted (corrected 2026-11-06).
  `FireOrdersPad.tsx` is 1,092 lines and carries the declaration button this file elsewhere
  claimed was missing. Someone should diff the five-slice plan against what shipped before
  quoting a remaining scope; the privacy constraint on seeker grouping is the part to check
  first.
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

## Shield operating levels (D3.3) — BUILT 2026-11-06, four clauses open

Shields cost energy every turn (D3.32) and a ship that does not pay does not have them. Found
in a live game by the owner: "During EA I had a ship spend 0 energy on shields. This should
have meant that it had no shields during the next turn. But I'm seeing full shields."

**The cause was a WRITE-ONLY FIELD, and it is worth remembering as a shape.**
`Ship.shieldsStatus` was set correctly by `allocateEnergy` every turn and read by
NOTHING — not the damage path, not the DTO, not the web, not one test. A field with no readers
cannot be caught by a test of its readers, and it had sat there long enough to look deliberate.
When something is set but never consumed, that is the bug, not a loose end.

**Now built:** `Shields.operableBoxes` decides how many boxes of a facing can absorb
anything — all of them at full, five at minimum (D3.33), none when off. `damageShield`
spends only those, so unpowered boxes stay UNDESTROYED exactly as the rule says, and come back
the moment somebody pays. (D3.331) is in there too: damage taken at full power eats the
minimum five first, so the rulebook's own example — a 20-box shield that took two hits has
THREE working boxes at minimum, not five — is a test.

**The default is load-bearing.** `Shields.operatingLevel` defaults to Active, and
`Ship.shieldsStatus` was changed from Inactive to Active to match. A great many units never
file an allocation — bases, ground bases, anything built straight in a test — and defaulting
the other way strips their shields, which is a far worse bug than the one being fixed.

**It is on the wire and on screen.** `ShipDto.shieldStatus`, ruled PUBLIC because
(D3.242) says the level "will be known" and (D3.332) lets enemy sensors read full-versus-minimum
at fifty hexes. The SSD panel shows a badge for anything but ACTIVE, and it has to: the boxes
cannot show this. An unpowered shield draws a FULL set of boxes, which is precisely why the
bug was reported as "I'm seeing full shields" — the display was not wrong, it just could not
say the only thing that mattered.

**Two test fixtures were paying for nothing and relied on it**: `PlasmaRackLaunchTest`
and `PlasmaRackBoltFirePathTest` allocated life support and fire control but no
shields, and their ships started dying once damage stopped being absorbed for free. They now
pay (D3.32) like a real ship. If a test suddenly has ships dying in it, check its allocation
before suspecting the damage code.

**STILL OPEN, all four about CHANGING the level rather than paying for it:**

- **(D3.334) the eight-impulse lockout.** "Any change in the shields, from full to minimum,
  or from minimum to full, can only be done once in any eight impulse period... A change
  during Energy Allocation counts Impulse #1 as the first impulse." Nothing enforces this;
  `BoxCycle` is the existing model for "unavailable for 8 impulses" and is the place to
  start.
- **(D3.322) allocated-but-not-activated.** "If energy was provided during allocation but the
  shields were not activated, the power remains available and the shields may be activated
  during the Operate Shields Step of any later impulse." We activate at once from the form,
  so a player cannot hold the power back and raise shields mid-turn.
- **Raising with reserve power** (H7.34): minimum can go to standard mid-turn on reserve
  power, "but not in response to damage (H7.345)". Unbuilt, and the H7.345 half is the part
  that needs care.
- **(D3.333) small hulls.** "Some smaller ships have only five (or fewer) shield boxes. These
  ships can pay the minimum shield cost and need not pay the full cost." Not modelled; such a
  hull currently pays full price for shields it cannot exceed.

(D3.331)'s other half is also unbuilt: the minimum five are "the last to be repaired", which
damage control does not know.

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
- ~~**The type-G rack's third reload set never materialises.**~~ **NOT A BUG — checked against a
  running ship 2026-11-06, and the report was a misreading of the COUNT.** An AD6 at Y176 holds
  exactly what FD3.72 and the sheet describe: `ammo=4, droneReloadSets=2, addReloads=8`. Two sets
  matching the magazine, plus eight anti-drone rounds as the third reload.

  The owner stated the rule that settles it (2026-11-06): "The last reload of a Type-G rack is
  always ADDs. So in Y175 (three reloads), we should see two sets that match the rack's magazine
  plus 8 ADDs to represent the last reload." Eight is not written down anywhere — four spaces at
  half a space per anti-drone (FD3.70) — so it follows the rack size.

  **Why it looked broken.** The third reload cannot live in `reloads`, because an anti-drone is
  not a `Drone`; it is the `addReloads` count. So `getNumberOfReloads()` answers 2 (the drone
  sets) where the SSD says three, and "reports two reloads" was read as a missing set rather
  than as a different thing being counted. `TypeGThirdReloadTest` now pins the contents on all
  three hulls, before and after the refit, so the next reader gets the answer rather than the
  diagnosis.

  **What IS worth knowing**, though nothing is wrong today: `getNumberOfReloads()` returns
  `reloads.isEmpty() ? numberOfReloads : reloads.size()`, so an UNLOADED type-G answers 3 and a
  loaded one answers 2. `ReloadStockpile` already avoids it by reading `droneReloadSets()`; the
  DTO's `reloadCount` still uses the ambiguous getter. It reports the ADDs separately as
  `antiDroneReloads`, so nothing is lost on the wire — but a UI adding the two numbers together
  would be wrong, and a UI showing only `reloadCount` disagrees with the sheet.
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
