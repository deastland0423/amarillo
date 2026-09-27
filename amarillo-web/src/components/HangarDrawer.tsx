import type { ShipObject } from '../types/gameState';
import { useStickyCollapse } from '../hooks/useStickyCollapse';

/**
 * Hangar operations: the wide half of Energy Allocation, in a drawer off its right edge.
 *
 * Everything here is one row per object — a fighter box, a shuttle, a ready rack — while the
 * EA column is one number per ship. A Kzinti CV's twelve fighter boxes need a table, and the
 * EA dialog is 300px wide; that mismatch is why this is a drawer rather than another section.
 *
 * A drawer and not a separate window because the two are genuinely one form. Capacitor
 * charges are bought out of the same power budget as everything in EA and submitted in the
 * same ALLOCATE, so the budget bar and the Commit button have to stay in the same object the
 * drawer is attached to. Dragging EA takes the drawer with it for the same reason.
 *
 * Still to come, once the server can take the orders: assigning deck crews to load a fighter
 * (J4.83), repair a shuttle (J4.818), load a scatter pack (FD7.22) or fill a ready rack
 * (J4.82). Those spend a pool of crews rather than power, which is why they will be a table
 * of their own rather than more rows in this one.
 */

/** Must match the width of .ea-dialog in App.css — the drawer is positioned off its edge. */
export const EA_WIDTH_PX = 300;

const DRAWER_WIDTH_PX = 540;
const TAB_WIDTH_PX = 28;

/**
 * One fighter box. The id is what the server calls it — bay index and space index, the same
 * pair Shuttles.boxId() builds — so an order can name it.
 */
interface BoxRow {
  key: string;
  label: string;
  charges: number;
  capacity: number;
  room: number;
  crewsWanted: number;
  work: number;
  jobs: Record<string, number>;
}

/** One deck crew job: a box, a verb, and the crews it could take. */
interface JobRow {
  key: string;          // "1-3:LOAD" — what the order names
  boxKey: string;
  boxLabel: string;
  task: string;
  maxCrews: number;
  detail: string;
}

function boxRows(ship: ShipObject): BoxRow[] {
  return (ship.shuttleBays ?? []).flatMap(bay =>
    (bay.spaces ?? []).map(sp => ({
      key:         `${bay.bayIndex}-${sp.spaceIndex}`,
      // The ship's name prefixes every shuttle aboard it, and every row here is the same
      // ship — six wasted characters a row on a panel that wants the width for a job column.
      label:       `Bay ${bay.bayIndex + 1} · `
                   + (sp.shuttle?.name ?? 'empty box').replace(`${ship.name}-`, ''),
      charges:     sp.capacitorCharges ?? 0,
      capacity:    sp.capacitorCapacity ?? 0,
      room:        sp.capacitorRoom ?? 0,
      crewsWanted: sp.crewsWanted ?? 0,
      work:        sp.workOutstanding ?? 0,
      jobs:        sp.crewJobs ?? {},
    })));
}

/**
 * What a job would do, in the units a player thinks in. Work is counted in half-actions — a
 * fusion charge is one, a drone space two (J4.833/J4.82) — which is the right currency for
 * the crews and the wrong one for a label.
 */
function jobDetail(box: BoxRow, task: string): string {
  if (task === 'UNLOAD') return 'drones aboard';
  if (task === 'REPAIR') return 'damaged';
  if (box.capacity === 1) return 'hellbore empty';
  if (box.capacity === 0) return `${Math.round(box.work / 2)} drone(s) short`;
  return `${box.work} charge(s) short`;
}

const TASK_LABEL: Record<string, string> = {
  LOAD:   'load',
  UNLOAD: 'unload',
  REPAIR: 'repair',
};

/** Every job on the ship, in bay order, so a box's jobs sit together under its name. */
function jobRows(boxes: BoxRow[]): JobRow[] {
  const rows: JobRow[] = [];
  for (const box of boxes) {
    let first = true;
    for (const [task, maxCrews] of Object.entries(box.jobs)) {
      rows.push({
        key:      `${box.key}:${task}`,
        boxKey:   box.key,
        boxLabel: first ? box.label : '',
        task:     TASK_LABEL[task] ?? task.toLowerCase(),
        maxCrews,
        detail:   jobDetail(box, task),
      });
      first = false;
    }
  }
  return rows;
}

export function HangarDrawer({
  ship, anchor, capsByBox, onCapsByBox, crewPostings, onCrewPostings,
  packLoading, onPackLoading, spent, total,
}: {
  ship: ShipObject;
  anchor: { left: number; top: number };
  capsByBox: Record<string, number>;
  onCapsByBox: (next: Record<string, number>) => void;
  crewPostings: Record<string, number>;
  onCrewPostings: (next: Record<string, number>) => void;
  packLoading: Record<string, Record<string, number>>;
  onPackLoading: (next: Record<string, Record<string, number>>) => void;
  spent: number;
  total: number;
}) {
  const [collapsed, setCollapsed] = useStickyCollapse('hangar-drawer-collapsed');

  const boxes = boxRows(ship);
  const capacitorBoxes = boxes.filter(b => b.capacity > 0);
  const jobs = jobRows(boxes);

  // The crews are their own budget — no energy, so nothing here touches the bar above.
  const crews = ship.availableDeckCrews ?? 0;
  const postedTotal = Object.values(crewPostings).reduce((a, b) => a + b, 0);

  // What a scatter pack can be filled from: the reload sets the ship's drone racks carry.
  const stockpile: Record<string, { rackSize: number; count: number }> = {};
  for (const rack of ship.droneRacks ?? [])
    for (const entry of rack.reloadPool ?? []) {
      if (!stockpile[entry.droneType])
        stockpile[entry.droneType] = { rackSize: entry.rackSize, count: 0 };
      stockpile[entry.droneType].count += entry.count;
    }
  const packs = (ship.shuttleBays ?? []).flatMap(bay =>
    (bay.shuttles ?? []).filter(s => s.type === 'admin' || s.type === 'scatterpack'));
  const canLoadPacks = packs.length > 0 && Object.keys(stockpile).length > 0;

  const spacesIn = (sel: Record<string, number>) =>
    Object.entries(sel).reduce((sum, [dt, n]) => sum + (stockpile[dt]?.rackSize ?? 1) * n, 0);
  // FD7.22: a crew per rack space. The same crews the jobs above want — which is the whole
  // reason this table moved here from the energy column, where the two competed through a
  // counter neither screen showed.
  const packCrews = Object.values(packLoading ?? {}).reduce((a, sel) => a + spacesIn(sel), 0);
  const crewsFree = Math.max(0, crews - postedTotal - packCrews);

  function post(jobKey: string, n: number) {
    const next = { ...crewPostings };
    if (n <= 0) delete next[jobKey];
    else next[jobKey] = n;
    onCrewPostings(next);
  }

  /** J4.8172 caps the FIGHTER at two crews, however many jobs are running in its box. */
  function crewsInBox(boxKey: string): number {
    return jobs.filter(j => j.boxKey === boxKey)
      .reduce((sum, j) => sum + (crewPostings[j.key] ?? 0), 0);
  }

  function buy(boxKey: string, n: number) {
    const next = { ...capsByBox };
    if (n <= 0) delete next[boxKey];
    else next[boxKey] = n;
    onCapsByBox(next);
  }

  // A ship with no bay has no hangar to operate. Everything else gets the strip, even a
  // cruiser whose hangar work is one admin shuttle — the tab is thin and says how much
  // there is to do.
  if ((ship.shuttleBays ?? []).length === 0)
    return null;

  // useDraggable lets a panel hang off the right edge, so the drawer would open into
  // nothing there. Flip it to EA's left when the room is on that side instead.
  const viewport = typeof window === 'undefined' ? 1920 : window.innerWidth;
  const rightEdge = anchor.left + EA_WIDTH_PX;
  const flip = rightEdge + TAB_WIDTH_PX + DRAWER_WIDTH_PX > viewport
      && anchor.left - DRAWER_WIDTH_PX > 0;

  const tabLeft = flip ? anchor.left - TAB_WIDTH_PX : rightEdge;
  const drawerLeft = flip
      ? anchor.left - TAB_WIDTH_PX - DRAWER_WIDTH_PX
      : rightEdge + TAB_WIDTH_PX;

  // Boxes wanting attention of either kind — a crew, or charges to buy back. Counting only
  // the crew work left a WS-3 carrier with a silent tab and 36 points of capacity unbought,
  // which is exactly the case the badge exists for.
  const needy = boxes.filter(
    b => Object.keys(b.jobs).length > 0 || b.charges < b.capacity).length;
  const pending = needy > 0 ? ` · ${needy}` : '';

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
        <span className="hangar-tab-text">HANGAR OPERATIONS{pending}</span>
      </button>

      {!collapsed && (
        <div className="hangar-drawer" style={{ left: drawerLeft, top: anchor.top }}>
          <div className="hangar-drawer-head">
            <span>Hangar Operations — {ship.name}</span>
            {/* The same budget the EA bar shows: the points spent here come out of it. */}
            <span className={spent > total ? 'hangar-budget over' : 'hangar-budget'}>
              {spent.toFixed(1)} / {total}
            </span>
          </div>

          <div className="hangar-drawer-body">
            {/* ---- Deck crews (J4.817) ---- */}
            <div className="ea-section-title" style={{ color: '#f0c040' }}>
              Deck Crews — {crewsFree} of {crews} free
            </div>
            <div className="ea-note">
              A crew posted to a job works it all turn: two crews reload a Stinger, one a
              hellbore (J4.833/J4.834). Two may work the same fighter at different jobs, but
              never more than two on one fighter (J4.8172). They are killed if that box is
              destroyed (J4.811), and launching the fighter they are on wastes the work
              (J4.8174). Post nobody and the ship decides for itself, top down.
            </div>

            {jobs.length === 0 ? (
              <div className="ea-note">
                Every fighter aboard is armed, so the crews have nothing to do until one
                spends its charges. They cost nothing to leave idle.
              </div>
            ) : (
              <table className="hangar-table">
                <thead>
                  <tr>
                    <th>Box</th><th>Job</th><th></th><th className="hangar-num">Crews</th>
                  </tr>
                </thead>
                <tbody>
                  {jobs.map(j => {
                    const posted = crewPostings[j.key] ?? 0;
                    const boxFull = crewsInBox(j.boxKey) >= 2;
                    return (
                      <tr key={j.key} className={posted > 0 ? 'hangar-row-posted' : ''}>
                        <td>{j.boxLabel}</td>
                        <td className="hangar-task">{j.task}</td>
                        <td>{j.detail}</td>
                        <td className="hangar-num">
                          <button className="ea-step-btn" disabled={posted <= 0}
                            onClick={() => post(j.key, posted - 1)}>−</button>
                          <span className="hangar-posted">{posted}</span>
                          <button className="ea-step-btn"
                            disabled={posted >= j.maxCrews || crewsFree <= 0 || boxFull}
                            onClick={() => post(j.key, posted + 1)}>+</button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}

            {canLoadPacks && (
              <>
                <div className="ea-section-title"
                     style={{ color: '#79c0ff', marginTop: '0.6rem' }}>
                  Scatter Packs
                </div>
                <div className="ea-note">
                  A crew loads one rack space onto a pack (FD7.22), drawn from the drone racks'
                  reload sets — so every drone here is a crew not reloading a fighter above,
                  and a reload the racks will not have later.
                </div>
                {packs.map(pack => {
                  const sel = packLoading?.[pack.name] ?? {};
                  const max = pack.maxDroneSpaces ?? 6;
                  const already = pack.committedSpaces ?? 0;
                  const here = spacesIn(sel);
                  return (
                    <div key={pack.name} className="ea-weapon-alloc-block">
                      <div className="ea-weapon-alloc-name">
                        {pack.name.replace(`${ship.name}-`, '')}
                        <span className="ea-note-dim">
                          {' '}— {(already + here).toFixed(1)} / {max} spaces
                        </span>
                      </div>
                      {Object.entries(stockpile).map(([dt, info]) => {
                        const n = sel[dt] ?? 0;
                        const roomOnPack = already + here + info.rackSize <= max;
                        const canAdd = n < info.count && roomOnPack
                            && info.rackSize <= crewsFree;
                        return (
                          <div key={dt} className="ea-stepper">
                            <button className="ea-step-btn" disabled={n <= 0}
                              onClick={() => onPackLoading({ ...packLoading,
                                [pack.name]: { ...sel, [dt]: Math.max(0, n - 1) } })}>−</button>
                            <span className="ea-step-value">{n}</span>
                            <button className="ea-step-btn" disabled={!canAdd}
                              onClick={() => onPackLoading({ ...packLoading,
                                [pack.name]: { ...sel, [dt]: n + 1 } })}>+</button>
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
                })}
              </>
            )}

            <div className="ea-section-title" style={{ color: '#7ee0a8', marginTop: '0.6rem' }}>
              Weapon Capacitors
            </div>
            <div className="ea-note">
              Each box has its own capacitor and can only arm the fighter in it (J4.881), so
              buy them one at a time. A fusion charge is a point (J4.832); a hellbore box takes
              2 a turn and needs two turns for its single charge (J4.834). Four points into one
              box is a fighter's next sortie; one point into four boxes is nothing yet.
            </div>

            <table className="hangar-table">
              <thead>
                <tr><th>Box</th><th className="hangar-num">Capacitor</th><th className="hangar-num">Buy</th></tr>
              </thead>
              <tbody>
                {capacitorBoxes.map(b => {
                  const bought = capsByBox[b.key] ?? 0;
                  return (
                    <tr key={b.key} className={b.charges < b.capacity ? 'hangar-row-short' : ''}>
                      <td>{b.label}</td>
                      <td className="hangar-num">
                        {b.charges}/{b.capacity}{b.charges >= b.capacity ? ' (full)' : ''}
                      </td>
                      <td className="hangar-num">
                        {b.room > 0 ? (
                          <>
                            <button className="ea-step-btn" disabled={bought <= 0}
                              onClick={() => buy(b.key, bought - 1)}>−</button>
                            <span className="hangar-posted">{bought}</span>
                            <button className="ea-step-btn" disabled={bought >= b.room}
                              onClick={() => buy(b.key, bought + 1)}>+</button>
                          </>
                        ) : '—'}
                      </td>
                    </tr>
                  );
                })}
                {capacitorBoxes.length === 0 && (
                  <tr><td colSpan={3} className="ea-note">
                    No weapon capacitors aboard — this ship carries no Hydran fighters.
                  </td></tr>
                )}
              </tbody>
            </table>

            <div className="ea-note hangar-todo">
              Still to come in the crew table: repairing a damaged shuttle (J4.818), loading
              a scatter pack (FD7.22, which spends these same crews), and filling a ready
              rack once the ship has drone stores to fill it from (J4.82).
            </div>
          </div>
        </div>
      )}
    </>
  );
}
