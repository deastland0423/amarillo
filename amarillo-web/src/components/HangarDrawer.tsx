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

/** One fighter box, in the order the ship fills them (Shuttles.rechargeCapacitors). */
interface BoxRow {
  key: string;
  label: string;
  charges: number;
  capacity: number;
}

function capacitorBoxes(ship: ShipObject): BoxRow[] {
  return (ship.shuttleBays ?? []).flatMap(bay =>
    (bay.spaces ?? [])
      .filter(sp => (sp.capacitorCapacity ?? 0) > 0)
      .map(sp => ({
        key:      `${bay.bayIndex}-${sp.spaceIndex}`,
        label:    `Bay ${bay.bayIndex + 1} · ${sp.shuttle?.name ?? 'empty box'}`,
        charges:  sp.capacitorCharges ?? 0,
        capacity: sp.capacitorCapacity ?? 0,
      })));
}

export function HangarDrawer({
  ship, anchor, fighterCaps, onFighterCaps, spent, total,
}: {
  ship: ShipObject;
  anchor: { left: number; top: number };
  fighterCaps: number;
  onFighterCaps: (points: number) => void;
  spent: number;
  total: number;
}) {
  const [collapsed, setCollapsed] = useStickyCollapse('hangar-drawer-collapsed');

  const boxes = capacitorBoxes(ship);
  const room = ship.fighterCapacitorRoom ?? 0;

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

  const pending = room > 0 ? ` · ${room}` : '';

  return (
    <>
      <button
        className={flip ? 'hangar-tab flip' : 'hangar-tab'}
        style={{ left: tabLeft, top: anchor.top + 56 }}
        title={room > 0
          ? `${room} point(s) of fighter capacitor capacity to buy`
          : 'Hangar operations'}
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
            <div className="ea-section-title" style={{ color: '#7ee0a8' }}>
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
                {boxes.map(b => (
                  <tr key={b.key} className={b.charges < b.capacity ? 'hangar-row-short' : ''}>
                    <td>{b.label}</td>
                    <td className="hangar-num">
                      {b.charges}/{b.capacity}{b.charges >= b.capacity ? ' (full)' : ''}
                    </td>
                  </tr>
                ))}
                {boxes.length === 0 && (
                  <tr><td colSpan={2} className="ea-note">
                    No weapon capacitors aboard — this ship carries no Hydran fighters.
                  </td></tr>
                )}
              </tbody>
            </table>

            <div className="ea-note hangar-todo">
              Deck crew orders — load a fighter, repair a shuttle, load a scatter pack, fill a
              ready rack — will live here too. They spend crews rather than power, so they get
              their own table.
            </div>
          </div>
        </div>
      )}
    </>
  );
}
