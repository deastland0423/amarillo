import type { ShipObject } from '../types/gameState';
import { useStickyCollapse } from '../hooks/useStickyCollapse';
import { Stepper } from './Stepper';

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

const DRAWER_WIDTH_PX = 460;
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
  crewsWanted: number;
  work: number;
}

function boxRows(ship: ShipObject): BoxRow[] {
  return (ship.shuttleBays ?? []).flatMap(bay =>
    (bay.spaces ?? []).map(sp => ({
      key:         `${bay.bayIndex}-${sp.spaceIndex}`,
      label:       `Bay ${bay.bayIndex + 1} · ${sp.shuttle?.name ?? 'empty box'}`,
      charges:     sp.capacitorCharges ?? 0,
      capacity:    sp.capacitorCapacity ?? 0,
      crewsWanted: sp.crewsWanted ?? 0,
      work:        sp.workOutstanding ?? 0,
    })));
}

/**
 * What a box is short, in the units a player thinks in. Work is counted in half-actions —
 * a fusion charge is one, a drone space two (J4.833/J4.82) — which is the right currency for
 * the crews and the wrong one for the label.
 */
function shortfall(box: BoxRow): string {
  if (box.work <= 0) return 'ready';
  return box.capacity === 1 ? 'hellbore empty' : `${box.work} charge(s) short`;
}

export function HangarDrawer({
  ship, anchor, fighterCaps, onFighterCaps, crewPostings, onCrewPostings, spent, total,
}: {
  ship: ShipObject;
  anchor: { left: number; top: number };
  fighterCaps: number;
  onFighterCaps: (points: number) => void;
  crewPostings: Record<string, number>;
  onCrewPostings: (next: Record<string, number>) => void;
  spent: number;
  total: number;
}) {
  const [collapsed, setCollapsed] = useStickyCollapse('hangar-drawer-collapsed');

  const boxes = boxRows(ship);
  const capacitorBoxes = boxes.filter(b => b.capacity > 0);
  const workBoxes = boxes.filter(b => b.crewsWanted > 0);
  const room = ship.fighterCapacitorRoom ?? 0;

  // The crews are their own budget — no energy, so nothing here touches the bar above.
  const crews = ship.availableDeckCrews ?? 0;
  const postedTotal = Object.values(crewPostings).reduce((a, b) => a + b, 0);
  const crewsFree = Math.max(0, crews - postedTotal);

  function post(boxKey: string, n: number) {
    const next = { ...crewPostings };
    if (n <= 0) delete next[boxKey];
    else next[boxKey] = n;
    onCrewPostings(next);
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
  const needy = boxes.filter(b => b.crewsWanted > 0 || b.charges < b.capacity).length;
  const pending = needy > 0 ? ` · ${needy}` : '';

  return (
    <>
      <button
        className={flip ? 'hangar-tab flip' : 'hangar-tab'}
        style={{ left: tabLeft, top: anchor.top + 56 }}
        title={needy > 0
          ? `${needy} fighter box(es) want attention — ${room} point(s) of capacitor capacity`
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
              A crew posted to a box works there all turn: two crews reload a Stinger, one a
              hellbore (J4.833/J4.834). They are killed if that box is destroyed (J4.811), and
              launching the fighter they are working on wastes the work (J4.8174). Post
              nobody and the ship decides for itself, top down.
            </div>

            {workBoxes.length === 0 ? (
              <div className="ea-note">
                Every fighter aboard is armed, so the crews have nothing to do until one
                spends its charges. They cost nothing to leave idle.
              </div>
            ) : (
              <table className="hangar-table">
                <thead>
                  <tr><th>Box</th><th>Needs</th><th className="hangar-num">Crews</th></tr>
                </thead>
                <tbody>
                  {workBoxes.map(b => {
                    const posted = crewPostings[b.key] ?? 0;
                    return (
                      <tr key={b.key} className={posted > 0 ? 'hangar-row-posted' : ''}>
                        <td>{b.label}</td>
                        <td>{shortfall(b)}</td>
                        <td className="hangar-num">
                          <button className="ea-step-btn" disabled={posted <= 0}
                            onClick={() => post(b.key, posted - 1)}>−</button>
                          <span className="hangar-posted">{posted}</span>
                          <button className="ea-step-btn"
                            disabled={posted >= b.crewsWanted || crewsFree <= 0}
                            onClick={() => post(b.key, posted + 1)}>+</button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}

            <div className="ea-section-title" style={{ color: '#7ee0a8', marginTop: '0.6rem' }}>
              Weapon Capacitors
            </div>
            <div className="ea-note">
              {room > 0
                ? '1 point a fusion charge (J4.832); a hellbore box takes 2 a turn and needs'
                  + ' two turns for its one charge (J4.834). Boxes fill top down, so the'
                  + ' points buy one fighter a full sortie rather than a squadron half a one.'
                : 'Every fighter box is full — nothing to buy this turn.'}
            </div>

            {room > 0 && (
              <Stepper value={fighterCaps} min={0} max={room}
                onChange={onFighterCaps}
                label="point(s) into the fighter boxes" />
            )}

            <table className="hangar-table">
              <thead>
                <tr><th>Box</th><th>Capacitor</th></tr>
              </thead>
              <tbody>
                {capacitorBoxes.map(b => (
                  <tr key={b.key} className={b.charges < b.capacity ? 'hangar-row-short' : ''}>
                    <td>{b.label}</td>
                    <td className="hangar-num">
                      {b.charges}/{b.capacity}{b.charges >= b.capacity ? ' (full)' : ''}
                    </td>
                  </tr>
                ))}
                {capacitorBoxes.length === 0 && (
                  <tr><td colSpan={2} className="ea-note">
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
