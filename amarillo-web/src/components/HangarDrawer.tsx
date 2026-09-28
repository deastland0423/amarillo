import { useState } from 'react';
import type React from 'react';
import type { ShipObject, ShuttleBayState, ShuttleInBayState, ShuttleSpaceState }
  from '../types/gameState';
import { useStickyCollapse } from '../hooks/useStickyCollapse';

/**
 * Hangar operations: everything done to the craft in a ship's bays at allocation time.
 *
 * Laid out like the SSD — a section per bay, a row per box — because that is what the player
 * is already looking at, and because a bay is a real object in the rules rather than a
 * grouping we invented: its own hatch and cooldown, its own launch tubes, chain reactions
 * confined to it (D12.112). Arranging the panel any other way means translating.
 *
 * It also puts everything about one box in one place. Ask "what about that Stinger" and the
 * answer is one row: what it holds, what it is short, who is working on it, what its
 * capacitor has. The previous arrangement scattered those across three sections.
 *
 * Two budgets run through the panel and neither belongs to a section, so both are pinned to
 * the header: deck crews (J4.81) for loading, unloading and repairing, and ship energy for
 * capacitors, suicide arming and weasel charging. A row may spend either, and the totals stay
 * visible while the player works anywhere down the list.
 *
 * A drawer rather than a window of its own because it and Energy Allocation are one form: the
 * energy comes out of the same budget and goes up in the same ALLOCATE, so the Commit button
 * has to stay attached to the thing spending from it.
 */

/** Must match the width of .ea-dialog in App.css — the drawer is positioned off its edge. */
export const EA_WIDTH_PX = 300;

const DRAWER_WIDTH_PX = 540;
const TAB_WIDTH_PX = 28;

/** J4.8172: two deck crews on one fighter and no more, however many jobs they split between. */
const MAX_CREWS_PER_BOX = 2;

/**
 * J4.8172 again: "two MORE deck crews can load the ready rack in that box". Refilling has
 * its own pair and does not eat into the two working the fighter, so the box's ceiling is
 * counted twice over — once for fighter work, once for the rack.
 */
const RACK_TASKS = new Set(['REFILL']);

/**
 * J4.8172 once more: two crews to a box, one drone space each (FD7.22), so two SPACES of
 * drones onto a scatter pack in a turn — however many crews the ship has idle. Mirrors
 * ScatterPack.MAX_SPACES_LOADED_PER_TURN, which is what actually enforces it.
 */
const PACK_SPACES_PER_TURN = 2;

const TASK_LABEL: Record<string, string> = {
  LOAD:   'load',
  UNLOAD: 'unload',
  REPAIR: 'repair',
  REFILL: 'refill rack',
};

/** The box's id on the wire — bay index and space index, as Shuttles.boxId() builds it. */
function boxId(bay: ShuttleBayState, space: ShuttleSpaceState): string {
  return `${bay.bayIndex}-${space.spaceIndex}`;
}

/**
 * What is in the box, in one phrase. An empty box and a destroyed one are different things
 * and a player needs to tell them apart at a glance.
 */
function occupantLine(space: ShuttleSpaceState, shipName: string): string {
  if (space.destroyed) return 'destroyed';
  const s = space.shuttle;
  if (!s) return 'empty';
  return s.name.replace(`${shipName}-`, '');
}

/**
 * How near this craft is to being ready to fly a mission — the thing a carrier captain is
 * scanning the list for, and the reason the row carries a coloured dot.
 * <p>
 * Null means the question does not apply: an admin shuttle has nothing to arm, and a dot on
 * it would be inventing a distinction the rules do not draw.
 */
function readiness(space: ShuttleSpaceState): 'ready' | 'partial' | 'empty' | null {
  const s = space.shuttle;
  if (!s || space.destroyed) return null;
  const rails = s.rails ?? [];
  const capacity = space.capacitorCapacity ?? 0;
  if (rails.length === 0 && capacity === 0) return null;   // nothing to arm

  const loaded = (space.chargesAboard ?? 0) + rails.filter(r => r.drone).length;
  if (loaded === 0) return 'empty';
  return (space.workOutstanding ?? 0) === 0 ? 'ready' : 'partial';
}

const READY_TITLE: Record<string, string> = {
  ready:   'Armed and ready',
  partial: 'Partly armed — deck crew work outstanding',
  empty:   'Unarmed',
};

/** What is on the rails, grouped: "2x TypeI". Empty string when it carries no drones. */
function droneSummary(s: ShuttleInBayState): string {
  const loaded = (s.rails ?? []).filter(r => r.drone);
  if (loaded.length === 0) return '';
  const byType = new Map<string, number>();
  for (const r of loaded) byType.set(r.drone!, (byType.get(r.drone!) ?? 0) + 1);
  return [...byType].map(([t, n]) => (n > 1 ? `${n}x ${t}` : t)).join(', ');
}

/**
 * Rail by rail, for the tooltip — the detail the summary folds away. Shows the EMPTY rails
 * too, and what size they are, because "which slot is still open and what will go in it" is
 * the question a half-loaded fighter actually raises.
 */
function railDetail(s: ShuttleInBayState): string {
  const rails = s.rails ?? [];
  if (rails.length === 0) return '';
  return rails
    .map(r => `${(r.railType ?? '?').toLowerCase()}: ${r.drone ?? 'empty'}`)
    .join('\n');
}

/** What state it is in: charges aboard, damage, and any special role it is playing. */
function stateLine(space: ShuttleSpaceState): string {
  if (space.destroyed) return '';
  // An empty box is not a blank row: its ready rack is still there and still stockable
  // while the fighter is away (J4.8223), which is the whole point of the refill job.
  if (!space.shuttle) {
    return space.readyRackCapacity != null
      ? `ready rack ${space.readyRackCount ?? 0}/${space.readyRackCapacity}`
      : '';
  }
  const bits: string[] = [];
  const s = space.shuttle;

  // What it is carrying comes first: it is the answer to "can this thing fly a mission",
  // which is what the row is being scanned for.
  if ((s.rails ?? []).length > 0)
    bits.push(droneSummary(s) || 'no drones');
  if (space.readyRackCapacity != null)
    bits.push(`ready rack ${space.readyRackCount ?? 0}/${space.readyRackCapacity}`);
  if ((space.capacitorCapacity ?? 0) > 0) {
    bits.push(`${space.chargesAboard ?? 0} loaded`);
    bits.push(`capacitor ${space.capacitorCharges ?? 0}/${space.capacitorCapacity}`);
  }
  if (s.specialRole) bits.push(s.specialRole);
  const turns = s.armingTurnsComplete ?? 0;
  if (turns > 0)
    bits.push(turns >= 3 ? `armed, dmg ${s.warheadDamage ?? 0}` : `arming ${turns}/3`);
  if (s.wwChargeCount != null && s.wwChargeCount > 0)
    bits.push(s.wwReady ? 'weasel ready' : `weasel ${s.wwChargeCount}/2`);
  if ((space.damage ?? 0) > 0) bits.push(`${space.damage} damage`);
  return bits.join(', ');
}

function Stepper({ value, onChange, min, max, label }: {
  value: number; onChange: (n: number) => void; min: number; max: number; label: string;
}) {
  return (
    <span className="hangar-ctl">
      <span className="hangar-ctl-label">{label}</span>
      <button className="ea-step-btn" disabled={value <= min}
        onClick={() => onChange(value - 1)}>−</button>
      <span className="hangar-posted">{value}</span>
      <button className="ea-step-btn" disabled={value >= max}
        onClick={() => onChange(value + 1)}>+</button>
    </span>
  );
}

/** One section of the panel, remembered open or closed, saying what is behind it when shut. */
function Section({ title, colour, summary, storageKey, startOpen = false, children }: {
  title: string;
  colour: string;
  summary?: string;
  storageKey: string;
  startOpen?: boolean;
  children: React.ReactNode;
}) {
  const [collapsed, setCollapsed] = useStickyCollapse(storageKey, !startOpen);
  return (
    <div className="hangar-section">
      <button className="hangar-section-head" style={{ color: colour }}
        onClick={() => setCollapsed(!collapsed)}>
        <span>{collapsed ? '▶' : '▼'} {title}</span>
        {summary && <span className="hangar-section-summary">{summary}</span>}
      </button>
      {!collapsed && <div className="hangar-section-body">{children}</div>}
    </div>
  );
}

export function HangarDrawer({
  ship, anchor, capsByBox, onCapsByBox, crewPostings, onCrewPostings,
  packLoading, onPackLoading, suicideArming, suicideHold, onSuicide,
  wwCharge, onWwCharge, spent, total,
}: {
  ship: ShipObject;
  anchor: { left: number; top: number };
  capsByBox: Record<string, number>;
  onCapsByBox: (next: Record<string, number>) => void;
  crewPostings: Record<string, number>;
  onCrewPostings: (next: Record<string, number>) => void;
  packLoading: Record<string, Record<string, number>>;
  onPackLoading: (next: Record<string, Record<string, number>>) => void;
  suicideArming: Record<string, number>;
  suicideHold: Record<string, boolean>;
  onSuicide: (arming: Record<string, number>, hold: Record<string, boolean>) => void;
  wwCharge: Set<string>;
  onWwCharge: (next: Set<string>) => void;
  spent: number;
  total: number;
}) {
  const [collapsed, setCollapsed] = useStickyCollapse('hangar-drawer-collapsed');
  const [expanded, setExpanded] = useState<string | null>(null);

  const bays = ship.shuttleBays ?? [];

  // What a scatter pack can be filled from: the reload sets the ship's drone racks carry.
  const stockpile: Record<string, { rackSize: number; count: number }> = {};
  for (const rack of ship.droneRacks ?? [])
    for (const entry of rack.reloadPool ?? []) {
      if (!stockpile[entry.droneType])
        stockpile[entry.droneType] = { rackSize: entry.rackSize, count: 0 };
      stockpile[entry.droneType].count += entry.count;
    }
  const spacesIn = (sel: Record<string, number>) =>
    Object.entries(sel).reduce((sum, [dt, n]) => sum + (stockpile[dt]?.rackSize ?? 1) * n, 0);

  // One pool, several claims: FD7.22's pack loading and J4.817's postings draw on the same
  // deck crews, which is why both totals live in the header rather than in their own sections.
  const crews = ship.availableDeckCrews ?? 0;
  const postedTotal = Object.values(crewPostings).reduce((a, b) => a + b, 0);
  const packCrews = Object.values(packLoading ?? {}).reduce((a, sel) => a + spacesIn(sel), 0);
  const crewsFree = Math.max(0, crews - postedTotal - packCrews);

  const wantsAttention = (sp: ShuttleSpaceState) =>
    Object.keys(sp.crewJobs ?? {}).length > 0
    || (sp.capacitorCharges ?? 0) < (sp.capacitorCapacity ?? 0)
    || (sp.readyRackCapacity != null && (sp.readyRackCount ?? 0) < sp.readyRackCapacity);

  // The tab's count: whether the panel is worth opening at all.
  const needy = bays.flatMap(b => b.spaces ?? []).filter(wantsAttention).length;

  if (bays.length === 0)
    return null;

  // useDraggable lets a panel hang off the right edge, so the drawer would open into nothing
  // there. Flip it to EA's left when the room is on that side instead.
  const viewport = typeof window === 'undefined' ? 1920 : window.innerWidth;
  const rightEdge = anchor.left + EA_WIDTH_PX;
  const flip = rightEdge + TAB_WIDTH_PX + DRAWER_WIDTH_PX > viewport
      && anchor.left - DRAWER_WIDTH_PX > 0;
  const tabLeft = flip ? anchor.left - TAB_WIDTH_PX : rightEdge;
  const drawerLeft = flip
      ? anchor.left - TAB_WIDTH_PX - DRAWER_WIDTH_PX
      : rightEdge + TAB_WIDTH_PX;

  function postCrews(jobKey: string, n: number) {
    const next = { ...crewPostings };
    if (n <= 0) delete next[jobKey];
    else next[jobKey] = n;
    onCrewPostings(next);
  }

  function buyCharges(key: string, n: number) {
    const next = { ...capsByBox };
    if (n <= 0) delete next[key];
    else next[key] = n;
    onCapsByBox(next);
  }

  /**
   * Crews posted in this box to one KIND of work — J4.8172 gives the fighter two and the
   * ready rack two more, so they are counted against separate ceilings.
   */
  function crewsInBox(key: string, rackWork: boolean): number {
    return Object.entries(crewPostings)
      .filter(([k]) => k.startsWith(`${key}:`)
          && RACK_TASKS.has(k.slice(key.length + 1)) === rackWork)
      .reduce((sum, [, n]) => sum + n, 0);
  }

  /** The controls a box offers, which depend entirely on what is sitting in it. */
  function controlsFor(bay: ShuttleBayState, space: ShuttleSpaceState) {
    const key = boxId(bay, space);
    const s = space.shuttle;
    const out: React.ReactNode[] = [];
    // A destroyed box is finished with. An EMPTY one is not: its rack can still be restocked
    // while its fighter is on its mission (J4.8223), and the server offers that job.
    if (space.destroyed || (!s && space.readyRackCapacity == null)) return out;

    // Deck crew jobs (J4.817) — one control each, capped at two on the fighter and two more
    // on the rack (J4.8172).
    for (const [task, maxCrews] of Object.entries(space.crewJobs ?? {})) {
      const jobKey = `${key}:${task}`;
      const posted = crewPostings[jobKey] ?? 0;
      const boxRoom =
        MAX_CREWS_PER_BOX - crewsInBox(key, RACK_TASKS.has(task)) + posted;
      out.push(
        <Stepper key={jobKey} value={posted} min={0}
          max={Math.min(maxCrews, boxRoom, posted + crewsFree)}
          onChange={n => postCrews(jobKey, n)}
          label={TASK_LABEL[task] ?? task.toLowerCase()} />);
    }

    // Everything below needs something sitting in the box. Refilling the rack was the one
    // job that did not, and it is already on the list.
    if (!s) return out;

    // The box's own capacitor (J4.832), which serves only the fighter in it (J4.881).
    if ((space.capacitorCapacity ?? 0) > 0) {
      const bought = capsByBox[key] ?? 0;
      out.push(
        <Stepper key={`${key}:cap`} value={bought} min={0} max={space.capacitorRoom ?? 0}
          onChange={n => buyCharges(key, n)}
          label="charge capacitor" />);
    }

    // Suicide arming (1–3 energy a turn for three turns) or the 1-point hold once armed.
    const turns = s.armingTurnsComplete ?? 0;
    if (s.type === 'suicide' || s.type === 'admin') {
      if (turns >= 3) {
        const holding = suicideHold?.[s.name] ?? false;
        out.push(
          <label key={`${key}:hold`} className="hangar-ctl hangar-check">
            <input type="checkbox" checked={holding}
              onChange={e => onSuicide(suicideArming,
                { ...suicideHold, [s.name]: e.target.checked })} />
            hold (1)
          </label>);
      } else {
        const energy = suicideArming?.[s.name] ?? 0;
        out.push(
          <Stepper key={`${key}:arm`} value={energy} min={0} max={3}
            onChange={n => onSuicide({ ...suicideArming, [s.name]: n }, suicideHold)}
            label="arm suicide" />);
      }
    }

    // Wild weasel charging: a point a turn for two consecutive turns (J3.12).
    if (s.wwChargeCount != null) {
      const paying = wwCharge.has(s.name);
      out.push(
        <label key={`${key}:ww`} className="hangar-ctl hangar-check">
          <input type="checkbox" checked={paying}
            onChange={e => {
              const next = new Set(wwCharge);
              if (e.target.checked) next.add(s.name); else next.delete(s.name);
              onWwCharge(next);
            }} />
          {s.wwReady ? 'keep weasel (1)' : 'charge weasel (1)'}
        </label>);
    }

    // Loading a pack is a drone-type picker, not a number, so it opens in place rather than
    // trying to live on the row.
    // Anything that MAY be a pack (FD7.11 says so by sending a capacity), given there is
    // something in the racks to put in it. Not a list of type names: that list said admin
    // and scatterpack, and quietly excluded every fighter the rule allows.
    if (s.maxDroneSpaces != null && Object.keys(stockpile).length > 0) {
      const here = spacesIn(packLoading?.[s.name] ?? {});
      out.push(
        <button key={`${key}:pack`} className="hangar-link"
          onClick={() => setExpanded(expanded === key ? null : key)}>
          {expanded === key ? 'close' : 'load pack'}{here > 0 ? ` (${here})` : ''}
        </button>);
    }
    return out;
  }

  /** The drone picker for one pack, shown under its row while it is open. */
  function packPicker(space: ShuttleSpaceState) {
    const s = space.shuttle;
    if (!s) return null;
    const sel = packLoading?.[s.name] ?? {};
    // The craft says its own capacity (FD7.11); a craft that cannot be a pack says nothing,
    // and there is nothing to draw. Never defaulted to a number — a guessed six is how a
    // capacity that belongs to the type became a constant in the client.
    if (s.maxDroneSpaces == null) return null;
    const max = s.maxDroneSpaces;
    const already = s.committedSpaces ?? 0;
    const here = spacesIn(sel);
    return (
      <div className="hangar-expand">
        <div className="ea-note">
          A crew loads one rack space onto a pack (FD7.22), drawn from the drone racks' reload
          sets — so every drone here is a crew not working a fighter, and a reload the racks
          will not have later. Two crews to a box is two spaces a turn (J4.8172), so a full
          admin shuttle takes three. {(already + here).toFixed(1)} / {max} spaces,
          {' '}{here.toFixed(1)} / {PACK_SPACES_PER_TURN} this turn.
        </div>
        {Object.entries(stockpile).map(([dt, info]) => {
          const n = sel[dt] ?? 0;
          const roomOnPack = already + here + info.rackSize <= max;
          const roomThisTurn = here + info.rackSize <= PACK_SPACES_PER_TURN;
          const canAdd = n < info.count && roomOnPack && roomThisTurn
            && info.rackSize <= crewsFree;
          return (
            <div key={dt} className="ea-stepper">
              <button className="ea-step-btn" disabled={n <= 0}
                onClick={() => onPackLoading({ ...packLoading,
                  [s.name]: { ...sel, [dt]: Math.max(0, n - 1) } })}>−</button>
              <span className="ea-step-value">{n}</span>
              <button className="ea-step-btn" disabled={!canAdd}
                onClick={() => onPackLoading({ ...packLoading,
                  [s.name]: { ...sel, [dt]: n + 1 } })}>+</button>
              <span className="ea-step-label">
                {dt.replace('Type', 'Type ')}
                <span className="ea-note-dim">
                  {' '}({info.count} left, {info.rackSize} space
                  {info.rackSize !== 1 ? 's' : ''} each)
                </span>
              </span>
            </div>
          );
        })}
      </div>
    );
  }

  /** What a bay's header says when it is shut: what it holds, and whether its hatch is free. */
  function baySummary(bay: ShuttleBayState): string {
    const busy = (bay.spaces ?? []).filter(wantsAttention).length;
    const hatch = bay.canLaunch ? 'hatch ready' : 'hatch cooling';
    const tubes = bay.launchTubeCount > 0
      ? ` · ${bay.availableTubes}/${bay.launchTubeCount} tubes` : '';
    return `${busy > 0 ? `${busy} want attention · ` : ''}${hatch}${tubes}`;
  }

  return (
    <>
      <button
        className={flip ? 'hangar-tab flip' : 'hangar-tab'}
        style={{ left: tabLeft, top: anchor.top + 56 }}
        title={needy > 0
          ? `${needy} fighter box(es) want attention`
          : 'Hangar operations — everything aboard is ready'}
        onClick={() => setCollapsed(!collapsed)}
      >
        <span className="hangar-tab-text">
          HANGAR OPERATIONS{needy > 0 ? ` · ${needy}` : ''}
        </span>
      </button>

      {!collapsed && (
        <div className="hangar-drawer" style={{ left: drawerLeft, top: anchor.top }}>
          <div className="hangar-drawer-head">
            <span>Hangar Operations — {ship.name}</span>
            {/* Both budgets, pinned: a row may spend either, and the totals have to stay
                visible while the player works anywhere down the list. */}
            <span className="hangar-budget">
              deck crews {crewsFree}/{crews}
              {/* J4.7: the ship's own supply of spare drones, in spaces. Pinned beside the
                  crews because it is the other thing a refill spends, and the one that does
                  not come back — it says how many more strikes this carrier can mount. */}
              {ship.droneStorageSpaces != null && (<>
                <span className="ea-note-dim"> · </span>
                <span title="J4.7: spaces of spare drones still in the hold">
                  drones {(ship.droneStorageHeld ?? 0)}/{ship.droneStorageSpaces}
                </span>
              </>)}
              <span className="ea-note-dim"> · </span>
              <span className={spent > total ? 'over' : ''}>{spent.toFixed(1)}/{total}</span>
            </span>
          </div>

          <div className="hangar-drawer-body">
            {bays.map(bay => (
              <Section key={bay.bayIndex} title={`Bay ${bay.bayIndex + 1}`} colour="#f0c040"
                storageKey={`hangar-bay-${bay.bayIndex}`} startOpen
                summary={baySummary(bay)}>
                {(bay.spaces ?? []).map(space => {
                  const key = boxId(bay, space);
                  const controls = controlsFor(bay, space);
                  const ready = readiness(space);
                  return (
                    <div key={key}
                      className={'hangar-box'
                        + (space.destroyed ? ' destroyed' : '')
                        + (wantsAttention(space) ? ' wants' : '')}>
                      <div className="hangar-box-line">
                        <span className="hangar-box-name">
                          {ready && <span className={`hangar-dot ${ready}`}
                            title={READY_TITLE[ready]} />}
                          {occupantLine(space, ship.name)}
                        </span>
                        <span className="hangar-box-state"
                          title={space.shuttle ? railDetail(space.shuttle) : undefined}>
                          {stateLine(space)}
                        </span>
                      </div>
                      {controls.length > 0 && (
                        <div className="hangar-box-controls">{controls}</div>
                      )}
                      {expanded === key && packPicker(space)}
                    </div>
                  );
                })}
              </Section>
            ))}

            <div className="ea-note hangar-todo">
              Launching stays in the Launch Orders pad — this panel is what a bay does
              between turns, not during one.
            </div>
          </div>
        </div>
      )}
    </>
  );
}
