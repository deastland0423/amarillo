import { useEffect, useRef, useState } from 'react';
import type { MapObject, ShipObject } from '../types/gameState';
import { parseLocation, facingToAngle, factionColor } from '../types/gameState';

const COLS    = 42;
const ROWS    = 32;
const SIZE    = 28;   // circumradius: center → corner
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
  ctx.fillStyle = '#0d1a0d';
  ctx.strokeStyle = '#1e3a1e';
  ctx.lineWidth = 1;
  for (let col = 1; col <= COLS; col++) {
    for (let row = 1; row <= ROWS; row++) {
      const [cx, cy] = hexCenter(col, row);
      tracePath(ctx, cx, cy);
      ctx.fill();
      ctx.stroke();
    }
  }
  ctx.fillStyle = '#2d5a2d';
  ctx.font = '9px monospace';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  for (let col = 1; col <= COLS; col++) {
    for (let row = 1; row <= ROWS; row++) {
      const [cx, cy] = hexCenter(col, row);
      ctx.fillText(
        `${String(col).padStart(2, '0')}${String(row).padStart(2, '0')}`,
        cx, cy,
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
) {
  if (!ship.shields || ship.shields.length === 0) return;
  const shieldR  = SIZE * 0.42 + 9;   // just outside selection ring
  const arcSpan  = Math.PI / 3;        // 60° per shield
  const gap      = 0.06;               // radians gap between adjacent arcs

  for (let i = 0; i < 6; i++) {
    const sh = ship.shields[i];
    if (!sh) continue;
    const center = bowAngle + i * arcSpan;
    const color  = shieldArcColor(sh.current, sh.max);

    ctx.strokeStyle = color;
    ctx.lineWidth   = 3.5;
    ctx.beginPath();
    ctx.arc(cx, cy, shieldR, center - arcSpan / 2 + gap, center + arcSpan / 2 - gap);
    ctx.stroke();

    // Strength number, placed just outside the arc
    if (sh.max > 0) {
      const labelR = shieldR + 7;
      ctx.fillStyle    = color;
      ctx.font         = '7px monospace';
      ctx.textAlign    = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(String(sh.current), cx + Math.cos(center) * labelR, cy + Math.sin(center) * labelR);
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

  drawShields(ctx, cx, cy, ship, angle);

  ctx.fillStyle    = '#e6edf3';
  ctx.font         = '8px monospace';
  ctx.textAlign    = 'center';
  ctx.textBaseline = 'top';
  ctx.fillText(ship.name, cx, cy + r + 2);

  ctx.globalAlpha = prevAlpha;
}

function drawObjects(
  ctx: CanvasRenderingContext2D,
  objects: MapObject[],
  myShips: string[] | null,
  selectedName: string | null,
  fireTargetName: string | null,
) {
  const mySet = new Set(myShips ?? []);
  for (const obj of objects) {
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
      drawShip(ctx, cx, cy, obj, mySet.has(obj.name), obj.name === selectedName);
      continue;
    }
    if (obj.type === 'DRONE') {
      ctx.fillStyle = '#d5a03a'; ctx.strokeStyle = '#ffcc66'; ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(cx, cy - 7); ctx.lineTo(cx + 6, cy);
      ctx.lineTo(cx, cy + 7); ctx.lineTo(cx - 6, cy);
      ctx.closePath(); ctx.fill(); ctx.stroke();
      continue;
    }
    if (obj.type === 'PLASMA') {
      ctx.fillStyle = '#3ab87a'; ctx.strokeStyle = '#66ffaa'; ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(cx, cy - 8); ctx.lineTo(cx + 7, cy + 6); ctx.lineTo(cx - 7, cy + 6);
      ctx.closePath(); ctx.fill(); ctx.stroke();
      continue;
    }
    if (obj.type === 'MINE') {
      ctx.strokeStyle = '#888888'; ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(cx - 6, cy - 6); ctx.lineTo(cx + 6, cy + 6);
      ctx.moveTo(cx + 6, cy - 6); ctx.lineTo(cx - 6, cy + 6);
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

interface Props {
  mapObjects?:      MapObject[];
  myShips?:         string[] | null;
  selectedName?:    string | null;
  fireTargetName?:  string | null;
  onSelect?:        (obj: MapObject | null) => void;
}

export default function HexGrid({ mapObjects, myShips, selectedName, fireTargetName, onSelect }: Props) {
  const [zoom, setZoom]     = useState(1.0);
  const zoomRef             = useRef(1.0);        // always current, no stale-closure risk
  const containerRef        = useRef<HTMLDivElement>(null);
  const canvasRef           = useRef<HTMLCanvasElement>(null);

  // Drag-to-pan state
  const dragging   = useRef(false);
  const dragMoved  = useRef(false);  // true if mouse moved enough to count as a drag
  const dragOrigin = useRef({ x: 0, y: 0, sl: 0, st: 0 });

  // Redraw canvas whenever objects / selection change
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;
    ctx.clearRect(0, 0, CANVAS_W, CANVAS_H);
    drawGrid(ctx);
    if (mapObjects && mapObjects.length > 0) {
      drawObjects(ctx, mapObjects, myShips ?? null, selectedName ?? null, fireTargetName ?? null);
    }
  }, [mapObjects, myShips, selectedName, fireTargetName]);

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
    if (!dragging.current) return;
    const dx = e.clientX - dragOrigin.current.x;
    const dy = e.clientY - dragOrigin.current.y;
    if (Math.abs(dx) > 4 || Math.abs(dy) > 4) dragMoved.current = true;
    if (dragMoved.current) {
      containerRef.current!.scrollLeft = dragOrigin.current.sl - dx;
      containerRef.current!.scrollTop  = dragOrigin.current.st - dy;
    }
  }

  function handleMouseUp() {
    dragging.current = false;
  }

  function handleClick(e: React.MouseEvent<HTMLCanvasElement>) {
    // Ignore clicks that were actually drags
    if (dragMoved.current) return;
    if (!onSelect || !mapObjects) return;
    const canvas = canvasRef.current!;
    const rect   = canvas.getBoundingClientRect();
    // rect.width == CANVAS_W * zoom, so this converts back to canvas pixels
    const scaleX = CANVAS_W / rect.width;
    const scaleY = CANVAS_H / rect.height;
    const px     = (e.clientX - rect.left) * scaleX;
    const py     = (e.clientY - rect.top)  * scaleY;

    const hex = pixelToHex(px, py);
    if (!hex) { onSelect(null); return; }
    const [col, row] = hex;
    const hit = mapObjects.find(o => o.location === `<${col}|${row}>`) ?? null;
    onSelect(hit);
  }

  const cursor = dragging.current && dragMoved.current ? 'grabbing' : 'grab';

  return (
    <div
      ref={containerRef}
      style={{ width: '100%', height: '100%', overflow: 'auto' }}
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
        onMouseLeave={handleMouseUp}
        onClick={handleClick}
      />
    </div>
  );
}

export { hexCenter, tracePath, SIZE, COLS, ROWS };
