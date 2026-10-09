import { useCallback, useEffect, useRef, useState } from 'react';
import type { MapObject, ShipObject, ShuttleObject, WildWeaselObject, DroneObject, PlasmaObject } from '../types/gameState';
import { parseLocation, facingToAngle, facingLabel, factionColor, shieldStrengthColor } from '../types/gameState';
import { hexRange } from '../hex/geometry';
import { boxIntersectsView, type ViewRect } from '../hex/viewport';

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
// The token art is 256px square and a ship is drawn at 0.9 of SIZE * 0.42, so a counter is
// 36 world pixels across. At 7x it asks for 254 of the 256 it has — past that the ART is the
// limit rather than the renderer, and magnifying further only buys you bigger soft edges.
const MAX_ZOOM = 7.0;
// Device-pixel ratio is honoured so the map is sharp on a HiDPI screen, but capped: beyond 2x
// the backing store quadruples for a difference nobody can see.
const MAX_DPR  = 2;

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

/** Wash a set of hexes in colour, beneath the counters. */
function drawZones(
  ctx: CanvasRenderingContext2D,
  zones: Array<{ hexes: string[]; color: string }>,
) {
  for (const zone of zones) {
    ctx.fillStyle = zone.color;
    for (const hex of zone.hexes) {
      const col = Number(hex.slice(0, 2));
      const row = Number(hex.slice(2, 4));
      if (!col || !row) continue;
      const [cx, cy] = hexCenter(col, row);
      tracePath(ctx, cx, cy);
      ctx.fill();
    }
  }
}

/** True if a hex centred here could put any ink inside the view. */
function hexVisible(cx: number, cy: number, view: ViewRect): boolean {
  return boxIntersectsView(cx, cy, SIZE, H / 2, view);
}

function drawGrid(
  ctx: CanvasRenderingContext2D,
  cols: number,
  rows: number,
  view: ViewRect,
  /** World units in one CSS pixel — see the hairline note below. */
  hairline: number,
) {
  const pattern = starfieldImage ? ctx.createPattern(starfieldImage, 'repeat') : null;
  ctx.fillStyle = pattern ?? '#0d1a0d';
  ctx.strokeStyle = 'rgba(255,255,255,0.4)';
  // The grid is a backdrop and should stay one CSS pixel wide at every zoom. Everything here
  // goes through the transform, so a plain lineWidth of 1 would be scaled with the map and the
  // outlines would fatten as you zoomed in until they crowded out the counters they sit behind.
  // One CSS pixel rather than one DEVICE pixel on purpose: a true hairline on a HiDPI screen is
  // so faint the grid reads as missing.
  ctx.lineWidth = hairline;
  for (let col = 1; col <= cols; col++) {
    for (let row = 1; row <= rows; row++) {
      const [cx, cy] = hexCenter(col, row);
      if (!hexVisible(cx, cy, view)) continue;
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
      if (!hexVisible(cx, cy, view)) continue;
      ctx.fillText(
        `${String(col).padStart(2, '0')}${String(row).padStart(2, '0')}`,
        cx, cy + labelOffsetY,
      );
    }
  }
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
    const color    = isDown ? '#3a3a3a' : shieldStrengthColor(visible, sh.max);

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

/**
 * The sequence number a seeker was launched with, from its name
 * ("IKV Vengeance-Drone-7" -> "7"), or null if it has none.
 *
 * One game-wide sequence covers drones and plasma alike, so the number identifies a seeker
 * uniquely without anyone having to read a full name.
 */
function seekerNumber(name: string): string | null {
  const m = /-(\d+)$/.exec(name);
  return m ? m[1] : null;
}

/** Small outlined number under a counter, legible over any terrain. */
function drawSeekerNumber(ctx: CanvasRenderingContext2D, cx: number, cy: number,
                          name: string, offsetY: number) {
  const n = seekerNumber(name);
  if (!n) return;
  // save/restore: the wide stroke below would otherwise leak into whatever draws next.
  ctx.save();
  ctx.font         = 'bold 9px monospace';
  ctx.textAlign    = 'center';
  ctx.textBaseline = 'top';
  ctx.lineWidth    = 3;
  ctx.strokeStyle  = 'rgba(0, 0, 0, 0.85)';
  ctx.strokeText(n, cx, cy + offsetY);
  ctx.fillStyle    = '#ffe9a8';
  ctx.fillText(n, cx, cy + offsetY);
  ctx.restore();
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
  if (ship.captured) {
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
  /** Lit from elsewhere - the Fire Orders pad, when a row is hovered. Any unit type. */
  highlightName: string | null,
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

  // Tractor lines to anything else held in a beam — a shuttle, a drone, a plasma
  // torpedo, or a probe canister being drawn aboard (J1.621/SH35.452). Ships are drawn by
  // the pass above from their own field, so they are skipped here rather than doubled.
  for (const obj of objects) {
    if (obj.type === 'SHIP') continue;
    const o = obj as { tractoredBy?: string | null; location?: string | null };
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

  // Painting order, coarsest first. Terrain, then ships, then the small things that sit
  // in the same hexes as ships — shuttles, seekers, mines — and whatever is in focus last
  // within its own group.
  //
  // Small things go over ships deliberately. A drone or shuttle launches into its
  // launcher's hex, and the launcher is usually the SELECTED ship; when the selection was
  // simply painted last, it covered whatever it had just put on the map, so a new drone
  // could not be seen at all. The focus pass still exists — it just cannot hide something
  // smaller than itself.
  //
  // Within the small things, oldest first, so the newest arrival is on top of the stack.
  const SMALL = new Set(['SHUTTLE', 'SUICIDE_SHUTTLE', 'SCATTER_PACK', 'WILD_WEASEL',
                         'DRONE', 'PLASMA', 'MINE']);
  const launchedAt = (o: MapObject) => (o as { launchImpulse?: number }).launchImpulse ?? 0;

  const terrain = objects.filter(o => o.type === 'TERRAIN');
  const ships   = objects.filter(o => o.type !== 'TERRAIN' && !SMALL.has(o.type)
                                   && o.name !== selectedName);
  const small   = objects.filter(o => SMALL.has(o.type) && o.name !== selectedName)
                         .sort((a, b) => launchedAt(a) - launchedAt(b));
  const focused = selectedName
    ? objects.filter(o => o.type !== 'TERRAIN' && o.name === selectedName)
    : [];
  const focusedShip  = focused.filter(o => !SMALL.has(o.type));
  const focusedSmall = focused.filter(o => SMALL.has(o.type));

  for (const obj of [...terrain, ...ships, ...focusedShip, ...small, ...focusedSmall]) {
    if (!obj.location) continue;
    const coords = parseLocation(obj.location);
    if (!coords) continue;
    const [col, row] = coords;
    if (col < 1 || col > cols || row < 1 || row > rows) continue;
    const [cx, cy] = hexCenter(col, row);

    // Before the token, and for every unit type rather than ships only: what the pad points
    // at is as often a drone as a cruiser.
    if (highlightName && obj.name === highlightName) {
      ctx.strokeStyle = '#a78bfa';
      ctx.lineWidth   = 3;
      ctx.beginPath();
      ctx.arc(cx, cy, SIZE * 0.42 + 9, 0, 2 * Math.PI);
      ctx.stroke();
    }

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
      drawSeekerNumber(ctx, cx, cy, obj.name, droneImg ? 13 : 8);
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
      drawSeekerNumber(ctx, cx, cy, obj.name, plasmaImg ? 13 : 8);
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
      let faction: string = shuttle.controllerFaction ?? '';
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

      // The same ring a selected ship gets (drawShip uses this colour and width). Without
      // it there is no telling which of several shuttles in a hex is the one moving.
      if (obj.name === selectedName) {
        ctx.strokeStyle = '#f0c040';
        ctx.lineWidth   = 2.5;
        ctx.beginPath();
        ctx.arc(cx, cy, r + 4, 0, 2 * Math.PI);
        ctx.stroke();
      }
    }

    if (obj.type === 'WILD_WEASEL') {
      const ww      = obj as import('../types/gameState').WildWeaselObject;
      const imgR    = SIZE * 0.2;   // shuttle image half-size
      if (obj.name === selectedName) {
        ctx.strokeStyle = '#f0c040';
        ctx.lineWidth   = 2.5;
        ctx.beginPath();
        ctx.arc(cx, cy, imgR + 4, 0, 2 * Math.PI);
        ctx.stroke();
      }
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
    (o): o is ShuttleObject | WildWeaselObject =>
      (o.type === 'SHUTTLE' || o.type === 'SUICIDE_SHUTTLE' || o.type === 'SCATTER_PACK'
          || o.type === 'WILD_WEASEL')
      && o.location === loc
  );
}

function shipTooltipLines(ship: ShipObject): string[] {
  const lines = [
    `Faction:  ${ship.faction}`,
    `Name:     ${ship.name}`,
    `Type:     ${ship.shipType}${ship.typeName ? ` — ${ship.typeName}` : ''}`,
    `Facing:   ${facingLabel(ship.facing)}`,
    `Speed:    ${ship.speed}`,
  ];
  // C2.0: when it moves next. On an enemy too — half the value is knowing when THEY move.
  const shipMoves = nextMoveText(ship);
  if (shipMoves) lines.push(`Moves:    ${shipMoves}`);
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
  shuttle: ShuttleObject | WildWeaselObject,
  isMine: boolean,
  allObjects: MapObject[],
): string[] {
  /**
   * A wild weasel is its own DTO, not a shuttle one, and carries none of the craft fields
   * below. Narrowing once here rather than casting at each use is also the honest reading of
   * the rules: a weasel is public from the moment it launches (J3.0), so the identification
   * block further down could never apply to one even if the fields existed.
   */
  const craft = shuttle.type === 'WILD_WEASEL' ? null : shuttle;

  // Resolve faction from parent ship
  const parentShip = allObjects.find(
    o => o.type === 'SHIP' && o.name === shuttle.parentShipName
  ) as ShipObject | undefined;
  // Falls back to the controller: a launcher can be destroyed while its shuttle flies on,
  // and then there is no parent ship on the map to look up.
  const faction = parentShip?.faction
    ?? craft?.controllerFaction
    ?? '?';

  // Fog-of-war is the server's job and it does it properly: a SUICIDE_SHUTTLE or a
  // SCATTER_PACK only ever reaches a viewer entitled to see it (its owner, or anyone once a
  // pack has released its drones). It used to be unmasked here on isIdentified as well,
  // which G4.233 forbids — identification never reveals a bomb or a load of drones.
  let typeLabel: string;
  if (shuttle.type === 'SUICIDE_SHUTTLE') typeLabel = 'Suicide Shuttle';
  else if (shuttle.type === 'SCATTER_PACK') typeLabel = 'Scatterpack';
  // A weasel is public by rule — its interference announces it at launch (J3.0), which is
  // why it is not subject to the fog-of-war above.
  else if (shuttle.type === 'WILD_WEASEL')  typeLabel = 'Wild Weasel';
  // Decided by what it IS, not by whether it is armed — every shuttle carries a phaser.
  else if (shuttle.isFighter) typeLabel = 'Fighter';
  // What the craft IS, from the catalogue. Every non-fighter used to read "Admin Shuttle",
  // so a GAS and an HTS were both mislabelled. The ROLE is still never shown.
  else typeLabel = shuttle.shuttleTypeName ?? 'Shuttle';

  const lines = [
    `Faction:  ${faction}`,
    `Type:     ${typeLabel}`,
    `From:     ${shuttle.parentShipName ?? '?'}`,
    `Speed:    ${shuttle.speed}`,
  ];
  // Damage, which nothing showed before — not even to the shuttle's owner.
  const hulls = craft ?? { hull: undefined, maxHull: undefined, crippled: undefined };
  if ((hulls.maxHull ?? 0) > 0)
    lines.push(`Hull:     ${hulls.hull ?? 0} / ${hulls.maxHull}`
      + (hulls.crippled ? '  CRIPPLED' : ''));
  // A suicide shuttle and a scatterpack are seeking weapons: they are chasing something,
  // and their controller may see what, exactly as a drone's or a plasma's tooltip has always
  // shown. The DTO carried it all along — only this tooltip never asked for it, so an owner
  // could watch their own pack fly with no way to tell what it was flying at.
  //
  // No `isMine` test here on purpose. targetName only ever arrives on the full suicide or
  // scatterpack DTO, which the server sends to the entitled alone — its controller, and
  // everyone once a pack has released its drones. An enemy who may not know gets a plain
  // ShuttleDto with no such field, so the presence of the value IS the entitlement, and a
  // second guard here would be the client deciding a question the server already decided.
  // (The identified-enemy case below is a different fact, bought with a lab: G4.233.)
  const seekingAt = craft?.targetName;
  if (seekingAt)
    lines.push(`Target:   ${seekingAt}`);
  // A destroyed weasel is not removed: it explodes for four impulses and keeps pulling
  // seekers in (J3.21), then leaves a spent pocket. Both states change what it is doing,
  // so say which one it is in.
  if (shuttle.type === 'WILD_WEASEL') {
    if (shuttle.exploding)          lines.push('Status:   EXPLODING (J3.21)');
    else if (shuttle.postExplosion) lines.push('Status:   spent');
  }
  // G4.233: what a lab or a scout channel bought. A seeking course narrows an enemy
  // shuttle to a suicide shuttle or a scatter pack without saying which — that is the
  // whole of the answer, so show it and nothing more.
  if (!isMine && craft?.isIdentified) {
    const manned = craft.manned;
    if (manned != null)
      lines.push(`Crew:     ${manned ? 'manned' : 'UNMANNED'}`);
    if (craft.seekingCourse) {
      const t = craft.seekingTargetName;
      lines.push(`Course:   SEEKING${t ? ` ${String.fromCharCode(8594)} ${t}` : ''}`);
    } else {
      lines.push('Course:   not seeking');
    }
  }
  const craftMoves = nextMoveText(shuttle as ShuttleObject);
  if (craftMoves) lines.push(`Moves:    ${craftMoves}`);
  lines.push(...fighterEwLines(shuttle as ShuttleObject));
  return lines;
}

/**
 * A fighter's electronic warfare, for the hover (J4.47, J4.9x).
 * <p>
 * Public for friend and enemy alike, exactly as a ship's is: it is the figure that decides
 * which target is worth shooting at, and a fighter flying with its EW fighter is a materially
 * harder shot than the same fighter that has drifted out of formation.
 *
 * Every number here is read off the DTO. J4.91's ceiling, J4.93's loan and J4.921's range are
 * rules answers and are not recomputed on this side.
 */
function fighterEwLines(shuttle: ShuttleObject): string[] {
  const lines: string[] = [];
  if (shuttle.ecmTotal == null && shuttle.eccmTotal == null) return lines;

  let ew = `EW:       ${shuttle.ecmTotal ?? 0} ECM / ${shuttle.eccmTotal ?? 0} ECCM`;
  if (shuttle.ewPods != null && shuttle.podsActive === false) ew += ' · pods OFF';
  lines.push(ew);
  if (shuttle.ecmSources) lines.push(`          ${shuttle.ecmSources}`);

  // An EW fighter: what it is putting out, and whether that was chosen or defaulted.
  if (shuttle.ewPods != null && shuttle.ewPods > 0) {
    lines.push(`Pods:     ${shuttle.ewPods} — ${shuttle.podEcm ?? 0} ECM / `
      + `${shuttle.podEccm ?? 0} ECCM`
      + (shuttle.podEwDeclared ? '' : ' (undeclared)'));
    if (shuttle.ewLendDelayRemaining) {
      lines.push(`          cannot lend for ${shuttle.ewLendDelayRemaining} more `
        + `impulse${shuttle.ewLendDelayRemaining === 1 ? '' : 's'} (J1.343)`);
    }
  }

  // A fighter being lent to, or one that has drifted out of its EW fighter's reach.
  if (shuttle.ewLenderName && shuttle.ewLenderName !== shuttle.name) {
    const range = shuttle.ewLenderRange;
    const limit = shuttle.ewLendRangeLimit;
    const dist = range == null ? '' : `, ${range} hex${range === 1 ? '' : 'es'}`
      + (limit != null && range > limit ? ` of ${limit}` : '');
    lines.push(`EW from:  ${shuttle.ewLenderName}${dist}`);
    if (shuttle.ewLendRefusal) lines.push(`          ${shuttle.ewLendRefusal}`);
  }
  if (shuttle.squadronName) lines.push(`Squadron: ${shuttle.squadronName}`);
  return lines;
}

/**
 * "moves next impulse" or "moves on 14 (in 3)", off the movement chart (C2.0).
 * <p>
 * Both figures come from the server. The chart is a rules table and the client has never held
 * it — which is exactly why a player could not tell when their own ship moved next, and why
 * P3.25's asteroid clearing was unusable in practice: its fire counts only on the impulse
 * immediately before entry, and nothing said which impulse that was.
 */
function nextMoveText(u: { nextMoveImpulse?: number; impulsesUntilMove?: number }): string | null {
  const at = u.nextMoveImpulse ?? 0;
  const inN = u.impulsesUntilMove ?? 0;
  if (at <= 0 || inN <= 0) return null;
  return inN === 1 ? `next impulse (${at})` : `impulse ${at} (in ${inN})`;
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
      lines.push(`Name:       ${s.name ?? '?'}`);
      lines.push(`Type:       Plasma`);
      lines.push(`Controller: ${s.controllerName ?? '?'}`);
      lines.push(`Launch:     ${absImpulseLabel(s.launchImpulse)}`);
      lines.push(`Speed:      ${s.speed}`);
      // C2.0: when the seeker next moves — the other half of "can I outrun it?", the pursued
      // unit's own cadence being on its own hover.
      const seekerMoves = nextMoveText(s as { nextMoveImpulse?: number; impulsesUntilMove?: number });
      if (seekerMoves) lines.push(`Moves:      ${seekerMoves}`);
      lines.push(`Str:        ${s.currentStrength}`);
      if (canSeeTarget) lines.push(`Target:     ${s.targetName ?? '?'}`);
      // plasmaType and pseudo are never revealed to enemy
    } else {
      // Drone type name is public only when identified (or mine)
      const typeLabel = (isMine || s.isIdentified) ? `Drone ${s.droneType}` : 'Drone';
      // The name, so this can be matched against the identify list — which names each
      // seeker in full. Without it a dozen inbound drones are indistinguishable.
      lines.push(`Name:       ${s.name ?? '?'}`);
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
  /** Unit to ring, driven from outside (the Fire Orders pad's hovered row). */
  highlightName?:   string | null;
  /** Units under the cursor, reported only when the set changes. */
  onHoverUnits?:    (names: string[]) => void;
  onSelect?:        (obj: MapObject | null) => void;
  /** When set, clicks call this with hex col/row instead of unit selection. */
  onHexClick?:      (col: number, row: number) => void;
  /** Switches cursor to crosshair to signal hex-pick mode. */
  pickingHex?:      boolean;
  /** When set, the map pans to center on this object's hex. New object every call ensures re-pan even for same name. */
  snapTo?:          { name: string } | null;
  /**
   * Ground to tint, drawn under everything else — a fleet's deployment zone. Hexes come from
   * the server already expanded, so the tint cannot disagree with what a placement is checked
   * against.
   */
  zones?:           Array<{ hexes: string[]; color: string; label?: string }>;
}

export default function HexGrid({ mapCols: mapColsProp, mapRows: mapRowsProp, mapObjects, myShips, selectedName, fireTargetName, highlightName, onHoverUnits, onSelect, onHexClick, pickingHex, snapTo, zones }: Props) {
  // Last set reported, so a mouse crossing the map does not re-render the pad per pixel.
  const hoverReported = useRef<string>('');
  const COLS     = mapColsProp ?? DEFAULT_COLS;
  const ROWS     = mapRowsProp ?? DEFAULT_ROWS;
  const CANVAS_W = canvasWidth(COLS);
  const CANVAS_H = canvasHeight(ROWS);
  const [zoom, setZoom]           = useState(1.0);
  const [tooltip, setTooltip]     = useState<Tooltip | null>(null);
  const [hexPicker, setHexPicker] = useState<HexPicker | null>(null);
  const [hoveredHex, setHoveredHex] = useState<[number, number] | null>(null);
  const zoomRef                 = useRef(1.0);        // always current, no stale-closure risk
  const containerRef            = useRef<HTMLDivElement>(null);
  const canvasRef               = useRef<HTMLCanvasElement>(null);

  // ---- Viewport rendering -------------------------------------------------------------------
  //
  // The canvas is the size of what you can SEE, not the size of the map, and the world reaches it
  // through a transform.
  //
  // It used to be the other way round: the canvas was the whole map at a fixed 3072x2727, and zoom
  // was a CSS width/height on the element. That meant zooming never redrew anything — the browser
  // stretched the pixels it already had, so a 256px counter rendered into 36 world pixels stayed 36
  // pixels of detail however far you went in. The hex lines and labels blurred by exactly the same
  // factor; the counters were just where it showed, having the most detail to lose. Scaling that
  // backing store with the zoom instead is not an option at this map size: 7x would be a canvas of
  // some 1.6 GB, past what any browser will give you.
  //
  // Drawing only the visible slice costs the same at every zoom — a few megabytes of the viewport,
  // whatever the magnification — and it gets FASTER as you zoom in, because fewer hexes are on
  // screen to draw. The drawing functions were already written in world coordinates, so none of
  // them had to change; they simply no longer know what the zoom is.
  const drawRef = useRef<() => void>(() => {});
  const rafRef  = useRef<number | null>(null);

  /**
   * Repaint on the next frame, collapsing a burst into one.
   *
   * Scroll and wheel fire far faster than the screen refreshes, and a token image finishing its
   * load can land at any time, so every one of them goes through here.
   */
  const scheduleDraw = useCallback(() => {
    if (rafRef.current != null) return;
    rafRef.current = requestAnimationFrame(() => {
      rafRef.current = null;
      drawRef.current();
    });
  }, []);

  // Drag-to-pan state
  const dragging   = useRef(false);
  const dragMoved  = useRef(false);  // true if mouse moved enough to count as a drag
  const dragOrigin = useRef({ x: 0, y: 0, sl: 0, st: 0 });

  // Rebuild the paint routine whenever what it would paint changes, then run it. Holding it in a
  // ref rather than calling it from here is what lets a scroll — or an image that finishes loading
  // minutes later — repaint with the CURRENT props without a React render in between.
  useEffect(() => {
    loadStarfield(scheduleDraw);
    loadPlanetImage(scheduleDraw);
    loadAsteroidImage(scheduleDraw);

    drawRef.current = () => {
      const canvas    = canvasRef.current;
      const container = containerRef.current;
      if (!canvas || !container) return;
      const ctx = canvas.getContext('2d');
      if (!ctx) return;

      const z   = zoomRef.current;
      const dpr = Math.min(window.devicePixelRatio || 1, MAX_DPR);

      // On screen the canvas covers the scrollport, except when the whole map is smaller than it.
      const cssW = Math.max(1, Math.min(container.clientWidth,  Math.ceil(CANVAS_W * z)));
      const cssH = Math.max(1, Math.min(container.clientHeight, Math.ceil(CANVAS_H * z)));
      const bufW = Math.round(cssW * dpr);
      const bufH = Math.round(cssH * dpr);
      // Assigning width or height CLEARS the canvas and resets its state, so only when it moved.
      if (canvas.width !== bufW || canvas.height !== bufH) {
        canvas.width  = bufW;
        canvas.height = bufH;
      }
      if (canvas.style.width  !== `${cssW}px`) canvas.style.width  = `${cssW}px`;
      if (canvas.style.height !== `${cssH}px`) canvas.style.height = `${cssH}px`;

      const sl = container.scrollLeft;
      const st = container.scrollTop;

      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.clearRect(0, 0, bufW, bufH);
      // World pixels to device pixels: magnify by the zoom, again by the display's pixel ratio,
      // then slide the scrolled-away part off the top-left edge.
      ctx.setTransform(z * dpr, 0, 0, z * dpr, -sl * dpr, -st * dpr);
      // The counters are 256px art drawn into 36 world pixels at rest — a seven-fold reduction,
      // which is exactly where a cheap downscale shows.
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';

      const view: ViewRect = { x: sl / z, y: st / z, w: cssW / z, h: cssH / z };
      const hairline = 1 / z;   // world units in one CSS pixel

      drawGrid(ctx, COLS, ROWS, view, hairline);
      if (zones && zones.length > 0) drawZones(ctx, zones);
      if (mapObjects && mapObjects.length > 0) {
        drawObjects(ctx, mapObjects, myShips ?? null, selectedName ?? null, fireTargetName ?? null,
          highlightName ?? null, scheduleDraw, COLS, ROWS);
      }
      if (hoveredHex) {
        const [hcol, hrow] = hoveredHex;
        const [cx, cy] = hexCenter(hcol, hrow);
        tracePath(ctx, cx, cy);
        ctx.fillStyle = 'rgba(255, 255, 100, 0.25)';
        ctx.fill();
        ctx.strokeStyle = 'rgba(255, 255, 100, 0.8)';
        ctx.lineWidth = 2 * hairline;
        ctx.stroke();
      }
    };
    drawRef.current();
  }, [mapObjects, myShips, selectedName, fireTargetName, highlightName, hoveredHex, zones,
      zoom, COLS, ROWS, CANVAS_W, CANVAS_H, scheduleDraw]);

  // Scrolling and resizing change WHAT is on screen without changing anything React knows about,
  // so both have to reach the canvas directly. The container keeps its native scrollbars; the
  // canvas is stuck to the scrollport and repainted under them.
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    container.addEventListener('scroll', scheduleDraw, { passive: true });
    const observer = new ResizeObserver(scheduleDraw);
    observer.observe(container);
    return () => {
      container.removeEventListener('scroll', scheduleDraw);
      observer.disconnect();
      if (rafRef.current != null) cancelAnimationFrame(rafRef.current);
    };
  }, [scheduleDraw]);

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

  /**
   * Screen coordinates to world pixels — the units {@link hexCenter} speaks.
   *
   * The canvas covers the scrollport now, so its origin is the scrollport's: add back what has
   * been scrolled away, then divide out the zoom. This replaced a {@code CANVAS_W / rect.width}
   * ratio that was only ever right while the canvas WAS the whole map, stretched by CSS.
   */
  function toWorld(clientX: number, clientY: number): [number, number] {
    const container = containerRef.current!;
    const rect = container.getBoundingClientRect();
    const z    = zoomRef.current;
    return [
      (clientX - rect.left + container.scrollLeft) / z,
      (clientY - rect.top  + container.scrollTop)  / z,
    ];
  }

  function handleMouseDown(e: React.MouseEvent<HTMLCanvasElement>) {
    if (e.button !== 0) return;
    dragging.current  = true;
    dragMoved.current = false;
    applyCursor();
    dragOrigin.current = {
      x:  e.clientX,
      y:  e.clientY,
      sl: containerRef.current!.scrollLeft,
      st: containerRef.current!.scrollTop,
    };
  }

  /**
   * Tell the parent what is under the cursor, but only when the answer changes: the tooltip
   * hit-test below runs on every mouse move, and pushing that rate into the pad would make a
   * pan across the map re-render it hundreds of times.
   */
  function reportHover(objects: MapObject[] | null, col: number, row: number) {
    if (!onHoverUnits) return;
    const names = objects
      ? objects.filter(o => o.location === `<${col}|${row}>`).map(o => o.name).sort()
      : [];
    const key = names.join('\u0000');
    if (key === hoverReported.current) return;
    hoverReported.current = key;
    onHoverUnits(names);
  }

  function handleMouseMove(e: React.MouseEvent<HTMLCanvasElement>) {
    // Pan logic
    if (dragging.current) {
      const dx = e.clientX - dragOrigin.current.x;
      const dy = e.clientY - dragOrigin.current.y;
      if (Math.abs(dx) > 4 || Math.abs(dy) > 4) { dragMoved.current = true; applyCursor(); }
      if (dragMoved.current) {
        containerRef.current!.scrollLeft = dragOrigin.current.sl - dx;
        containerRef.current!.scrollTop  = dragOrigin.current.st - dy;
      }
    }

    // Tooltip hit-test (skip if dragging or no seeker objects to check)
    if (!mapObjects) { setTooltip(null); reportHover(null, 0, 0); return; }
    const [px, py] = toWorld(e.clientX, e.clientY);
    const hex      = pixelToHex(px, py, COLS, ROWS);
    if (!hex) { setTooltip(null); reportHover(null, 0, 0); if (pickingHex) setHoveredHex(null); return; }
    const [col, row] = hex;
    if (pickingHex) { setHoveredHex([col, row]); setTooltip(null); return; }
    const lines: string[] = [];

    for (const ship of shipsAt(mapObjects, col, row)) {
      if (lines.length > 0) lines.push('──────────────────');
      lines.push(...shipTooltipLines(ship));
    }

    for (const shuttle of shuttlesAt(mapObjects, col, row)) {
      if (lines.length > 0) lines.push('──────────────────');
      const controller = shuttle.type === 'WILD_WEASEL' ? null : shuttle.controllerName;
      const isMine = myShips != null && (
        (shuttle.parentShipName != null && myShips.includes(shuttle.parentShipName)) ||
        (controller != null && myShips.includes(controller))
      );
      lines.push(...shuttleTooltipLines(shuttle, isMine, mapObjects));
    }

    reportHover(mapObjects, col, row);

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
    applyCursor();
  }

  function handleMouseLeave() {
    dragging.current = false;
    applyCursor();
    setTooltip(null);
    setHoveredHex(null);
    reportHover(null, 0, 0);   // or the last unit stays lit in the pad after the cursor goes
  }

  function handleClick(e: React.MouseEvent<HTMLCanvasElement>) {
    // Ignore clicks that were actually drags
    if (dragMoved.current) return;

    // Dismiss any open picker on canvas click
    setHexPicker(null);

    const [px, py] = toWorld(e.clientX, e.clientY);
    const hex      = pixelToHex(px, py, COLS, ROWS);

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

  /**
   * The cursor is set on the ELEMENT rather than through the style prop, because the grab/grabbing
   * distinction is drag state and drag state lives in refs — reading a ref while rendering is both
   * a lint error and a real staleness risk, since nothing re-renders when a ref changes. Writing it
   * imperatively also keeps a drag from re-rendering the component on every mouse move, which is
   * the reason the drag state is in refs to begin with.
   */
  function applyCursor() {
    const canvas = canvasRef.current;
    if (!canvas) return;
    canvas.style.cursor = pickingHex ? 'crosshair'
                        : (dragging.current && dragMoved.current) ? 'grabbing'
                        : 'grab';
  }

  // Covers the prop-driven half: entering or leaving hex-pick mode changes the cursor with no
  // mouse event to hang it off.
  useEffect(applyCursor, [pickingHex]);

  return (
    <div
      ref={containerRef}
      style={{ position: 'relative', width: '100%', height: '100%', overflow: 'auto' }}
    >
      {/* The spacer carries the scroll EXTENT — the whole map at this zoom — so the scrollbars,
          drag-to-pan and snap-to all keep working on container.scrollLeft/scrollTop exactly as
          before. The canvas inside it sticks to the top-left of the scrollport and only ever
          holds the slice you can see; its width, height and CSS size are set during the paint,
          because assigning either dimension clears the canvas. */}
      <div style={{ width: CANVAS_W * zoom, height: CANVAS_H * zoom, position: 'relative' }}>
        <canvas
          ref={canvasRef}
          style={{
            display:  'block',
            position: 'sticky',
            top:      0,
            left:     0,
          }}
          onMouseDown={handleMouseDown}
          onMouseMove={handleMouseMove}
          onMouseUp={handleMouseUp}
          onMouseLeave={handleMouseLeave}
          onClick={handleClick}
        />
      </div>
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
