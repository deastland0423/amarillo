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
- **Ballistic targeting (F4.0) is BUILT** — see the ground-base section below for what landed and
  what did not. Kept here only as a pointer, because this entry used to hold the full reading of
  F4.0 and somebody looking for it will come here first. Still open: the SCATTER-PACK has no
  ballistic door (it needs F4.41/F4.42/F4.43, which is a feature in its own right — see the
  scatter-pack section), F4.3 wild boars, F4.5 tractors, and firing at a bare hex has no UI.
- **Mine racks (M9.0) — rules READ 2026-10-12, unbuilt.** The Romulan large Q-ship is the first
  hull in the data to carry them, and `auxiliary.mineRacks` is parked rather than read.

  **M9.11:** minesweepers and minelayers carry mine storage boxes. "Each such box holds FOUR large
  mines. Two small mines can be substituted for one large one." So a rack is 4 NSMs or up to 8
  T-bombs — the owner's analogy is a type-G drone rack holding type-I against type-VI drones, and
  `DroneRack` already models capacity that varies by what is loaded, so it is the pattern to copy
  rather than a new idea. **M9.12: there are NO reloads for mine racks**, and the mines in them do
  not come with the free dummies a T-bomb gets (M3.224).

  **M9.13 makes them a damage location**, which is the part that reaches beyond minelaying: cargo
  hits MAY be scored on mine rack boxes and MUST be when no other cargo boxes remain, and shuttle
  hits must be scored on shuttle or mine rack boxes where either exists (Annex #7E). Mines on a
  destroyed rack are destroyed and do NOT detonate. **M9.14:** a non-minelaying ship cannot carry
  mines at all except under M3.1 or M2.7.

  What exists today: `Game.dropMine` takes "TBOMB", "DUMMY_TBOMB" or "NSM", `MineResolver` runs
  arming and detonation, and `auxiliary.nuclearSpaceMines` gives a ship its NSMs. What is missing
  is the RACK as an inventory and as a hit location.
- **R1.7 acceleration limit on freighters and Q-ships — DEAD DATA, not implemented.** Every
  freighter and Q-ship sheet carries the same line: "this ship can accelerate by no more than 5
  movement points or double the current speed. It can disengage by acceleration." Eleven ship files
  declare `auxiliary.maxAccel: 5` and **nothing reads it** — `ShipSpec` does not even declare the
  field, so Jackson drops it at parse time. A freighter can presently go from zero to flank in one
  energy allocation.

  Already tracked in code: `ShipJsonKeyGuardTest`'s PARKED list names it, which is what keeps
  the gap visible rather than silent. Spelling unified 2026-10-12 (owner: "maxAccel: 5 is cleaner"): the four large Q-ships said
  `slow: true` instead, which was a second name for the same unimplemented rule and made the gap
  look like a distinction. Note the field name still only carries ONE bound of the rule — the
  doubling clause and the disengage-by-acceleration clause are not expressible in it, so whatever
  implements this will probably want to read the rule rather than the number.
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

**Six Q-ships, ready to enter now (R1.7, pp27-32).** Kzinti large p27 and small p28, Romulan large
p29 and small p30, Tholian large p31 and small p32. Nothing new is needed: Q-ships already exist as
`L-Q` and `S-Q` for the Federation, Gorn and Klingons, so this is data entry against a working
pattern. (Separately, the Hydrans have `S-Q` but no large Q-ship, which is not from this book.)

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
