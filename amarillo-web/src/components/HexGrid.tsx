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

const DEFAULT_COLS = 42;
const DEFAULT_ROWS = 32;
const SIZE    = 48;   // circumradius: center → corner
const PADDING = 12;
const SQRT3   = Math.sqrt(3);

const MIN_ZOOM = 0.25;
const MAX_ZOOM = 4.0;

// ESG visuals (G23.0) — one base colour drives everything ESG on the map
// (active-field ring + announcement glow). Change ESG_RGB to re-theme them all.
const ESG_RGB         = '120, 220, 255';              // base theme colour (r, g, b)
const ESG_RING_STROKE = `rgba(${ESG_RGB}, 0.9)`;      // active-field ring outline
const ESG_RING_FILL   = `rgba(${ESG_RGB}, 0.16)`;     // active-field ring hex tint
const ESG_GLOW_RGB    = ESG_RGB;                       // announcement aura; alpha scales with countdown
// Announcement aura (G23.31): a soft glow round the ship while a release is
// pending, brightening as the 4-impulse countdown nears formation.
const ESG_ANNOUNCE_DELAY = 4;                          // impulses of advance notice

/** Pixel center of hex (col, row), both 1-indexed. */
function hexCenter(col: number, row: number): [number, number] {
  const h = SQRT3 * SIZE;
  const x = PADDING + SIZE + (col - 1) * SIZE * 1.5;
  const y = PADDING + h / 2 + (row - 1) * h + (col % 2 === 0 ? h / 2 : 0);
  return [x, y];
}

/** SFB hex range between two hexes — replicates MapUtils.getRange (x=col, y=row). */
function hexRange(c1: number, r1: number, c2: number, r2: number): number {
  const xDiff = Math.abs(c2 - c1);
  if (xDiff === 0) return Math.abs(r2 - r1);
  const even    = c1 % 2 === 0;
  const topY    = even ? r1 - Math.floor(xDiff / 2) : r1 - Math.floor((xDiff + 1) / 2);
  const bottomY = even ? r1 + Math.floor((xDiff + 1) / 2) : r1 + Math.floor(xDiff / 2);
  if (r2 >= topY && r2 <= bottomY) return xDiff;
  return r2 < topY ? xDiff + (topY - r2) : xDiff + (r2 - bottomY);
}

/** Return the [col, row] of the hex closest to pixel (px, py), or null if too far. */
function pixelToHex(px: number, py: number, cols: number, rows: number): [number, number] | null {
  let bestCol = -1, bestRow = -1, bestDist = Infinity;
  for (let col = 1; col <= cols; col++) {
    for (let row = 1; row <= rows; row++) {
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

function drawGrid(ctx: CanvasRenderingContext2D, cols: number, rows: number) {
  const pattern = starfieldImage ? ctx.createPattern(starfieldImage, 'repeat') : null;
  ctx.fillStyle = pattern ?? '#0d1a0d';
  ctx.strokeStyle = 'rgba(255,255,255,0.4)';
  ctx.lineWidth = 1;
  for (let col = 1; col <= cols; col++) {
    for (let row = 1; row <= rows; row++) {
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
  for (let col = 1; col <= cols; col++) {
    for (let row = 1; row <= rows; row++) {
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
  cols: number,
  rows: number,
) {
  const mySet = new Set(myShips ?? []);

  // Draw tractor beam lines before units so lines appear under tokens
  for (const obj of objects) {
    if (obj.type !== 'SHIP') continue;
    const ship = obj as import('../types/gameState').ShipObject;
    if (!ship.tractored || !ship.tractoredByName) continue;
    const holder = objects.find(o => o.type === 'SHIP' && o.name === ship.tractoredByName) as import('../types/gameState').ShipObject | undefined;
    if (!holder?.location || !ship.location) continue;
    const heldCoords   = parseLocation(ship.location);
    const holderCoords = parseLocation(holder.location);
    if (!heldCoords || !holderCoords) continue;
    const [heldCol, heldRow]     = heldCoords;
    const [holderCol, holderRow] = holderCoords;
    const [hx1, hy1] = hexCenter(heldCol, heldRow);
    const [hx2, hy2] = hexCenter(holderCol, holderRow);
    ctx.save();
    ctx.beginPath();
    ctx.moveTo(hx1, hy1);
    ctx.lineTo(hx2, hy2);
    ctx.strokeStyle = '#22d3ee'; // cyan
    ctx.lineWidth = 2;
    ctx.setLineDash([6, 4]);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.restore();
  }

  // Tractor lines to grabbed probe canisters being drawn aboard (J1.621/SH35.452)
  for (const obj of objects) {
    if (obj.type !== 'OBJECTIVE') continue;
    const o = obj as import('../types/gameState').ObjectiveObject;
    if (!o.tractoredBy || !o.location) continue;
    const holder = objects.find(h => h.type === 'SHIP' && h.name === o.tractoredBy) as import('../types/gameState').ShipObject | undefined;
    if (!holder?.location) continue;
    const oc = parseLocation(o.location);
    const hc = parseLocation(holder.location);
    if (!oc || !hc) continue;
    const [ox, oy] = hexCenter(oc[0], oc[1]);
    const [hx, hy] = hexCenter(hc[0], hc[1]);
    ctx.save();
    ctx.beginPath();
    ctx.moveTo(ox, oy);
    ctx.lineTo(hx, hy);
    ctx.strokeStyle = '#22d3ee';
    ctx.lineWidth = 2;
    ctx.setLineDash([6, 4]);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.restore();
  }

  // Two-pass rendering: terrain first so units always appear on top.
  const terrain = objects.filter(o => o.type === 'TERRAIN');
  const units   = objects.filter(o => o.type !== 'TERRAIN');
  for (const obj of [...terrain, ...units]) {
    if (!obj.location) continue;
    const coords = parseLocation(obj.location);
    if (!coords) continue;
    const [col, row] = coords;
    if (col < 1 || col > cols || row < 1 || row > rows) continue;
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
      // Active ESG fields (G23.0): a hollow ring of hexes at the field's radius,
      // moving with the ship. Drawn under the token so r=0 fields don't hide it.
      for (const w of (obj as ShipObject).weapons ?? []) {
        // Announced-but-not-formed (G23.31): a faint aura that brightens toward
        // formation. Public to all (a field is coming); only the owner sees the
        // target radius (opponents get esgRadius < 0 from the DTO, G23.311).
        if (w.esgAnnounced) {
          const releaseIn = Math.max(0, Math.min(ESG_ANNOUNCE_DELAY, w.esgReleaseIn ?? ESG_ANNOUNCE_DELAY));
          const t = (ESG_ANNOUNCE_DELAY - releaseIn) / ESG_ANNOUNCE_DELAY; // 0 at announce → 1 at formation
          const peak  = 0.18 + t * 0.34;
          const glowR = SIZE * (1.4 + t * 0.5);
          ctx.save();
          const grad = ctx.createRadialGradient(cx, cy, SIZE * 0.15, cx, cy, glowR);
          grad.addColorStop(0, `rgba(${ESG_GLOW_RGB}, ${peak})`);
          grad.addColorStop(1, `rgba(${ESG_GLOW_RGB}, 0)`);
          ctx.fillStyle = grad;
          ctx.beginPath();
          ctx.arc(cx, cy, glowR, 0, 2 * Math.PI);
          ctx.fill();
          const ghostRad = w.esgRadius ?? -1;
          if (ghostRad >= 0) { // owner-only ghost ring at the chosen radius
            ctx.strokeStyle = `rgba(${ESG_GLOW_RGB}, ${0.35 + t * 0.3})`;
            ctx.setLineDash([4, 4]);
            ctx.lineWidth = 1.5;
            for (let c = Math.max(1, col - ghostRad - 1); c <= Math.min(cols, col + ghostRad + 1); c++) {
              for (let r = Math.max(1, row - ghostRad - 1); r <= Math.min(rows, row + ghostRad + 1); r++) {
                if (hexRange(col, row, c, r) !== ghostRad) continue;
                const [hx, hy] = hexCenter(c, r);
                tracePath(ctx, hx, hy);
                ctx.stroke();
              }
            }
          }
          ctx.restore();
          continue;
        }
        if (!w.esgActive) continue;
        const rad = w.esgRadius ?? 0;
        ctx.save();
        ctx.strokeStyle = ESG_RING_STROKE;
        ctx.fillStyle   = ESG_RING_FILL;
        ctx.lineWidth   = 2;
        for (let c = Math.max(1, col - rad - 1); c <= Math.min(cols, col + rad + 1); c++) {
          for (let r = Math.max(1, row - rad - 1); r <= Math.min(rows, row + rad + 1); r++) {
            if (hexRange(col, row, c, r) !== rad) continue;
            const [hx, hy] = hexCenter(c, r);
            tracePath(ctx, hx, hy);
            ctx.fill();
            ctx.stroke();
          }
        }
        ctx.restore();
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
      } else if (obj.terrainType === 'PLANET' || obj.terrainType === 'GAS_GIANT') {
        // Footprint radius r spans r hexes of √3·SIZE pitch beyond the center
        // hex; r=0 keeps the classic single-hex planet disc
        const fr = obj.radius ?? 0;
        // Disc radius: reach a fraction of a hex past the outermost footprint
        // hex centers. The old +0.5 lapped a half-hex onto the surrounding
        // empty hexes, making the planet look larger than its footprint.
        const pr = fr === 0 ? 30 : SQRT3 * SIZE * (fr + 0.15);
        const isGiant = obj.terrainType === 'GAS_GIANT';
        // Counter-art resolution chain: instance tokenArt → per-type default
        // (planet1.png for PLANET; giants have no default) → procedural disc.
        const terrainImg = obj.tokenArt
          ? loadTokenImage(obj.tokenArt, onImageLoad)
          : (isGiant ? null : planetImage);
        if (terrainImg) {
          ctx.save();
          ctx.beginPath();
          ctx.arc(cx, cy, pr, 0, 2 * Math.PI);
          ctx.clip();
          ctx.drawImage(terrainImg, cx - pr, cy - pr, pr * 2, pr * 2);
          ctx.restore();
        } else {
          ctx.fillStyle = isGiant ? '#a5713f' : '#2d6a8a';
          ctx.beginPath();
          ctx.arc(cx, cy, pr, 0, 2 * Math.PI);
          ctx.fill();
          if (isGiant) {
            // Simple banding so giants read as gas, not rock
            ctx.save();
            ctx.beginPath();
            ctx.arc(cx, cy, pr, 0, 2 * Math.PI);
            ctx.clip();
            ctx.fillStyle = 'rgba(255, 255, 255, 0.10)';
            for (let band = -3; band <= 3; band += 2)
              ctx.fillRect(cx - pr, cy + (band * pr) / 4, pr * 2, pr / 5);
            ctx.restore();
          }
        }
        ctx.strokeStyle = isGiant ? '#c9955c' : '#4a9aba';
        ctx.lineWidth   = 2;
        ctx.beginPath();
        ctx.arc(cx, cy, pr, 0, 2 * Math.PI);
        ctx.stroke();
        // Planetary rings (P2.223): tint each ring HEX so it's unambiguous
        // which hexes are ring terrain. Bands are tinted by index — the inner
        // ring (band 0) and outer ring (band 1) get slightly different tints.
        // (Placeholder for future ring1/ring2 hex tokens.)
        if ((obj.rings?.length ?? 0) > 0) {
          const bands = obj.rings!;
          const RING_TINTS = ['rgba(224, 170, 100, 0.34)', 'rgba(206, 184, 132, 0.22)'];
          const maxOuter = Math.max(...bands.map(b => b[1]));
          for (let c = Math.max(1, col - maxOuter - 1); c <= Math.min(cols, col + maxOuter + 1); c++) {
            for (let r = Math.max(1, row - maxOuter - 1); r <= Math.min(rows, row + maxOuter + 1); r++) {
              const dist = hexRange(col, row, c, r);
              const bandIdx = bands.findIndex(b => dist >= b[0] && dist <= b[1]);
              if (bandIdx < 0) continue;
              const [hx, hy] = hexCenter(c, r);
              tracePath(ctx, hx, hy);
              ctx.fillStyle = RING_TINTS[bandIdx % RING_TINTS.length];
              ctx.fill();
            }
          }
        }
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
    if (obj.type === 'OBJECTIVE') {
      const o = obj as import('../types/gameState').ObjectiveObject;
      if (o.carrierName) continue; // carried — travels with its ship, not drawn on the map
      const grabbed = !!o.tractoredBy; // held in a beam / being drawn aboard
      // A party on a planet face (SH50.46) is nudged toward that side's edge, so
      // several on one planet hex sit on their own faces (A=1 north, clockwise).
      let ox = cx, oy = cy;
      if (o.side && o.side >= 1 && o.side <= 6) {
        const ang = (o.side - 1) * Math.PI / 3;
        ox = cx + Math.sin(ang) * SIZE * 0.55;
        oy = cy - Math.cos(ang) * SIZE * 0.55;
      }
      const s = 8;
      ctx.fillStyle   = '#e0b34a';
      ctx.strokeStyle = grabbed ? '#22d3ee' : '#8a6d1f';
      ctx.lineWidth   = grabbed ? 2 : 1.5;
      ctx.beginPath();
      ctx.rect(ox - s, oy - s, s * 2, s * 2);
      ctx.fill();
      ctx.stroke();
      ctx.fillStyle    = '#ffffff';
      ctx.font         = 'bold 9px sans-serif';
      ctx.textAlign    = 'center';
      ctx.textBaseline = 'top';
      const label = o.beingRecovered ? `${o.name ?? 'Objective'} ⟳` : (o.name ?? 'Objective');
      ctx.fillText(label, ox, oy + s + 2);
      continue;
    }
    if (obj.type === 'SHUTTLE' || obj.type === 'SUICIDE_SHUTTLE' || obj.type === 'SCATTER_PACK') {
      const shuttle = obj as import('../types/gameState').ShuttleObject;
      const angle   = facingToAngle(shuttle.facing);

      // Faction: special shuttles carry controllerFaction; plain shuttles look up parent ship.
      let faction: string = (shuttle as any).controllerFaction ?? '';
      if (!faction) {
        const parent = objects.find(o => o.type === 'SHIP' && o.name === shuttle.parentShipName) as ShipObject | undefined;
        faction = parent?.faction ?? '';
      }

      const tokenPath = faction ? `${faction.toLowerCase()}/shuttle.png` : null;
      const tokenImg  = tokenPath ? loadTokenImage(tokenPath, onImageLoad) : null;
      const r = SIZE * 0.2;

      if (tokenImg) {
        ctx.save();
        ctx.translate(cx, cy);
        ctx.rotate(angle + Math.PI / 2);
        ctx.drawImage(tokenImg, -r, -r, r * 2, r * 2);
        ctx.restore();
      } else {
        ctx.fillStyle = '#79c0ff';
        ctx.beginPath();
        ctx.arc(cx, cy, r, 0, 2 * Math.PI);
        ctx.fill();
      }
    }

    if (obj.type === 'WILD_WEASEL') {
      const ww      = obj as import('../types/gameState').WildWeaselObject;
      const imgR    = SIZE * 0.2;   // shuttle image half-size
      const ringR   = imgR * 1.45;  // status ring just outside the image
      const angle   = facingToAngle(ww.facing);

      // Resolve faction from parent ship for shuttle token art
      const wwParent  = objects.find(o => o.type === 'SHIP' && o.name === ww.parentShipName) as ShipObject | undefined;
      const wwFaction = wwParent?.faction ?? '';
      const wwToken   = wwFaction ? loadTokenImage(`${wwFaction.toLowerCase()}/shuttle.png`, onImageLoad) : null;

      ctx.save();

      if (ww.postExplosion) {
        // Ionized radiation pocket — ring only, no shuttle (J3.212)
        ctx.globalAlpha = 0.6;
        ctx.strokeStyle = '#9ca3af';
        ctx.lineWidth   = 1.5;
        ctx.setLineDash([2, 5]);
        ctx.beginPath();
        ctx.arc(cx, cy, ringR, 0, 2 * Math.PI);
        ctx.stroke();
        ctx.setLineDash([]);
      } else if (ww.exploding) {
        // Expanding debris cloud — ring only, no shuttle (J3.211)
        ctx.strokeStyle = '#f97316';
        ctx.lineWidth   = 3;
        ctx.setLineDash([3, 2]);
        ctx.beginPath();
        ctx.arc(cx, cy, ringR, 0, 2 * Math.PI);
        ctx.stroke();
        ctx.setLineDash([]);
      } else {
        // Active WW — shuttle token + purple ring
        if (wwToken) {
          ctx.translate(cx, cy);
          ctx.rotate(angle + Math.PI / 2);
          ctx.drawImage(wwToken, -imgR, -imgR, imgR * 2, imgR * 2);
          ctx.setTransform(1, 0, 0, 1, 0, 0);
        } else {
          ctx.fillStyle = '#79c0ff';
          ctx.beginPath();
          ctx.arc(cx, cy, imgR, 0, 2 * Math.PI);
          ctx.fill();
        }
        ctx.strokeStyle = '#a78bfa';
        ctx.lineWidth   = 2;
        ctx.setLineDash([4, 3]);
        ctx.beginPath();
        ctx.arc(cx, cy, ringR, 0, 2 * Math.PI);
        ctx.stroke();
        ctx.setLineDash([]);
      }

      ctx.restore();
    }
  }
}

const H = SQRT3 * SIZE;
function canvasWidth(cols: number)  { return Math.ceil(PADDING * 2 + SIZE + (cols - 1) * SIZE * 1.5 + SIZE); }
function canvasHeight(rows: number) { return Math.ceil(PADDING * 2 + H * rows + H / 2); }

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
  const lines = [
    `Faction:  ${ship.faction}`,
    `Name:     ${ship.name}`,
    `Type:     ${ship.shipType}`,
    `Facing:   ${facingLabel(ship.facing)}`,
    `Speed:    ${ship.speed}`,
  ];
  // EW is announced as it is allocated, and lending is explicitly public (G24.211 note,
  // G24.2115) — so it belongs on the hover for enemy ships too, where it is the figure that
  // decides which target is worth shooting at.
  const ecm  = (ship.ecmAllocated  ?? 0) + (ship.lentEcm  ?? 0);
  const eccm = (ship.eccmAllocated ?? 0) + (ship.lentEccm ?? 0);
  const lent = (ship.lentEcm ?? 0) + (ship.lentEccm ?? 0);
  const jam  = ship.offensiveEw ?? 0;
  if (ecm > 0 || eccm > 0 || jam > 0) {
    let ew = `EW:       ${ecm} ECM / ${eccm} ECCM`;
    if (lent > 0) ew += ` (${lent} lent in)`;
    if (jam > 0)  ew += ` · jammed ${jam}`;
    lines.push(ew);
  }
  if (!ship.activeFireControl) lines.push(`FC:       passive`);
  return lines;
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
    const launcherName = s.type === 'DRONE' ? (s as DroneObject).launcherName : null;
    const isMine = myShips != null && (
      (s.controllerName != null && myShips.includes(s.controllerName)) ||
      (launcherName != null && myShips.includes(launcherName))
    );
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
  mapCols?:         number;
  mapRows?:         number;
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

export default function HexGrid({ mapCols: mapColsProp, mapRows: mapRowsProp, mapObjects, myShips, selectedName, fireTargetName, onSelect, onHexClick, pickingHex, snapTo }: Props) {
  const COLS     = mapColsProp ?? DEFAULT_COLS;
  const ROWS     = mapRowsProp ?? DEFAULT_ROWS;
  const CANVAS_W = canvasWidth(COLS);
  const CANVAS_H = canvasHeight(ROWS);
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
    drawGrid(ctx, COLS, ROWS);
    if (mapObjects && mapObjects.length > 0) {
      drawObjects(ctx, mapObjects, myShips ?? null, selectedName ?? null, fireTargetName ?? null,
        () => setTokenRevision(r => r + 1), COLS, ROWS);
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
    const hex     = pixelToHex(px, py, COLS, ROWS);
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
      const shuttleAny = shuttle as any;
      const isMine = myShips != null && (
        (shuttleAny.parentShipName != null && myShips.includes(shuttleAny.parentShipName)) ||
        (shuttleAny.controllerName != null && myShips.includes(shuttleAny.controllerName))
      );
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

    const hex = pixelToHex(px, py, COLS, ROWS);

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

export { hexCenter, tracePath, SIZE, DEFAULT_COLS, DEFAULT_ROWS };
