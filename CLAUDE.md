# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A computerized version of the Star Fleet Battles (SFB) board game. Rules fidelity is the top priority: implementations follow the Captain's Master Rulebook, and code comments cite rule numbers (e.g. `G7.42`, `C3.14`, `E10.4`) at the site that implements them. When starting work on a new rules area, ask the user to upload the relevant rulebook PDF rather than working from memory — but **look in `data/rules/` and `data/ssd/` first**, which already hold the Captain's Master Rulebook, the Master Annex File and the SSD books. Grep them before concluding anything is missing: a note claiming "we don't have that annex" has been wrong more than once.

Read text PDFs with `pdftotext -layout`. **Page rendering IS available**, contrary to what this file used to say — `pdftoppm` is installed but NOT on PATH, so `command -v` wrongly reports it absent. Call it by full path:
`/c/Users/deastland/AppData/Local/Microsoft/WinGet/Packages/oschwartz10612.Poppler_Microsoft.Winget.Source_8wekyb3d8bbwe/poppler-25.07.0/Library/bin/pdftoppm.exe`. Use 150 dpi for data tables, 600 dpi for small numbered ones, and `-x -y -W -H` crops for counting boxes. SSD sheets are rotated 90°, a PDF page is not the printed page number, and a large sheet may span two facing pages — check the contents list for a page RANGE before concluding a base has four shields.

Both directories are gitignored: these are ADB's watermarked product and must never be committed. Verify with `git ls-files | grep -ci "\.pdf$"` before any push.

Open work is listed in [BACKLOG.md](BACKLOG.md), grouped by readiness. It records what is NOT done; the git log records what is. Check the code before believing any "does not exist" claim in it.

## Commands

```bash
# Build everything + run all tests (from repo root)
mvn clean install

# Run one test class / one test method
mvn test -pl amarillo-core -Dtest=TractorTest
mvn test -pl amarillo-core -Dtest=TractorTest#rotate_succeeds_movesTargetToAdjacentHex

# Backend server (port 8080)
cd amarillo-server && mvn spring-boot:run

# Frontend dev server
cd amarillo-web && npm run dev

# Frontend lint / type-checked build
cd amarillo-web && npm run lint
cd amarillo-web && npm run build   # tsc -b && vite build
```

- Always build with `install` from the root, never `package` in a submodule: amarillo-server resolves amarillo-core from `~/.m2`, so a stale install means the server runs old core code. After changing core or server code, remind the user to rebuild and restart `spring-boot:run`.
- `amarillo-web` is npm-only — the root pom builds `amarillo-core` and `amarillo-server`.
- **`npm run build`, `npm run lint` and `npm test` are all green, and must stay that way.** This file used to say `tsc -b` had pre-existing errors to be disregarded; that instruction outlived its truth and did damage while it lasted. A real crash in `LaunchOrdersPad` — reading `attacker.ship` where a fighter can be the attacker — sat in plain sight because nobody could tell a new error from the accepted noise. Fixed 2026-10-09, with CI in `.github/workflows/ci.yml` to keep it so. The Vite dev server still does not type-check, so run the gates yourself before committing.

## Architecture

Three tiers, strictly layered:

- **amarillo-core** — the rules engine, plain Java, no Spring. All game logic lives here.
- **amarillo-server** — Spring Boot wrapper: REST + STOMP WebSocket. `GameSession` maps `ActionRequest.type` strings to Game method calls and broadcasts a `GameStateDto` snapshot after every action. No rules logic here beyond request validation — but those validations encode rule limits and silently drift when a rule changes in core (a pre-G7.6 tractor-energy cap survived two revisions this way). When changing a rule, grep the system's name across **all three tiers**, and update `GameSessionAllocateTest` when touching an ALLOCATE validation.
- **amarillo-web** — React/TypeScript frontend. A pure view: it renders the DTO and sends actions. It never computes rules outcomes (only mirrors them for previews, e.g. `weaponDamageTables.ts`, which must be updated whenever a new Java weapon class is added).

  Two traps where the DTO meets TypeScript, both of which have shipped bugs:
  a **primitive** Java field (`int`, `boolean`) is always serialized, so a field that applies to one weapon type arrives as `0` on every weapon — use `Integer`/`Boolean` so "not applicable" can be said. And Jackson serializes null as `"field": null` rather than omitting the key, so a client guard must be `!= null`, never `!== undefined`: `null !== undefined` is true.

### Game.java is the aggregate root

`com.sfb.Game` owns all authoritative state (ships, seekers, shuttles, mines, terrain, phase, pending damage/choices) and the turn sequence: `startTurn()` → per-ship energy allocation queue → `beginImpulses()` → the `advancePhase()` loop → `endTurn()`. Nothing outside core mutates ship state directly; every action goes through a Game method returning `ActionResult`.

Turns run 32 impulses (each Game owns a `TurnTracker` clock instance (`game.getClock()`); "absolute" vs "local" impulse. Never make it static — deep systems get the clock injected via `Ship.attachClock()`, and `GameIsolationTest` pins cross-game independence). Each impulse cycles `ImpulsePhase`: `MOVEMENT → ACTIVITY → DIRECT_FIRE → END_OF_IMPULSE`, with interrupt phases (`REINFORCEMENT`, `DAC_CHOICE`, `CONTROL_OVERFLOW`) that return to where they interrupted. `INITIAL_ACTIVITY` runs once at turn start, and only when tractor links exist.

### Resolver extraction pattern

Game's domain logic lives in package-private collaborator classes in `com.sfb`. There are fourteen as of 2026-10-09: `ShipMover`, `SeekerMover`, `ShuttleMover`, `TractorResolver`, `DamageResolver`, `BoardingResolver`, `LaunchCoordinator`, `MineResolver`, `SeekerControl`, `LockOnResolver`, `DisengagementResolver`, `EsgResolver`, `SquadronEwResolver`, `VictoryCalculator`. Game itself holds state, the phase machine, allocation, victory, and public delegates.

**Game.java is 4,715 lines, of which about 2,894 are code** — this file claimed ~1,900 for long enough that an outside reviewer reasonably concluded decomposition had stalled. It has not: three of the fourteen collaborators above arrived after that number was written. But Game did grow by roughly a thousand code lines in the meantime, so the direction of the pressure is real. Re-measure before quoting a size here, and treat raw line counts across this repo with care — the comment density is deliberate and runs 30–40%, so a file's length overstates its logic by about a third.

- Pending/shared lists stay **declared in Game** and are passed into the resolver constructor by reference.
- Phase transitions stay **in Game**, reached via package-private hooks (e.g. `enterDacChoicePhase()`).
- Game keeps one-line public delegates so the server and tests are unaffected.
- Never cache `Game.internalDamageLog()` — Game replaces that list each resolution step; always call the accessor.
- One extraction per commit; extract opportunistically when touching a domain, not as big-bang refactors.

### Hex grid geometry

The map uses offset coordinates (`Location`, serialized as `"<x|y>"`) with **column-parity-sensitive** neighbors. Never move a unit by a raw `(dx, dy)` delta — it breaks shape/range across odd/even columns. Use `MapUtils.getAdjacentHex(loc, direction, maxCols, maxRows)` (returns null off-map) and direction-based drag helpers. Bearings use the zone-based spine-line algorithm in `MapUtils`, never geometric atan2. Directions are the six bearings `{1, 5, 9, 13, 17, 21}` (A–F).

### Server action protocol

`ActionRequest` is one class with named per-action field groups (`tractorBid`, `facing`, `crewAmount`, …). `hexCol`/`hexRow` are the shared destination-hex pair for hex-targeted actions (`ROTATE_TRACTORED`, `PLACE_TBOMB`) — add named fields for new actions rather than reusing unrelated ones. Primitive int fields default to 0 when a client omits them, so handlers validate `>= 1` for hex coordinates.

### Ship data and tests

Ship definitions live as JSON (loaded via `ShipLibrary`), but tests build ships from `com.sfb.samples` builders (e.g. `FederationShips.getFedCa()`) so no filesystem access is needed. **When a ship JSON changes, update the matching sample builder** — otherwise tests silently diverge from the real data.

Tests that need the impulse engine add ships to a `Game` (each Game has a fresh clock; no reset ritual needed), call `game.startTurn()`, then submit allocations to trigger `beginImpulses()`. Tractor links must be established *before* the last allocation to open the `INITIAL_ACTIVITY` phase (they persist across turns and are maintained at turn start per G7.42).
