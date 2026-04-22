import './FacingPicker.css';

// SFB hex facings: A=1, B=5, C=9, D=13, E=17, F=21
// Arranged clockwise starting from top-right (A)
const FACINGS: { label: string; value: number; gridCol: number; gridRow: number }[] = [
  { label: 'A', value: 1,  gridCol: 3, gridRow: 1 },
  { label: 'B', value: 5,  gridCol: 4, gridRow: 2 },
  { label: 'C', value: 9,  gridCol: 4, gridRow: 3 },
  { label: 'D', value: 13, gridCol: 3, gridRow: 4 },
  { label: 'E', value: 17, gridCol: 2, gridRow: 3 },
  { label: 'F', value: 21, gridCol: 2, gridRow: 2 },
];

interface Props {
  value:    number | null;
  onChange: (facing: number) => void;
  label?:   string;
}

export function FacingPicker({ value, onChange, label }: Props) {
  return (
    <div className="facing-picker">
      {label && <div className="facing-picker-label">{label}</div>}
      <div className="facing-picker-grid">
        {FACINGS.map(f => (
          <button
            key={f.value}
            className={`facing-btn${value === f.value ? ' selected' : ''}`}
            style={{ gridColumn: f.gridCol, gridRow: f.gridRow }}
            onClick={() => onChange(f.value)}
            title={`Facing ${f.label}`}
          >
            {f.label}
          </button>
        ))}
      </div>
    </div>
  );
}
