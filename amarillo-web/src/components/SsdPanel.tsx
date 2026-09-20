import { useState } from 'react';
import type { ShipObject, WeaponState } from '../types/gameState';
import { facingLabel, facingToAngle, factionColor } from '../types/gameState';
import { bearsOn, hexesInArc, turnFacing, type Hex } from '../hex/geometry';
import { useDraggable } from '../hooks/useDraggable';

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
 * It is built as a container with slots (identity, arc diagram, weapons, and later systems
 * and energy) because it is meant to grow into the ship status readout. Shields are the
 * obvious next panel: the ring of six hexes at radius 1 IS the six shield facings.
 *
 * Two properties worth preserving as it grows:
 *
 *  - It is LIVE. The caller looks the ship up in the current game state on every render, so
 *    this re-renders with the battle. A status readout showing values from the moment it
 *    was opened is worse than no readout.
 *  - It works for an ENEMY ship. Weapon arcs are public knowledge — SSDs are printed in the
 *    rulebook — and knowing where an opponent cannot shoot is half of maneuvering.
 */

/** Hexes out from the ship. Far enough that an FA and an FX are plainly different shapes. */
const RADIUS = 5;

/** Circumradius of a mini-map hex, in SVG units. */
const SIZE = 21;

/** Row spacing for flat-top hexes, matching the battle map's layout. */
const ROW_H = Math.sqrt(3) * SIZE;

interface Props {
  ship: ShipObject;
  isMine: boolean;
  onClose: () => void;
}

/** Offset of a hex from the centre hex, in SVG coordinates. */
function offsetFromCentre(centre: Hex, hex: Hex): [number, number] {
  const x = (hex.col - centre.col) * SIZE * 1.5;
  const parity = (c: number) => (c % 2 === 0 ? ROW_H / 2 : 0);
  const y = (hex.row - centre.row) * ROW_H + parity(hex.col) - parity(centre.col);
  return [x, y];
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

/** Weapons worth showing an arc for: scout channels have no firing arc to speak of. */
function arcBearingWeapons(ship: ShipObject): WeaponState[] {
  return (ship.weapons ?? []).filter(w => !w.scoutChannel && w.arcMask > 0);
}

export default function SsdPanel({ ship, isMine, onClose }: Props) {
  const [selected, setSelected] = useState<string | null>(null);

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
  const drag = useDraggable({
    left: Math.max(16, (typeof window === 'undefined' ? 1200 : window.innerWidth) - 330),
    top: 70,
  });

  // The diagram is drawn around a nominal centre; only offsets matter, and column parity
  // has to be preserved or the arcs would sit half a hex out. Using the ship's real column
  // keeps that honest even though the position itself is never shown.
  const shipCol = Number(ship.location?.split('|')[0]?.replace('<', '') ?? 10);
  const centre: Hex = { col: Number.isFinite(shipCol) ? shipCol : 10, row: 10 };

  const weapons = arcBearingWeapons(ship);
  const selectedWeapon = weapons.find(w => weaponKey(w) === selected) ?? null;

  const disc = hexesInArc(centre, facing, ALL_DIRECTIONS, RADIUS);
  const covered = new Set<string>();   // any functional weapon bears here
  for (const w of weapons) {
    if (!w.functional) continue;
    for (const hex of disc)
      if (bearsOn(centre, facing, w.arcMask, hex))
        covered.add(`${hex.col}|${hex.row}`);
  }

  const span = (RADIUS + 1.2) * SIZE * 1.5;
  const vbHeight = (RADIUS + 1.2) * ROW_H;

  return (
    <div style={{ ...panelStyle, left: drag.position.left, top: drag.position.top }}>
      {/* ---- identity ---------------------------------------------------- */}
      <div style={headerStyle} {...drag.handleProps} title="Drag to move">
        <div>
          <span style={{ fontWeight: 700, color: factionColor(ship.faction) }}>{ship.name}</span>
          <span style={{ color: '#888', marginLeft: 6, fontSize: '0.78rem' }}>
            {ship.shipType}{isMine ? '' : ' (enemy)'}
          </span>
        </div>
        <button className="secondary" style={{ padding: '0 8px' }} onClick={onClose}>✕</button>
      </div>
      <div style={{ fontSize: '0.72rem', marginBottom: 6,
                    color: previewFacing == null ? '#888' : '#d29922' }}>
        {previewFacing == null
          ? `Facing ${facingLabel(ship.facing)} ${String.fromCharCode(183)} arcs shown as they point right now`
          : `Trying facing ${facingLabel(facing)} ${String.fromCharCode(183)} actually facing ${facingLabel(ship.facing)}`}
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
          return (
            <polygon
              key={key}
              points={hexPoints(x, y, SIZE)}
              fill={fill}
              stroke="#30363d"
              strokeWidth={1}
            />
          );
        })}

        {/* the ship itself, pointing along its facing */}
        <g transform={`rotate(${(facingToAngle(facing) * 180) / Math.PI})`}>
          <polygon
            points={`${SIZE * 0.75},0 ${-SIZE * 0.45},${SIZE * 0.5} ${-SIZE * 0.45},${-SIZE * 0.5}`}
            fill={factionColor(ship.faction)}
            stroke="#c9d1d9"
            strokeWidth={1}
          />
        </g>
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

      {/* ---- weapons ----------------------------------------------------- */}
      <div style={{ fontSize: '0.72rem', color: '#8b949e', margin: '8px 0 4px' }}>
        {weapons.length === 0 ? 'No weapons with a firing arc.'
          : 'Select a weapon to light its arc.'}
      </div>
      <div style={{ maxHeight: 190, overflowY: 'auto' }}>
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
                {w.name}{w.designator ? ` ${w.designator}` : ''}
                {!w.functional && <span style={{ color: '#f85149' }}> (destroyed)</span>}
              </span>
              <span style={{ color: isSelected ? '#d4f5e2' : '#8b949e' }}>{w.arcLabel}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/** Weapons share names, so the designator is part of the identity. */
function weaponKey(w: WeaponState): string {
  return `${w.name}#${w.designator ?? ''}`;
}

const panelStyle: React.CSSProperties = {
  position: 'fixed',
  width: 300,
  zIndex: 40,
  background: '#161b22',
  border: '1px solid #30363d',
  borderRadius: 6,
  padding: 10,
  boxShadow: '0 6px 24px rgba(0,0,0,0.5)',
};

const headerStyle: React.CSSProperties = {
  cursor: 'grab',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: 8,
  marginBottom: 2,
};
