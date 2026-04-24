import { useState } from 'react';
import './FacingPicker.css';

// Flat-top hexagon. A=up, B=upper-right, C=lower-right, D=down, E=lower-left, F=upper-left.
// Edge midpoints of a flat-top hex are at angles -90°, -30°, 30°, 90°, 150°, -150° — exactly A–F.

const SVG_W   = 140;
const SVG_H   = 130;
const CX      = 70;
const CY      = 65;
const HEX_R   = 22;                          // circumradius (vertex to center)
const HEX_IR  = HEX_R * Math.sqrt(3) / 2;   // inradius (edge midpoint to center) ≈ 19
const BTN_DIST = 44;                          // center → button centre
const BTN_R    = 11;                          // button circle radius

const FACINGS: { label: string; value: number; angle: number }[] = [
  { label: 'A', value: 1,  angle: -90  },
  { label: 'B', value: 5,  angle: -30  },
  { label: 'C', value: 9,  angle:  30  },
  { label: 'D', value: 13, angle:  90  },
  { label: 'E', value: 17, angle: 150  },
  { label: 'F', value: 21, angle: -150 },
];

function polar(dist: number, angleDeg: number): [number, number] {
  const r = angleDeg * Math.PI / 180;
  return [CX + dist * Math.cos(r), CY + dist * Math.sin(r)];
}

function hexPolygonPoints(): string {
  return [0, 60, 120, 180, 240, 300]
    .map(d => polar(HEX_R, d).join(','))
    .join(' ');
}

interface Props {
  value:         number | null;
  onChange:      (facing: number) => void;
  label?:        string;
  allowedFacings?: Set<number>; // if provided, directions not in this set are dimmed and unclickable
}

export function FacingPicker({ value, onChange, label, allowedFacings }: Props) {
  const [hovered, setHovered] = useState<number | null>(null);

  return (
    <div className="facing-picker">
      {label && <div className="facing-picker-label">{label}</div>}
      <svg width={SVG_W} height={SVG_H}>
        {/* Hex outline */}
        <polygon
          points={hexPolygonPoints()}
          fill="#0d1117"
          stroke="#444c56"
          strokeWidth="1.5"
        />

        {FACINGS.map(f => {
          const [ex, ey] = polar(HEX_IR, f.angle);
          const [lx, ly] = polar(BTN_DIST - BTN_R - 2, f.angle);
          const [bx, by] = polar(BTN_DIST, f.angle);

          const allowed = !allowedFacings || allowedFacings.has(f.value);
          const sel = value === f.value;
          const hov = hovered === f.value && allowed;
          const accent  = '#58a6ff';
          const validHl = '#3fb950'; // green highlight for in-arc directions

          return (
            <g
              key={f.value}
              onClick={() => allowed && onChange(f.value)}
              onMouseEnter={() => setHovered(f.value)}
              onMouseLeave={() => setHovered(null)}
              style={{ cursor: allowed ? 'pointer' : 'not-allowed', opacity: allowed ? 1 : 0.3 }}
            >
              {/* Connector line from hex edge to button */}
              <line
                x1={ex} y1={ey} x2={lx} y2={ly}
                stroke={sel ? accent : allowed ? validHl : '#30363d'}
                strokeWidth={sel || (allowed && !sel) ? 1.5 : 1}
              />
              {/* Button circle */}
              <circle
                cx={bx} cy={by} r={BTN_R}
                fill={sel ? '#1f3a5a' : hov ? '#21262d' : allowed ? '#1a2f1a' : '#161b22'}
                stroke={sel ? accent : hov ? accent : allowed ? validHl : '#444c56'}
                strokeWidth={sel ? 2 : 1.5}
              />
              {/* Label */}
              <text
                x={bx} y={by}
                textAnchor="middle"
                dominantBaseline="middle"
                fill={sel ? accent : hov ? '#e6edf3' : allowed ? validHl : '#8b949e'}
                fontSize="11"
                fontWeight="600"
                style={{ userSelect: 'none', pointerEvents: 'none' }}
              >
                {f.label}
              </text>
            </g>
          );
        })}
      </svg>
    </div>
  );
}
