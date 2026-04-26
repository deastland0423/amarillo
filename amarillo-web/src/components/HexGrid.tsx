import { useEffect, useRef, useState } from 'react';
import type { MapObject, ShipObject, DroneObject, PlasmaObject } from '../types/gameState';
import { parseLocation, facingToAngle, facingLabel, factionColor } from '../types/gameState';

// Cache of loaded token images keyed by tokenArt path.
// Entries are HTMLImageElement once loaded, or null while loading/failed.
const tokenImageCache = new Map<string, HTMLImageElement | null>();

function loadTokenImage(path: string, onLoad: () => void): HTMLImageElement | null {
  if (tokenImageCache.has(path)) return tokenImageCache.get(path)!;
  tokenImageCache.set(path, null); // mark as loading
  const img = new Image();
  img.onload  = () => { tokenImageCache.set(path, img); onLoad(); };
  img.onerror = () => { tokenImageCache.set(path, null); }; // leave null on failure — fall back to circle
  img.src = `/tokens/${path}`;
  return null;
}

// Starfield background image — loaded once, tiled across hex fills.
let starfieldImage: HTMLImageElement | null = null;
let starfieldLoading = false;
function loadStarfield(onLoad: () => void) {
  if (starfieldImage || starfieldLoading) return;
  starfieldLoading = true;
  const img = new Image();
  img.onload  = () => { starfieldImage = img; onLoad(); };
  img.onerror = () => { starfieldLoading = false; }; // fall back to solid fill
  img.src = '/background/starfield1.png';
}

// Planet terrain image — loaded once, drawn clipped to a circle.
let planetImage: HTMLImageElement | null = null;
let planetLoading = false;
function loadPlanetImage(onLoad: () => void) {
  if (planetImage || planetLoading) return;
  planetLoading = true;
  const img = new Image();
  img.onload  = () => { planetImage = img; onLoad(); };
  img.onerror = () => { planetLoading = false; };
  img.src = '/tokens/terrain/planet1.png';
}

// Asteroid terrain image — loaded once, drawn at hex center.
let asteroidImage: HTMLImageElement | null = null;
let asteroidLoading = false;
function loadAsteroidImage(onLoad: () => void) {
  if (asteroidImage || asteroidLoading) return;
  asteroidLoading = true;
  const img = new Image();
  img.onload  = () => { asteroidImage = img; onLoad(); };
  img.onerror = () => { asteroidLoading = false; };
  img.src = '/tokens/terrain/asteroid.png';
}

const COLS    = 42;
const ROWS    = 32;
const SIZE    = 48;   // circumradius: center → corner
const PADDING = 12;
const SQRT3   = Math.sqrt(3);

const MIN_ZOOM = 0.25;
const MAX_ZOOM = 4.0;

/** Pixel center of hex (col, row), both 1-indexed. */
function hexCenter(col: number, row: number): [number, number] {
  const h = SQRT3 * SIZE;
  const x = PADDING + SIZE + (col - 1) * SIZE * 1.5;
  const y = PADDING + h / 2 + (row - 1) * h + (col % 2 === 0 ? h / 2 : 0);
  return [x, y];
}

/** Return the [col, row] of the hex closest to pixel (px, py), or null if too far. */
function pixelToHex(px: number, py: number): [number, number] | null {
  let bestCol = -1, bestRow = -1, bestDist = Infinity;
  for (let col = 1; col <= COLS; col++) {
    for (let row = 1; row <= ROWS; row++) {
      const [cx, cy] = hexCenter(col, row);
      const d = Math.hypot(px - cx, py - cy);
      if (d < bestDist) { bestDist = d; bestCol = col; bestRow = row; }
    }
  }
  return bestDist <= SIZE ? [bestCol, bestRow] : null;
}

/** Trace a flat-top hex path centered at (cx, cy). */
function tracePath(ctx: CanvasRenderingContext2D, cx: number, cy: number) {
  ctx.beginPath();
  for (let i = 0; i < 6; i++) {
    const angle = (Math.PI / 180) * (60 * i);
    const x = cx + SIZE * Math.cos(angle);
    const y = cy + SIZE * Math.sin(angle);
    if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
  }
  ctx.closePath();
}

function drawGrid(ctx: CanvasRenderingContext2D) {
  const pattern = starfieldImage ? ctx.createPattern(starfieldImage, 'repeat') : null;
  ctx.fillStyle = pattern ?? '#0d1a0d';
  ctx.strokeStyle = 'rgba(255,255,255,0.4)';
  ctx.lineWidth = 1;
  for (let col = 1; col <= COLS; col++) {
    for (let row = 1; row <= ROWS; row++) {
      const [cx, cy] = hexCenter(col, row);
      tracePath(ctx, cx, cy);
      ctx.fill();
      ctx.stroke();
    }
  }
  ctx.fillStyle = 'rgba(255,255,255,0.5)';
  ctx.font = '9px monospace';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'alphabetic';
  const labelOffsetY = SIZE * SQRT3 / 2 - 3;  // near bottom edge of hex
  for (let col = 1; col <= COLS; col++) {
    for (let row = 1; row <= ROWS; row++) {
      const [cx, cy] = hexCenter(col, row);
      ctx.fillText(
        `${String(col).padStart(2, '0')}${String(row).padStart(2, '0')}`,
        cx, cy + labelOffsetY,
      );
    }
  }
}

function shieldArcColor(current: number, max: number): string {
  if (max === 0 || current === 0) return '#333333';
  const pct = current / max;
  if (pct > 0.6)  return '#56d364';  // green
  if (pct > 0.25) return '#f0c040';  // yellow
  return '#f85149';                   // red
}

function drawShields(
  ctx: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  ship: ShipObject,
  bowAngle: number,
  isMine: boolean,
) {
  if (!ship.shields || ship.shields.length === 0) return;
  const shieldR  = SIZE * 0.42 + 9;   // just outside selection ring
  const arcSpan  = Math.PI / 3;        // 60° per shield
  const gap      = 0.06;               // radians gap between adjacent arcs

  for (let i = 0; i < 6; i++) {
    const sh = ship.shields[i];
    if (!sh) continue;
    const visible  = isMine ? sh.current : sh.baseStrength;
    const center   = bowAngle + i * arcSpan;
    const isDown   = !sh.active;
    const color    = isDown ? '#3a3a3a' : shieldArcColor(visible, sh.max);

    ctx.strokeStyle = color;
    ctx.lineWidth   = isDown ? 2 : 3.5;
    ctx.setLineDash(isDown ? [3, 4] : []);
    ctx.beginPath();
    ctx.arc(cx, cy, shieldR, center - arcSpan / 2 + gap, center + arcSpan / 2 - gap);
    ctx.stroke();
    ctx.setLineDash([]);

    // Strength number, placed just outside the arc
    if (sh.max > 0) {
      const labelR = shieldR + 7;
      ctx.fillStyle    = isDown ? '#484f58' : color;
      ctx.font         = '7px monospace';
      ctx.textAlign    = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(String(visible), cx + Math.cos(center) * labelR, cy + Math.sin(center) * labelR);
    }
  }
}

function cloakAlpha(cloakState?: string, fadeStep?: number): number {
  switch (cloakState) {
    case 'FULLY_CLOAKED': return 0.18;
    case 'FADING_OUT':    return 1.0 - ((fadeStep ?? 0) / 5) * 0.82;
    case 'FADING_IN':     return 0.18 + ((fadeStep ?? 0) / 5) * 0.82;
    default:              return 1.0;
  }
}

function drawShip(
  ctx: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  ship: ShipObject,
  isMine: boolean,
  isSelected: boolean,
  onImageLoad: () => void,
) {
  const r     = SIZE * 0.42;
  const color = factionColor(ship.faction);
  const angle = facingToAngle(ship.facing);

  const prevAlpha  = ctx.globalAlpha;
  ctx.globalAlpha *= cloakAlpha(ship.cloakState, ship.cloakFadeStep);

  if (isSelected) {
    ctx.strokeStyle = '#f0c040';
    ctx.lineWidth   = 2.5;
    ctx.beginPath();
    ctx.arc(cx, cy, r + 6, 0, 2 * Math.PI);
    ctx.stroke();
  }
  if (isMine) {
    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth   = 1.5;
    ctx.beginPath();
    ctx.arc(cx, cy, r + 3, 0, 2 * Math.PI);
    ctx.stroke();
  }

  const tokenImg = ship.tokenArt ? loadTokenImage(ship.tokenArt, onImageLoad) : null;
  if (tokenImg) {
    ctx.save();
    ctx.translate(cx, cy);
    ctx.rotate(angle + Math.PI / 2); // token art should point "up" (north) — rotate to facing
    const tr = r * 0.9;
    ctx.drawImage(tokenImg, -tr, -tr, tr * 2, tr * 2);
    ctx.restore();
  } else {
    // Default: faction-colored circle with facing triangle
    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.arc(cx, cy, r, 0, 2 * Math.PI);
    ctx.fill();

    const bowX = cx + Math.cos(angle) * r;
    const bowY = cy + Math.sin(angle) * r;
    ctx.fillStyle = 'rgba(0,0,0,0.45)';
    ctx.beginPath();
    ctx.moveTo(bowX, bowY);
    ctx.lineTo(cx + Math.cos(angle + 2.5) * r * 0.6, cy + Math.sin(angle + 2.5) * r * 0.6);
    ctx.lineTo(cx + Math.cos(angle - 2.5) * r * 0.6, cy + Math.sin(angle - 2.5) * r * 0.6);
    ctx.closePath();
    ctx.fill();

    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth   = 1.5;
    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.lineTo(bowX, bowY);
    ctx.stroke();
  }

  drawShields(ctx, cx, cy, ship, angle, isMine);

  ctx.font         = '8px monospace';
  ctx.textAlign    = 'center';
  ctx.textBaseline = 'top';
  if ((ship as any).captured) {
    ctx.fillStyle = '#ff6b6b';
    ctx.fillText('CAPTURED', cx, cy + r + 2);
    ctx.fillStyle = '#e6edf3';
    ctx.fillText(ship.name, cx, cy + r + 12);
  } else {
    ctx.fillStyle = '#e6edf3';
    ctx.fillText(ship.name, cx, cy + r + 2);
  }

  ctx.globalAlpha = prevAlpha;
}

function drawObjects(
  ctx: CanvasRenderingContext2D,
  objects: MapObject[],
  myShips: string[] | null,
  selectedName: string | null,
  fireTargetName: string | null,
  onImageLoad: () => void,
) {
  const mySet = new Set(myShips ?? []);
  // Two-pass rendering: terrain first so units always appear on top.
  const terrain = objects.filter(o => o.type === 'TERRAIN');
  const units   = objects.filter(o => o.type !== 'TERRAIN');
  for (const obj of [...terrain, ...units]) {
    if (!obj.location) continue;
    const coords = parseLocation(obj.location);
    if (!coords) continue;
    const [col, row] = coords;
    if (col < 1 || col > COLS || row < 1 || row > ROWS) continue;
    const [cx, cy] = hexCenter(col, row);

    if (obj.type === 'SHIP') {
      const isFireTarget = obj.name === fireTargetName;
      if (isFireTarget) {
        // Red targeting ring
        ctx.strokeStyle = '#f85149';
        ctx.lineWidth   = 2.5;
        ctx.beginPath();
        ctx.arc(cx, cy, SIZE * 0.42 + 6, 0, 2 * Math.PI);
        ctx.stroke();
      }
      drawShip(ctx, cx, cy, obj, mySet.has(obj.name), obj.name === selectedName, onImageLoad);
      continue;
    }
    if (obj.type === 'DRONE') {
      const faction = (obj as DroneObject).controllerFaction?.toLowerCase();
      const dronePath = faction ? `${faction}/drone.png` : null;
      const droneImg = dronePath ? loadTokenImage(dronePath, onImageLoad) : null;
      if (droneImg) {
        const droneAngle = facingToAngle((obj as DroneObject).facing);
        ctx.save();
        ctx.translate(cx, cy);
        ctx.rotate(droneAngle + Math.PI / 2);
        ctx.drawImage(droneImg, -15, -15, 30, 30);
        ctx.restore();
      } else {
        ctx.fillStyle = '#d5a03a'; ctx.strokeStyle = '#ffcc66'; ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(cx, cy - 7); ctx.lineTo(cx + 6, cy);
        ctx.lineTo(cx, cy + 7); ctx.lineTo(cx - 6, cy);
        ctx.closePath(); ctx.fill(); ctx.stroke();
      }
      continue;
    }
    if (obj.type === 'PLASMA') {
      const plasmaFaction = (obj as PlasmaObject).controllerFaction?.toLowerCase();
      const plasmaPath = plasmaFaction ? `${plasmaFaction}/plasma.png` : null;
      const plasmaImg = plasmaPath ? loadTokenImage(plasmaPath, onImageLoad) : null;
      if (plasmaImg) {
        const plasmaAngle = facingToAngle((obj as PlasmaObject).facing);
        ctx.save();
        ctx.translate(cx, cy);
        ctx.rotate(plasmaAngle + Math.PI / 2);
        ctx.drawImage(plasmaImg, -15, -15, 30, 30);
        ctx.restore();
      } else {
        ctx.fillStyle = '#3ab87a'; ctx.strokeStyle = '#66ffaa'; ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(cx, cy - 8); ctx.lineTo(cx + 7, cy + 6); ctx.lineTo(cx - 7, cy + 6);
        ctx.closePath(); ctx.fill(); ctx.stroke();
      }
      // Strength label always shown
      const strength = (obj as PlasmaObject).currentStrength ?? 0;
      ctx.fillStyle    = '#ffffff';
      ctx.font         = '8px monospace';
      ctx.textAlign    = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(String(strength), cx, cy + 1);
      continue;
    }
    if (obj.type === 'TERRAIN') {
      if (obj.terrainType === 'ASTEROID') {
        if (asteroidImage) {
          const ar = 22;
          ctx.drawImage(asteroidImage, cx - ar, cy - ar, ar * 2, ar * 2);
        } else {
          const rocks = [
            { dx: -13, dy:  -8, r: 3.5 },
            { dx:   4, dy: -13, r: 2.5 },
            { dx:  14, dy:  -3, r: 3 },
            { dx:  -4, dy:  13, r: 4 },
            { dx:   1, dy:   1, r: 2 },
            { dx: -14, dy:   6, r: 2.5 },
            { dx:  10, dy:   9, r: 2 },
          ];
          ctx.fillStyle   = '#7a6248';
          ctx.strokeStyle = '#a08060';
          ctx.lineWidth   = 0.5;
          for (const rock of rocks) {
            ctx.beginPath();
            ctx.arc(cx + rock.dx, cy + rock.dy, rock.r, 0, 2 * Math.PI);
            ctx.fill();
            ctx.stroke();
          }
        }
      } else if (obj.terrainType === 'PLANET') {
        const pr = 30;
        if (planetImage) {
          ctx.save();
          ctx.beginPath();
          ctx.arc(cx, cy, pr, 0, 2 * Math.PI);
          ctx.clip();
          ctx.drawImage(planetImage, cx - pr, cy - pr, pr * 2, pr * 2);
          ctx.restore();
        } else {
          ctx.fillStyle = '#2d6a8a';
          ctx.beginPath();
          ctx.arc(cx, cy, pr, 0, 2 * Math.PI);
          ctx.fill();
        }
        ctx.strokeStyle = '#4a9aba';
        ctx.lineWidth   = 2;
        ctx.beginPath();
        ctx.arc(cx, cy, pr, 0, 2 * Math.PI);
        ctx.stroke();
        ctx.fillStyle    = '#ffffff';
        ctx.font         = 'bold 11px sans-serif';
        ctx.textAlign    = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(obj.name ?? 'Planet', cx, cy);
      }
      continue;
    }
    if (obj.type === 'MINE') {
      const r = 7;
      ctx.strokeStyle = obj.active ? '#f85149' : '#f0c040';
      ctx.lineWidth   = 1.5;
      ctx.beginPath();
      // Horizontal
      ctx.moveTo(cx - r, cy);     ctx.lineTo(cx + r, cy);
      // Vertical
      ctx.moveTo(cx,     cy - r); ctx.lineTo(cx,     cy + r);
      // Diagonal \
      ctx.moveTo(cx - r * 0.707, cy - r * 0.707); ctx.lineTo(cx + r * 0.707, cy + r * 0.707);
      // Diagonal /
      ctx.moveTo(cx + r * 0.707, cy - r * 0.707); ctx.lineTo(cx - r * 0.707, cy + r * 0.707);
      ctx.stroke();
      continue;
    }
    if (obj.type === 'SHUTTLE' || obj.type === 'SUICIDE_SHUTTLE' || obj.type === 'SCATTER_PACK') {
      ctx.fillStyle = '#79c0ff';
      ctx.beginPath();
      ctx.arc(cx, cy, 5, 0, 2 * Math.PI);
      ctx.fill();
    }
  }
}

// Canvas intrinsic dimensions (never change — zoom is CSS only)
const H        = SQRT3 * SIZE;
const CANVAS_W = Math.ceil(PADDING * 2 + SIZE + (COLS - 1) * SIZE * 1.5 + SIZE);
const CANVAS_H = Math.ceil(PADDING * 2 + H * ROWS + H / 2);

/** Convert an absolute impulse number to a human-readable "T1:I5" string. */
function absImpulseLabel(abs: number): string {
  if (!abs || abs <= 0) return '?';
  const turn    = Math.ceil(abs / 32);
  const impulse = ((abs - 1) % 32) + 1;
  return `T${turn}:I${impulse}`;
}

/** Collect all DRONE and PLASMA objects at hex (col, row). */
function seekersAt(objects: MapObject[], col: number, row: number): (DroneObject | PlasmaObject)[] {
  const loc = `<${col}|${row}>`;
  return objects.filter(
    (o): o is DroneObject | PlasmaObject =>
      (o.type === 'DRONE' || o.type === 'PLASMA') && o.location === loc
  );
}

/** Collect all SHIP objects at hex (col, row). */
function shipsAt(objects: MapObject[], col: number, row: number): ShipObject[] {
  const loc = `<${col}|${row}>`;
  return objects.filter((o): o is ShipObject => o.type === 'SHIP' && o.location === loc);
}

/** Collect all shuttle-type objects at hex (col, row). */
function shuttlesAt(objects: MapObject[], col: number, row: number) {
  const loc = `<${col}|${row}>`;
  return objects.filter(
    o => (o.type === 'SHUTTLE' || o.type === 'SUICIDE_SHUTTLE' || o.type === 'SCATTER_PACK')
      && o.location === loc
  );
}

function shipTooltipLines(ship: ShipObject): string[] {
  return [
    `Faction:  ${ship.faction}`,
    `Name:     ${ship.name}`,
    `Hull:     ${ship.hull}`,
    `Facing:   ${facingLabel(ship.facing)}`,
    `Speed:    ${ship.speed}`,
  ];
}

function shuttleTooltipLines(
  shuttle: MapObject,
  isMine: boolean,
  allObjects: MapObject[],
): string[] {
  // Resolve faction from parent ship
  const parentShip = allObjects.find(
    o => o.type === 'SHIP' && o.name === (shuttle as any).parentShipName
  ) as ShipObject | undefined;
  const faction = parentShip?.faction ?? '?';

  // Fog-of-war: SUICIDE_SHUTTLE and SCATTER_PACK appear as "Shuttle" until owned or identified
  const revealed = isMine || !!(shuttle as any).isIdentified;
  let typeLabel: string;
  if (shuttle.type === 'SUICIDE_SHUTTLE') typeLabel = revealed ? 'Suicide Shuttle' : 'Shuttle';
  else if (shuttle.type === 'SCATTER_PACK') typeLabel = revealed ? 'Scatter Pack'   : 'Shuttle';
  else if ((shuttle as any).weapons?.length > 0) typeLabel = 'Fighter';
  else typeLabel = 'Admin Shuttle';

  return [
    `Faction:  ${faction}`,
    `Type:     ${typeLabel}`,
    `From:     ${(shuttle as any).parentShipName ?? '?'}`,
    `Speed:    ${(shuttle as any).speed}`,
  ];
}

/** Build tooltip lines for a list of seekers.
 *  myShips: the set of ship names owned by the viewing player (null = spectator). */
function seekerTooltipLines(seekers: (DroneObject | PlasmaObject)[], myShips: string[] | null | undefined): string[] {
  const lines: string[] = [];
  for (let i = 0; i < seekers.length; i++) {
    if (i > 0) lines.push('──────────────────');
    const s = seekers[i];
    const isMine = myShips != null && s.controllerName != null && myShips.includes(s.controllerName);
    const canSeeTarget = isMine || s.isIdentified;

    if (s.type === 'PLASMA') {
      // Type label and strength are always public
      lines.push(`Type:       Plasma`);
      lines.push(`Controller: ${s.controllerName ?? '?'}`);
      lines.push(`Launch:     ${absImpulseLabel(s.launchImpulse)}`);
      lines.push(`Speed:      ${s.speed}`);
      lines.push(`Str:        ${s.currentStrength}`);
      if (canSeeTarget) lines.push(`Target:     ${s.targetName ?? '?'}`);
      // plasmaType and pseudo are never revealed to enemy
    } else {
      // Drone type name is public only when identified (or mine)
      const typeLabel = (isMine || s.isIdentified) ? `Drone ${s.droneType}` : 'Drone';
      lines.push(`Type:       ${typeLabel}`);
      lines.push(`Controller: ${s.controllerName ?? '?'}`);
      lines.push(`Launch:     ${absImpulseLabel(s.launchImpulse)}`);
      lines.push(`Speed:      ${s.speed}`);
      // Damage taken is always public; max hull only revealed when identified
      const hullMax = (isMine || s.isIdentified) ? `${s.maxHull}` : '?';
      lines.push(`Damage:     ${s.damageTaken} / ${hullMax}`);
      if (canSeeTarget)    lines.push(`Target:     ${s.targetName ?? '?'}`);
      if (isMine || s.isIdentified) {
        lines.push(`Warhead:    ${s.warheadDamage}`);
        lines.push(`Endurance:  ${s.endurance}`);
      }
    }
  }
  return lines;
}

interface Tooltip {
  x:     number;   // container-relative pixels
  y:     number;
  lines: string[];
}

interface HexPicker {
  x:     number;   // container-relative pixels
  y:     number;
  units: MapObject[];
}

/** Short label shown in the hex picker for any map object. */
function pickerLabel(o: MapObject): string {
  switch (o.type) {
    case 'SHIP':         return `Ship: ${o.name}`;
    case 'DRONE':        return `Drone (${(o as DroneObject).controllerName ?? '?'})`;
    case 'PLASMA':       return `Plasma (${(o as PlasmaObject).controllerName ?? '?'})`;
    case 'SHUTTLE':      return `Shuttle: ${o.name}`;
    case 'SUICIDE_SHUTTLE': return `Shuttle: ${o.name}`;
    case 'SCATTER_PACK': return `Shuttle: ${o.name}`;
    default:             return o.name ?? o.type;
  }
}

interface Props {
  mapObjects?:      MapObject[];
  myShips?:         string[] | null;
  selectedName?:    string | null;
  fireTargetName?:  string | null;
  onSelect?:        (obj: MapObject | null) => void;
  /** When set, clicks call this with hex col/row instead of unit selection. */
  onHexClick?:      (col: number, row: number) => void;
  /** Switches cursor to crosshair to signal hex-pick mode. */
  pickingHex?:      boolean;
  /** When set, the map pans to center on this object's hex. New object every call ensures re-pan even for same name. */
  snapTo?:          { name: string } | null;
}

export default function HexGrid({ mapObjects, myShips, selectedName, fireTargetName, onSelect, onHexClick, pickingHex, snapTo }: Props) {
  const [zoom, setZoom]           = useState(1.0);
  const [tooltip, setTooltip]     = useState<Tooltip | null>(null);
  const [hexPicker, setHexPicker] = useState<HexPicker | null>(null);
  const [tokenRevision, setTokenRevision] = useState(0);
  const [hoveredHex, setHoveredHex] = useState<[number, number] | null>(null);
  const zoomRef                 = useRef(1.0);        // always current, no stale-closure risk
  const containerRef            = useRef<HTMLDivElement>(null);
  const canvasRef               = useRef<HTMLCanvasElement>(null);

  // Drag-to-pan state
  const dragging   = useRef(false);
  const dragMoved  = useRef(false);  // true if mouse moved enough to count as a drag
  const dragOrigin = useRef({ x: 0, y: 0, sl: 0, st: 0 });

  // Redraw canvas whenever objects / selection change, or when a token image finishes loading
  useEffect(() => {
    loadStarfield(() => setTokenRevision(r => r + 1));
    loadPlanetImage(() => setTokenRevision(r => r + 1));
    loadAsteroidImage(() => setTokenRevision(r => r + 1));
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);
    drawGrid(ctx);
    if (mapObjects && mapObjects.length > 0) {
      drawObjects(ctx, mapObjects, myShips ?? null, selectedName ?? null, fireTargetName ?? null,
        () => setTokenRevision(r => r + 1));
    }
    if (hoveredHex) {
      const [hcol, hrow] = hoveredHex;
      const [cx, cy] = hexCenter(hcol, hrow);
      tracePath(ctx, cx, cy);
      ctx.fillStyle = 'rgba(255, 255, 100, 0.25)';
      ctx.fill();
      ctx.strokeStyle = 'rgba(255, 255, 100, 0.8)';
      ctx.lineWidth = 2;
      ctx.stroke();
    }
  }, [mapObjects, myShips, selectedName, fireTargetName, tokenRevision, hoveredHex]);

  // Snap-to: pan map to center on the named object whenever snapTo changes (new object = always re-fires)
  useEffect(() => {
    if (!snapTo || !mapObjects) return;
    const obj = mapObjects.find(o => o.name === snapTo.name);
    if (!obj?.location) return;
    const loc = parseLocation(obj.location);
    if (!loc) return;
    const [hx, hy] = hexCenter(loc[0], loc[1]);
    const container = containerRef.current;
    if (!container) return;
    const z = zoomRef.current;
    container.scrollLeft = hx * z - container.clientWidth  / 2;
    container.scrollTop  = hy * z - container.clientHeight / 2;
  }, [snapTo, mapObjects]);

  // Non-passive wheel listener so we can preventDefault
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    function onWheel(e: WheelEvent) {
      if (!e.ctrlKey) return; // plain wheel scrolls normally; Ctrl+wheel zooms
      e.preventDefault();

      const rect    = container!.getBoundingClientRect();
      const factor  = e.deltaY < 0 ? 1.15 : 1 / 1.15;
      const oldZoom = zoomRef.current;
      const newZoom = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, oldZoom * factor));
      if (newZoom === oldZoom) return;

      // Canvas-pixel position under the cursor (before zoom change)
      const cursorX = e.clientX - rect.left + container!.scrollLeft;
      const cursorY = e.clientY - rect.top  + container!.scrollTop;

      zoomRef.current = newZoom;
      setZoom(newZoom);

      // After React re-renders with the new CSS size, re-anchor scroll so the
      // pixel under the cursor stays under the cursor.
      requestAnimationFrame(() => {
        if (!containerRef.current) return;
        containerRef.current.scrollLeft =
          (cursorX / oldZoom) * newZoom - (e.clientX - rect.left);
        containerRef.current.scrollTop  =
          (cursorY / oldZoom) * newZoom - (e.clientY - rect.top);
      });
    }

    container.addEventListener('wheel', onWheel, { passive: false });
    return () => container.removeEventListener('wheel', onWheel);
  }, []); // attach once; handler reads zoom via ref

  function handleMouseDown(e: React.MouseEvent<HTMLCanvasElement>) {
    if (e.button !== 0) return;
    dragging.current  = true;
    dragMoved.current = false;
    dragOrigin.current = {
      x:  e.clientX,
      y:  e.clientY,
      sl: containerRef.current!.scrollLeft,
      st: containerRef.current!.scrollTop,
    };
  }

  function handleMouseMove(e: React.MouseEvent<HTMLCanvasElement>) {
    // Pan logic
    if (dragging.current) {
      const dx = e.clientX - dragOrigin.current.x;
      const dy = e.clientY - dragOrigin.current.y;
      if (Math.abs(dx) > 4 || Math.abs(dy) > 4) dragMoved.current = true;
      if (dragMoved.current) {
        containerRef.current!.scrollLeft = dragOrigin.current.sl - dx;
        containerRef.current!.scrollTop  = dragOrigin.current.st - dy;
      }
    }

    // Tooltip hit-test (skip if dragging or no seeker objects to check)
    if (!mapObjects) { setTooltip(null); return; }
    const canvas  = canvasRef.current!;
    const rect    = canvas.getBoundingClientRect();
    const scaleX  = CANVAS_W / rect.width;
    const scaleY  = CANVAS_H / rect.height;
    const px      = (e.clientX - rect.left) * scaleX;
    const py      = (e.clientY - rect.top)  * scaleY;
    const hex     = pixelToHex(px, py);
    if (!hex) { setTooltip(null); if (pickingHex) setHoveredHex(null); return; }
    const [col, row] = hex;
    if (pickingHex) { setHoveredHex([col, row]); setTooltip(null); return; }
    const lines: string[] = [];

    for (const ship of shipsAt(mapObjects, col, row)) {
      if (lines.length > 0) lines.push('──────────────────');
      lines.push(...shipTooltipLines(ship));
    }

    for (const shuttle of shuttlesAt(mapObjects, col, row)) {
      if (lines.length > 0) lines.push('──────────────────');
      const isMine = myShips != null && (shuttle as any).parentShipName != null
        && myShips.includes((shuttle as any).parentShipName);
      lines.push(...shuttleTooltipLines(shuttle, isMine, mapObjects));
    }

    const seekers = seekersAt(mapObjects, col, row);
    if (seekers.length > 0) {
      if (lines.length > 0) lines.push('──────────────────');
      lines.push(...seekerTooltipLines(seekers, myShips));
    }

    if (lines.length === 0) { setTooltip(null); return; }

    const containerRect = containerRef.current!.getBoundingClientRect();
    setTooltip({
      x:     e.clientX - containerRect.left + 14,
      y:     e.clientY - containerRect.top  + 14,
      lines,
    });
  }

  function handleMouseUp() {
    dragging.current = false;
  }

  function handleMouseLeave() {
    dragging.current = false;
    setTooltip(null);
    setHoveredHex(null);
  }

  function handleClick(e: React.MouseEvent<HTMLCanvasElement>) {
    // Ignore clicks that were actually drags
    if (dragMoved.current) return;

    // Dismiss any open picker on canvas click
    setHexPicker(null);

    const canvas = canvasRef.current!;
    const rect   = canvas.getBoundingClientRect();
    const scaleX = CANVAS_W / rect.width;
    const scaleY = CANVAS_H / rect.height;
    const px     = (e.clientX - rect.left) * scaleX;
    const py     = (e.clientY - rect.top)  * scaleY;

    const hex = pixelToHex(px, py);

    // Hex-pick mode: deliver coordinates, skip unit selection
    if (pickingHex && onHexClick) {
      if (hex) onHexClick(hex[0], hex[1]);
      return;
    }

    if (!onSelect || !mapObjects) return;
    if (!hex) { onSelect(null); return; }
    const [col, row] = hex;
    const hits = mapObjects.filter(o => o.location === `<${col}|${row}>`);

    if (hits.length === 0) { onSelect(null); return; }
    if (hits.length === 1) { onSelect(hits[0]); return; }

    // Multiple units — show picker at click position (viewport coords for position:fixed)
    setHexPicker({
      x:     e.clientX + 8,
      y:     e.clientY + 8,
      units: hits,
    });
  }

  const cursor = pickingHex ? 'crosshair'
               : (dragging.current && dragMoved.current) ? 'grabbing'
               : 'grab';

  return (
    <div
      ref={containerRef}
      style={{ position: 'relative', width: '100%', height: '100%', overflow: 'auto' }}
    >
      <canvas
        ref={canvasRef}
        width={CANVAS_W}
        height={CANVAS_H}
        style={{
          display: 'block',
          width:   CANVAS_W * zoom,
          height:  CANVAS_H * zoom,
          cursor,
        }}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseLeave}
        onClick={handleClick}
      />
      {tooltip && (
        <div style={{
          position:        'fixed',
          left:            tooltip.x,
          top:             tooltip.y,
          background:      'rgba(13,26,13,0.92)',
          border:          '1px solid #2d5a2d',
          borderRadius:    4,
          padding:         '4px 8px',
          pointerEvents:   'none',
          zIndex:          999,
          fontFamily:      'monospace',
          fontSize:        11,
          color:           '#e6edf3',
          whiteSpace:      'nowrap',
          lineHeight:      '1.6',
        }}>
          {tooltip.lines.map((line, i) => <div key={i}>{line}</div>)}
        </div>
      )}

      {hexPicker && (
        <div style={{
          position:      'fixed',
          left:          hexPicker.x,
          top:           hexPicker.y,
          background:    'rgba(13,26,13,0.97)',
          border:        '1px solid #2d5a2d',
          borderRadius:  4,
          padding:       '4px 0',
          zIndex:        1000,
          fontFamily:    'monospace',
          fontSize:      12,
          color:         '#e6edf3',
          minWidth:      160,
          boxShadow:     '0 2px 8px rgba(0,0,0,0.5)',
        }}>
          <div style={{ padding: '2px 10px 4px', fontSize: 10, color: '#8b949e' }}>
            Select unit
          </div>
          {hexPicker.units.map((unit, i) => (
            <div
              key={i}
              style={{ padding: '4px 10px', cursor: 'pointer' }}
              onMouseEnter={e => (e.currentTarget.style.background = '#1f3d1f')}
              onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
              onClick={() => {
                setHexPicker(null);
                onSelect?.(unit);
              }}
            >
              {pickerLabel(unit)}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export { hexCenter, tracePath, SIZE, COLS, ROWS };
