import { useEffect, useState } from 'react';
import type { MapObject, ShieldState, ShipObject, WeaponState } from '../types/gameState';
import { facingLabel, facingToAngle, factionColor, parseLocation,
         shieldStrengthColor } from '../types/gameState';
import { bearsOn, hexGetBearingBetween, hexesInArc, hexRangeBetween, ringShieldNumber,
         turnFacing, type Hex } from '../hex/geometry';
import { useDraggable } from '../hooks/useDraggable';
import { weaponTitle } from './weaponTitle';
import WeaponFullTable from './WeaponFullTable';

/**
 * The SSD panel: a ship as it appears on its own record sheet, rather than as a token on
 * the map.
 *
 * Today it answers one question — where does this weapon actually point? — with a small
 * hex diagram instead of tinting the battle map. A diagram has two advantages over an
 * overlay: it cannot clutter the map, and it sidesteps range entirely. An arc and a range
 * are different rules, and an overlay that stopped at maximum range would be
 * indistinguishable from one that stopped at the edge of the arc.
 *
 * It is built as a container with slots because it is meant to grow into the ship status
 * readout. Built so far: identity, the arc diagram with the shield ring on it (the six hexes
 * at radius 1 ARE the six facings), a facing preview, the weapon list, and a systems block —
 * power, movement, hull, crew, command, support, special systems and the bays' contents.
 *
 * (This comment used to name shields as "the obvious next panel" long after they were drawn,
 * and that nearly bought a reimplementation of a finished feature. Energy allocation is the
 * next genuinely absent slot. Keep this list honest or delete it.)
 *
 * Three properties worth preserving as it grows:
 *
 *  - It FITS. The panel is position:fixed, so anything past the bottom of the window is
 *    unreachable — no page scroll can retrieve it. The height is capped relative to where the
 *    panel has been dragged and the body below the title bar scrolls; see panelBounds.
 *
 *  - It is LIVE. The caller looks the ship up in the current game state on every render, so
 *    this re-renders with the battle. A status readout showing values from the moment it
 *    was opened is worse than no readout.
 *  - It works for an ENEMY ship. Weapon arcs are public knowledge — SSDs are printed in the
 *    rulebook — and knowing where an opponent cannot shoot is half of maneuvering.
 */

/**
 * Panel width. ALSO the zoom control: the arc diagram is a viewBox at width 100%, so the hexes, the
 * text and the contacts all scale with this. Changing SIZE instead only rescales the internal units
 * and looks identical.
 *
 * Declared here rather than inline in the style because the opening position is measured from it.
 * Those two were separate numbers once and drifted apart — the panel grew to 450 and the offset
 * stayed at 330, which opened it a sixth off the right edge of the window.
 */
const PANEL_WIDTH = 450;

/** Breathing room between the panel and the edge of the window, when it opens and when capped. */
const EDGE_MARGIN = 16;

/** Hexes out from the ship. Far enough that an FA and an FX are plainly different shapes. */
const RADIUS = 5;

/** Circumradius of a mini-map hex, in SVG units. */
const SIZE = 21;

/**
 * Where a shield readout sits, as a fraction of the way to the neighbouring hex centre.
 * The hex edge is at exactly 0.5, so a little beyond that puts the number outside its own
 * shield face while staying clear of 1.0, where a contact at range 1 is drawn. 0.55 rather
 * than something roomier because the hexes directly above and below are the tight
 * direction: at 0.62 the strength ran into the contact circle in the next hex out.
 */
const EDGE_FRACTION = 0.55;

/** Row spacing for flat-top hexes, matching the battle map's layout. */
const ROW_H = Math.sqrt(3) * SIZE;

interface Props {
  ship: ShipObject;
  isMine: boolean;
  /** Everything on the map, so the diagram can plot what is actually out there. */
  contacts: MapObject[];
  onClose: () => void;
  /**
   * Where it opens. Default is hard against the right edge, which is right on the BATTLE MAP — the
   * panel stays open while you study the map, so it must not sit over the part you are reading.
   *
   * The fleet builder wants something else: its own right-hand column is the fleet being built, so
   * the default lands the panel squarely on top of the list you are adding ships to. It passes
   * 'centre' instead. Either way the panel is draggable from there.
   */
  openAt?: 'right' | 'centre';
}

/** A contact placed against the diagram: inside it, or somewhere off past the rim. */
interface PlottedContact {
  name: string;
  hex: Hex;
  range: number;
  bearing: number;
  colour: string;
  beyond: boolean;
  borne: boolean;
}

/** Offset of a hex from the centre hex, in SVG coordinates. */
function offsetFromCentre(centre: Hex, hex: Hex): [number, number] {
  const x = (hex.col - centre.col) * SIZE * 1.5;
  const parity = (c: number) => (c % 2 === 0 ? ROW_H / 2 : 0);
  const y = (hex.row - centre.row) * ROW_H + parity(hex.col) - parity(centre.col);
  return [x, y];
}

/**
 * Where to park a contact that lies beyond the diagram: out past the rim on its true
 * bearing. Direction 1 is up, which is what facingToAngle already means.
 */
function rimPoint(bearing: number): [number, number] {
  const a = facingToAngle(bearing);
  // An ellipse, not a circle: flat-top hexes step 1.5 x SIZE across a column but
  // SQRT3 x SIZE down a row, so the disc of hexes is half again as tall as it is wide. A
  // circular rim put a contact due north INSIDE the drawn hexes, where it read as being
  // four hexes away rather than nine.
  const rx = (RADIUS + 0.85) * SIZE * 1.5;
  const ry = (RADIUS + 0.7) * ROW_H;
  return [rx * Math.cos(a), ry * Math.sin(a)];
}

/** The six corners of a flat-top hex, as an SVG points string. */
function hexPoints(cx: number, cy: number, size: number): string {
  const pts: string[] = [];
  for (let i = 0; i < 6; i++) {
    const a = (Math.PI / 180) * (60 * i);
    pts.push(`${(cx + size * Math.cos(a)).toFixed(2)},${(cy + size * Math.sin(a)).toFixed(2)}`);
  }
  return pts.join(' ');
}

/**
 * A 360-degree arc covers exactly the disc around the ship, so the diagram's hexes come
 * from the same tested function that computes every other arc rather than from a second
 * loop that could disagree with it about the edge.
 */
const ALL_DIRECTIONS = (1 << 24) - 1;

/**
 * What colour a contact draws in. Ships carry their own faction; a shuttle or a seeker
 * takes its controller's, falling back to the ship that launched it, exactly as the battle
 * map resolves it.
 */
function contactColour(o: MapObject, all: MapObject[]): string {
  const faction = (o as { faction?: string }).faction
    ?? (o as { controllerFaction?: string }).controllerFaction
    ?? (all.find(p => p.type === 'SHIP'
        && p.name === ((o as { parentShipName?: string | null }).parentShipName
          ?? (o as { launcherName?: string | null }).launcherName)) as ShipObject | undefined)?.faction;
  return faction ? factionColor(faction) : '#8b949e';
}

/** Units, not scenery: terrain has no bearing on where a weapon points. */
const CONTACT_TYPES = new Set([
  'SHIP', 'SHUTTLE', 'SUICIDE_SHUTTLE', 'SCATTER_PACK', 'WILD_WEASEL', 'DRONE', 'PLASMA',
]);

/** Weapons worth showing an arc for: scout channels have no firing arc to speak of. */
function arcBearingWeapons(ship: ShipObject): WeaponState[] {
  return (ship.weapons ?? []).filter(w => !w.scoutChannel && w.arcMask > 0);
}

export default function SsdPanel(
    { ship, isMine, contacts, onClose, openAt = 'right' }: Props) {
  const [selected, setSelected] = useState<string | null>(null);
  const [showContacts, setShowContacts] = useState(true);

  // Escape closes it. A way out that does not depend on reaching a particular pixel: the
  // panel is draggable, and a drag that put its close button out of reach used to leave it
  // stuck on screen for good.
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  /**
   * A facing being TRIED, or null when the diagram is showing the truth. The question a
   * player actually has is "what bears if I turn?", and answering it here costs nothing and
   * commits nothing: this never touches the ship.
   *
   * The preview remembers which ship and which real facing it was made against, and is
   * ignored the moment either changes. So the panel cannot sit there showing a hypothesis
   * while the battle has moved on — truth wins, every time, without an effect that
   * resets state and costs a second render for it.
   */
  const [preview, setPreview] =
    useState<{ forShip: string; forFacing: number; facing: number } | null>(null);
  const previewFacing = preview != null
      && preview.forShip === ship.name
      && preview.forFacing === ship.facing
    ? preview.facing : null;
  const facing = previewFacing ?? ship.facing;

  const tryFacing = (steps: number) => setPreview({
    forShip: ship.name, forFacing: ship.facing, facing: turnFacing(facing, steps),
  });

  // Opens out of the way on the right, then goes wherever it is dragged. It is meant to
  // stay open while you look at the map, so it must not be stuck over the part you need.
  //
  // Measured from PANEL_WIDTH rather than a number typed here, which is how it went wrong: the
  // offset said 330 while the panel had grown to 450, so a sixth of it hung off the right edge of
  // the window. On the battle map that reads as a panel sitting snugly at the edge, because the map
  // behind it gives no clue where the cut is; in the fleet builder it is plainly half off the view.
  // One constant for both, so they cannot disagree again.
  const viewportWidth = typeof window === 'undefined' ? 1200 : window.innerWidth;
  const drag = useDraggable({
    left: Math.max(EDGE_MARGIN, openAt === 'centre'
        ? (viewportWidth - PANEL_WIDTH) / 2
        : viewportWidth - PANEL_WIDTH - EDGE_MARGIN),
    top: 70,
  });

  // The ship's REAL hex, not a nominal one: contacts are plotted against this now, so an
  // approximate centre would put every one of them in the wrong place. Column parity has
  // always mattered here — the offset layout shifts alternate columns half a hex.
  const loc = parseLocation(ship.location);
  const centre: Hex | null = loc ? { col: loc[0], row: loc[1] } : null;

  const weapons = arcBearingWeapons(ship);
  const selectedWeapon = weapons.find(w => weaponKey(w) === selected) ?? null;

  // A ship that has disengaged keeps its entry but loses its hex (C7.1), and there is
  // nothing to draw a diagram around.
  if (!centre)
    return (
      <div style={{ ...panelStyle, left: drag.position.left, top: drag.position.top }}>
        <div style={headerStyle} {...drag.handleProps} title="Drag to move">
          <span style={{ fontWeight: 700 }}>{ship.name}</span>
          <button className="secondary" style={{ padding: '0 8px' }} onClick={onClose}>
            {String.fromCharCode(10005)}
          </button>
        </div>
        <div style={{ fontSize: '0.75rem', color: '#8b949e' }}>
          Off the map {String.fromCharCode(8212)} no bearing to draw.
        </div>
      </div>
    );

  const disc = hexesInArc(centre, facing, ALL_DIRECTIONS, RADIUS);
  const covered = new Set<string>();   // any functional weapon bears here
  for (const w of weapons) {
    if (!w.functional) continue;
    for (const hex of disc)
      if (bearsOn(centre, facing, w.arcMask, hex))
        covered.add(`${hex.col}|${hex.row}`);
  }

  // Everything else on the map, placed against the diagram. The radius limits what can be
  // DRAWN in its true hex, not what can be answered: an arc is unbounded, so a contact at
  // range nine is in it or not just as definitely as one at range two. Those get a marker
  // out on the rim at their true bearing instead of being dropped.
  const plotted: PlottedContact[] = showContacts ? contacts
    .filter(o => CONTACT_TYPES.has(o.type) && o.name !== ship.name)
    .flatMap(o => {
      const at = parseLocation(o.location);
      if (!at) return [];
      const hex = { col: at[0], row: at[1] };
      const range = hexRangeBetween(centre, hex);
      if (range === 0) return [];        // sharing the ship's own hex: no bearing exists
      return [{
        name: o.name,
        hex,
        range,
        bearing: hexGetBearingBetween(centre, hex),
        colour: contactColour(o, contacts),
        beyond: range > RADIUS,
        borne: selectedWeapon != null && bearsOn(centre, facing, selectedWeapon.arcMask, hex),
      }];
    })
    .sort((a, b) => a.range - b.range) : [];

  const borneCount = plotted.filter(c => c.borne).length;

  /**
   * Shield facings, keyed by hex. The six hexes at range 1 ARE the six shields: the one
   * dead ahead is #1 and they number clockwise from there, which is the numbering on the
   * SSD. Derived from each hex's bearing rather than assumed, so it follows the previewed
   * facing for free — turn to starboard and the panel shows which shield WOULD be facing
   * him.
   */
  const shieldRing = new Map<string, { num: number; state: ShieldState | undefined }>();
  for (const hex of disc) {
    if (hexRangeBetween(centre, hex) !== 1) continue;
    const num = ringShieldNumber(centre, facing, hex);
    const index = num - 1;
    shieldRing.set(`${hex.col}|${hex.row}`,
      { num, state: ship.shields?.find(sh => sh.shieldNum === num) ?? ship.shields?.[index] });
  }

  const span = (RADIUS + 1.2) * SIZE * 1.5;
  const vbHeight = (RADIUS + 1.2) * ROW_H;

  return (
    <div style={{ ...panelStyle, ...panelBounds(drag.position.top),
                  left: drag.position.left, top: drag.position.top }}>
      {/* ---- identity ---------------------------------------------------- */}
      <div style={headerStyle} {...drag.handleProps} title="Drag to move">
        <div>
          <span style={{ fontWeight: 700, color: factionColor(ship.faction) }}>{ship.name}</span>
          <span style={{ color: '#888', marginLeft: 6, fontSize: '0.78rem' }}>
            {ship.shipType}{ship.typeName ? ` · ${ship.typeName}` : ''}
            {isMine ? '' : ' (enemy)'}
          </span>
        </div>
        <button className="secondary" style={{ padding: '0 8px' }}
                title="Close (Esc)" onClick={onClose}>✕</button>
      </div>

      {/* Everything below the title bar scrolls as ONE region. The title bar stays put so the
          close button and the drag grip are never scrolled away — which is the same reasoning
          useDraggable uses to keep the handle on screen. */}
      <div style={bodyStyle}>
      <div style={{ fontSize: '0.72rem', marginBottom: 6,
                    color: previewFacing == null ? '#888' : '#d29922' }}>
        {previewFacing == null
          ? `Facing ${facingLabel(ship.facing)} ${String.fromCharCode(183)} arcs shown as they point right now`
          : `Trying facing ${facingLabel(facing)} ${String.fromCharCode(183)} actually facing ${facingLabel(ship.facing)}`}
      </div>
      <div style={{ fontSize: '0.68rem', color: '#6e7681', marginBottom: 4 }}>
        Inner ring: shields 1{String.fromCharCode(8211)}6
        {isMine ? '' : ' (base strength \u2014 an enemy\u2019s reinforcement is not public)'}
      </div>

      {/* ---- arc diagram ------------------------------------------------- */}
      <svg
        viewBox={`${-span} ${-vbHeight} ${span * 2} ${vbHeight * 2}`}
        style={{ width: '100%', height: 'auto', display: 'block', background: '#0d1117',
                 border: `1px solid ${previewFacing == null ? '#30363d' : '#d29922'}`,
                 borderRadius: 4 }}
      >
        {disc.map(hex => {
          const [x, y] = offsetFromCentre(centre, hex);
          const key = `${hex.col}|${hex.row}`;
          const inSelected = selectedWeapon != null
            && bearsOn(centre, facing, selectedWeapon.arcMask, hex);
          // Faint for anything the ship can reach, strong for the weapon in hand. Showing
          // both at once is the point: where two arcs overlap is where you want him.
          const fill = inSelected ? '#2f6f4f'
            : covered.has(key) ? '#1b2a33'
            : '#12161c';
          const shield = shieldRing.get(key);
          const down = shield?.state != null && !shield.state.active;
          return (
            <polygon
              key={key}
              points={hexPoints(x, y, SIZE)}
              fill={fill}
              // A dropped shield is drawn as a gap in the ring rather than a number in a
              // different colour, because that is what it is (D3.4).
              stroke={down ? '#484f58' : '#30363d'}
              strokeDasharray={down ? '3 4' : undefined}
              strokeWidth={1}
            />
          );
        })}

        {/* The ship itself, pointing along its facing. Drawn before the shield readouts
            so a rotated icon can never cover one; contacts come last, over everything. */}
        <g transform={`rotate(${(facingToAngle(facing) * 180) / Math.PI})`}>
          <polygon
            points={`${SIZE * 0.6},0 ${-SIZE * 0.38},${SIZE * 0.42} ${-SIZE * 0.38},${-SIZE * 0.42}`}
            fill={factionColor(ship.faction)}
            stroke="#c9d1d9"
            strokeWidth={1}
          />
        </g>

        {/* shields on the ship's own hex edges, not in the ring hexes beyond them */}
        {[...shieldRing.entries()].map(([key, shield]) => {
          const [col, row] = key.split('|').map(Number);
          const [hx, hy] = offsetFromCentre(centre, { col, row });
          // A shield is a FACE of this hex. The edge is halfway to the neighbour, so a
          // little past that sits the readout just outside its own shield and well clear
          // of the neighbouring hex centre, where contacts are drawn.
          const x = hx * EDGE_FRACTION;
          const y = hy * EDGE_FRACTION;
          const state = shield.state;
          // An enemy's reinforcement is not public; the base strength is. Same rule the
          // battle map follows, and worth keeping identical.
          const visible = state ? (isMine ? state.current : state.baseStrength) : 0;
          const colour = state == null ? '#484f58'
            : !state.active ? '#484f58'
            : shieldStrengthColor(visible, state.max);
          // Hoverable, so the tooltip below can be reached: this group had
          // pointerEvents="none". Nothing is blocked by allowing it, since the labels sit
          // at 0.55 of a hex step and contacts at 1.0.
          return (
            <g key={`shield-${key}`}>
              {/* The strength alone. Which shield it is, the position already says — that
                  being the whole reason for putting them on the ring — so a 1-6 label
                  beside every number was just something else to read. It survives in the
                  hover, where it costs nothing. */}
              <text x={x} y={y + 5} textAnchor="middle" fontSize={13}
                    fontWeight={700} fill={colour}>
                {state == null ? '-' : visible}
                <title>
                  {state == null
                    ? `Shield ${shield.num}`
                    : `Shield ${shield.num}: ${visible} of ${state.max}`
                      + (state.active ? '' : ' (down)')}
                </title>
              </text>
            </g>
          );
        })}

        {/* contacts: in their true hex inside the diagram, or on the rim beyond it */}
        {plotted.map(c => {
          const [x, y] = c.beyond
            ? rimPoint(c.bearing)
            : offsetFromCentre(centre, c.hex);
          const r = c.beyond ? SIZE * 0.26 : SIZE * 0.34;
          return (
            <g key={c.name}>
              <circle
                cx={x} cy={y} r={r}
                fill={c.colour}
                stroke={c.borne ? '#f0c040' : '#0d1117'}
                strokeWidth={c.borne ? 3 : 1.5}
                opacity={c.beyond ? 0.75 : 1}
              >
                <title>{`${c.name} ${String.fromCharCode(183)} range ${c.range}`}</title>
              </circle>
              {c.beyond && (
                <text
                  x={x} y={y + r + 11}
                  textAnchor="middle"
                  fontSize={11}
                  fill={c.borne ? '#f0c040' : '#8b949e'}
                >{c.range}</text>
              )}
            </g>
          );
        })}

      </svg>

      {/* ---- turn it and see ---------------------------------------------- */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 6 }}>
        <button
          className="secondary"
          style={{ padding: '0 8px' }}
          title="Turn to port and see what bears"
          onClick={() => tryFacing(-1)}
        >{String.fromCharCode(8630)}</button>
        <span style={{ flex: 1, textAlign: 'center', fontSize: '0.75rem',
                       color: previewFacing == null ? '#8b949e' : '#d29922' }}>
          {previewFacing == null ? 'Turn to preview' : `Facing ${facingLabel(facing)}`}
        </span>
        <button
          className="secondary"
          style={{ padding: '0 8px' }}
          title="Turn to starboard and see what bears"
          onClick={() => tryFacing(1)}
        >{String.fromCharCode(8631)}</button>
      </div>
      {previewFacing != null && (
        <button
          className="secondary"
          style={{ width: '100%', marginTop: 4, fontSize: '0.72rem' }}
          onClick={() => setPreview(null)}
        >Back to actual facing {facingLabel(ship.facing)}</button>
      )}

      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 6 }}>
        <button
          className="secondary"
          style={{ flex: 1, fontSize: '0.72rem' }}
          onClick={() => setShowContacts(v => !v)}
        >{showContacts ? 'Hide contacts' : 'Show contacts'}</button>
        {selectedWeapon && showContacts && (
          <span style={{ fontSize: '0.72rem', color: borneCount > 0 ? '#f0c040' : '#8b949e' }}>
            {borneCount} in arc
          </span>
        )}
      </div>

      {/* ---- weapons ----------------------------------------------------- */}
      <div style={{ fontSize: '0.72rem', color: '#8b949e', margin: '8px 0 4px' }}>
        {weapons.length === 0 ? 'No weapons with a firing arc.'
          : 'Select a weapon to light its arc.'}
      </div>
      {/* No inner scroll cap any more: the whole body scrolls as one region, and a nested
          scrollbar here meant a long weapons list hid the systems block behind a second
          scrollbar the reader had to notice first. */}
      <div>
        {weapons.map(w => {
          const key = weaponKey(w);
          const isSelected = key === selected;
          return (
            <div
              key={key}
              onClick={() => setSelected(isSelected ? null : key)}
              style={{
                display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer',
                padding: '2px 4px', borderRadius: 3, fontSize: '0.78rem',
                background: isSelected ? '#2f6f4f' : 'transparent',
                opacity: w.functional ? 1 : 0.45,
              }}
            >
              <span style={{ flex: 1 }}>
                {weaponTitle(w)}
                {!w.functional && <span style={{ color: '#f85149' }}> (destroyed)</span>}
              </span>
              <span style={{ color: isSelected ? '#d4f5e2' : '#8b949e' }}>{w.arcLabel}</span>
            </div>
          );
        })}
      </div>

      {/* ---- the selected weapon's table --------------------------------
          Hung off the selection the arc diagram already uses, rather than a second gesture on
          the same row: clicking a weapon here has always meant "tell me about this one", and
          its damage table is part of the answer. INLINE rather than hovering, because this
          panel is dense and a table of eleven bands floating over it would cover the diagram
          the click just changed.

          Not a derived number, which this panel refuses on principle (see SystemsBlock): it is
          the same static table the firing preview reads, collapsed into the bands the SSD
          prints. Nothing here is computed from game state. */}
      {selectedWeapon && (
        <WeaponFullTable
          weaponName={selectedWeapon.name}
          armingType={selectedWeapon.armingType ?? null}
          plasmaType={selectedWeapon.launcherType
            ? (selectedWeapon.plasmaType ?? selectedWeapon.launcherType) : null}
          anchored={false}
        />
      )}

      {/* ---- systems ----------------------------------------------------- */}
      <SystemsBlock ship={ship} />
      </div>{/* end scrolling body */}
    </div>
  );
}

/**
 * What the hull is, as against what it points at you.
 *
 * <p>Added for the fleet builder, where the question is "what am I buying?" rather than "where can
 * it shoot?" — but it belongs on the battle panel too, which is what this component was always
 * meant to grow into. Every figure is shown as {@code available/max} where damage can take it away,
 * so the same block reads as a status board mid-battle and as a spec sheet in the shelf, where the
 * two are equal.
 *
 * <h2>No derived numbers here, deliberately</h2>
 * The figure a buyer most wants is "how fast can it go on all its power", and it is NOT computed
 * here even though total power and move cost are both to hand. Dividing one by the other is a rules
 * question — C12.38's cap, life support, fire control, reserve warp and the Orion engine-doubling
 * case all bear on it — and this tier mirrors rules, never computes them. Core has no ship top-speed
 * figure today ({@code maxSpeed} belongs to shuttles, and {@code maxSpeedNextTurn} is the
 * acceleration limit, which is 10 from a standstill for every hull). If the number is wanted, it
 * comes from core. Until then the player gets the honest inputs.
 */
/**
 * The ship's drone racks, by type: "2 x type-A, 1 x type-G drone racks".
 *
 * A bare count told a buyer nothing — two type-As and two type-Gs are completely different ships to
 * fly, since only the G throws anti-drones (FD3.7). Counted off `weapons` rather than `droneRacks`
 * because the rack TYPE rides the weapon entry; `droneRacks` carries what is loaded, not what kind.
 */
function droneRackSummary(ship: ShipObject): string | null {
  const byType = new Map<string, number>();
  for (const w of ship.weapons ?? []) {
    if (!w.rackType) continue;
    const label = 'type-' + w.rackType.replace(/^TYPE_/, '');
    byType.set(label, (byType.get(label) ?? 0) + 1);
  }
  if (byType.size === 0) return null;
  const total = [...byType.values()].reduce((a, b) => a + b, 0);
  const parts = [...byType.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([label, n]) => (n > 1 ? `${n} × ${label}` : label));
  return `${parts.join(', ')} drone rack${total === 1 ? '' : 's'}`;
}

function SystemsBlock({ ship }: { ship: ShipObject }) {
  /** "n" when nothing can be lost, "n/m" once they differ — damage should be visible, not implied. */
  const pair = (now: number | undefined, max: number | undefined): string | null => {
    if (max == null || max === 0) return null;
    return now === max ? String(max) : `${now ?? 0}/${max}`;
  };
  const join = (...parts: (string | null | undefined)[]) =>
    parts.filter(p => p != null && p !== '').join(' · ');

  const warp = join(
    pair(ship.availableLWarp, ship.maxLWarp) && `L ${pair(ship.availableLWarp, ship.maxLWarp)}`,
    pair(ship.availableCWarp, ship.maxCWarp) && `C ${pair(ship.availableCWarp, ship.maxCWarp)}`,
    pair(ship.availableRWarp, ship.maxRWarp) && `R ${pair(ship.availableRWarp, ship.maxRWarp)}`,
  );
  const other = join(
    pair(ship.availableImpulse, ship.maxImpulse) && `imp ${pair(ship.availableImpulse, ship.maxImpulse)}`,
    pair(ship.availableApr, ship.maxApr) && `APR ${pair(ship.availableApr, ship.maxApr)}`,
    pair(ship.availableAwr, ship.maxAwr) && `AWR ${pair(ship.availableAwr, ship.maxAwr)}`,
    ship.availableBattery ? `batt ${ship.availableBattery}` : null,
  );

  const rows: Array<[string, string | null]> = [
    ['Power', join(ship.totalPower ? `${ship.totalPower} total` : null, warp, other)],
    // The two standing charges every turn starts with, so "total power" is not read as spendable.
    ['Upkeep', join(
      ship.lifeSupportCost ? `life support ${ship.lifeSupportCost}` : null,
      ship.fireControlCost ? `fire control ${ship.fireControlCost}` : null,
    )],
    ['Movement', join(
      ship.moveCost ? `${ship.moveCost} per hex` : null,
      ship.turnMode ? `turn mode ${ship.turnMode}` : null,
      ship.turnHexes ? `${ship.turnHexes} hex${ship.turnHexes === 1 ? '' : 'es'}` : null,
      ship.hetCost ? `HET ${ship.hetCost}` : null,
    )],
    ['Hull', join(
      pair(ship.availableFhull, ship.maxFhull) && `fore ${pair(ship.availableFhull, ship.maxFhull)}`,
      pair(ship.availableAhull, ship.maxAhull) && `aft ${pair(ship.availableAhull, ship.maxAhull)}`,
      pair(ship.availableChull, ship.maxChull) && `centre ${pair(ship.availableChull, ship.maxChull)}`,
    )],
    ['Crew', join(
      ship.availableCrewUnits ? `${ship.availableCrewUnits} units` : null,
      ship.minimumCrew ? `min ${ship.minimumCrew}` : null,
      ship.boardingParties ? `${ship.boardingParties} boarding` : null,
      ship.commandos ? `${ship.commandos} commandos` : null,
      ship.availableDeckCrews ? `${ship.availableDeckCrews} deck` : null,
      // G21.0: absent means normal, so saying so would be noise. Only the exceptions earn a word.
      ship.crewQuality && ship.crewQuality !== 'NORMAL'
        ? ship.crewQuality.toLowerCase() + ' quality' : null,
    )],
    ['Command', join(
      ship.commandRating ? `rating ${ship.commandRating}` : null,
      pair(ship.availableBridge, ship.maxBridge) && `bridge ${pair(ship.availableBridge, ship.maxBridge)}`,
      pair(ship.availableFlag, ship.maxFlag) && `flag ${pair(ship.availableFlag, ship.maxFlag)}`,
      pair(ship.availableAuxcon, ship.maxAuxcon) && `aux ${pair(ship.availableAuxcon, ship.maxAuxcon)}`,
      pair(ship.availableEmer, ship.maxEmer) && `emer ${pair(ship.availableEmer, ship.maxEmer)}`,
      ship.controlLimit ? `control ${ship.controlUsed ?? 0}/${ship.controlLimit}` : null,
    )],
    ['Support', join(
      pair(ship.availableTransporters, ship.totalTransporters)
        && `transporters ${pair(ship.availableTransporters, ship.totalTransporters)}`,
      pair(ship.availableTractors, ship.totalTractors)
        && `tractors ${pair(ship.availableTractors, ship.totalTractors)}`,
      ship.availableLab ? `labs ${ship.availableLab}` : null,
      ship.sensorRating ? `sensor ${ship.sensorRating}` : null,
      ship.scannerBonus ? `scanner ${ship.scannerBonus}` : null,
    )],
    // Only what the hull actually has. A list of absent systems teaches nothing and would be
    // longer than the list of present ones on every ship in the game.
    ['Special', join(
      ship.aegisFitted && ship.aegisFitted !== 'NONE' ? `aegis ${ship.aegisFitted.toLowerCase()}` : null,
      ship.scoutEwPool ? `scout channels ${ship.scoutEwPool} EW` : null,
      ship.uimFunctional ? 'UIM' : null,
      ship.canDoubleEngines ? 'engine doubling' : null,
      ship.cloakState && ship.cloakState !== 'NONE' ? 'cloaking device' : null,
      droneRackSummary(ship),
      ship.droneStorageSpaces ? `${ship.droneStorageSpaces} spaces fighter drones` : null,
      ship.cargoDroneSpaces ? `${ship.cargoDroneSpaces} spaces cargo drones` : null,
      ship.tBombs ? `${ship.tBombs} T-bombs` : null,
    )],
  ];

  // A carrier's air wing is much of what it costs, and it is invisible on the shelf. The bays are
  // listed by their contents rather than counted, because "3 bays" says nothing a buyer can use.
  const bayLines = (ship.shuttleBays ?? []).map(bay => {
    const counts = new Map<string, number>();
    for (const s of bay.shuttles ?? []) {
      // shortName is what the craft is CALLED; s.type is the catalogue KEY, and showing that was
      // the bug — a carrier's air wing read as "stinger1, stinger1, admin".
      const label = s.shortName ?? s.type ?? 'shuttle';
      counts.set(label, (counts.get(label) ?? 0) + 1);
    }
    return [...counts.entries()].map(([t, n]) => (n > 1 ? `${n} × ${t}` : t)).join(', ');
  }).filter(line => line !== '');

  return (
    <div style={{ marginTop: 8, borderTop: '1px solid #30363d', paddingTop: 6 }}>
      {rows.filter(([, value]) => value).map(([label, value]) => (
        <div key={label} style={{ display: 'flex', gap: 6, fontSize: '0.72rem', lineHeight: 1.6 }}>
          <span style={{ flex: '0 0 4.4rem', color: '#6e7681' }}>{label}</span>
          <span style={{ flex: 1, color: '#c9d1d9' }}>{value}</span>
        </div>
      ))}
      {bayLines.map((line, i) => (
        <div key={`bay${i}`} style={{ display: 'flex', gap: 6, fontSize: '0.72rem', lineHeight: 1.6 }}>
          <span style={{ flex: '0 0 4.4rem', color: '#6e7681' }}>
            {i === 0 ? `Bay${bayLines.length > 1 ? ' 1' : ''}` : `Bay ${i + 1}`}
          </span>
          <span style={{ flex: 1, color: '#c9d1d9' }}>{line}</span>
        </div>
      ))}
    </div>
  );
}

/** Weapons share names, so the designator is part of the identity. */
function weaponKey(w: WeaponState): string {
  return `${w.name}#${w.designator ?? ''}`;
}

const panelStyle: React.CSSProperties = {
  position: 'fixed',
  width: PANEL_WIDTH,
  zIndex: 40,
  background: '#161b22',
  border: '1px solid #30363d',
  borderRadius: 6,
  padding: 10,
  boxShadow: '0 6px 24px rgba(0,0,0,0.5)',
  // The panel is position:fixed, so content past the bottom of the window was simply
  // unreachable — no page scroll could bring it back. It grew past a screen when the systems
  // block arrived. A column with a scrolling body and a pinned header fixes it; the height cap
  // itself is set per-render by panelBounds, because it depends on where the panel has been
  // dragged to.
  display: 'flex',
  flexDirection: 'column',
};

/**
 * How tall the whole panel may be, given where its top edge currently sits.
 *
 * The cap goes on the PANEL, not on its body: bounding only the body leaves the panel free to
 * exceed it by the height of the title bar and the padding, which is exactly enough to put the
 * last line of the systems block back under the bottom edge. With the panel capped and the body
 * allowed to shrink, the whole thing is guaranteed to fit.
 *
 * Measured from the panel's own top rather than as a flat share of the viewport, so dragging it
 * down shortens it instead of pushing its foot off screen again. The 16px keeps it clear of the
 * bottom edge, and the 200px floor stops a panel dragged almost to the bottom collapsing to a
 * sliver — {@code useDraggable} only guarantees the HANDLE stays on screen, not the body.
 */
function panelBounds(top: number): React.CSSProperties {
  return { maxHeight: `max(200px, calc(100vh - ${Math.max(0, top)}px - ${EDGE_MARGIN}px))` };
}

/**
 * The scrolling region: everything below the title bar.
 *
 * {@code minHeight: 0} is the load-bearing line. A flex child defaults to refusing to shrink below
 * its content, so without it the body ignores the panel's cap and {@code overflowY} never engages —
 * the panel simply grows and the bottom is unreachable again.
 */
const bodyStyle: React.CSSProperties = {
  flex: 1,
  minHeight: 0,
  overflowY: 'auto',
};

const headerStyle: React.CSSProperties = {
  cursor: 'grab',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: 8,
  marginBottom: 2,
};
