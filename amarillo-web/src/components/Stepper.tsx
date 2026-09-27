/**
 * The −/value/+ control used wherever a player buys a whole number of something.
 *
 * Lifted out of EnergyAllocationDialog when the hangar drawer needed it too: two copies of
 * a control is two places for the disabled-at-the-limit behaviour to drift apart.
 */
export function Stepper({
  value, min, max, step = 1, onChange, label,
}: { value: number; min: number; max: number; step?: number; onChange: (n: number) => void; label: string }) {
  return (
    <div className="ea-stepper">
      <button className="ea-step-btn" onClick={() => onChange(Math.max(min, value - step))} disabled={value <= min}>−</button>
      <span className="ea-step-value">{value}</span>
      <button className="ea-step-btn" onClick={() => onChange(Math.min(max, value + step))} disabled={value >= max}>+</button>
      <span className="ea-step-label">{label}</span>
    </div>
  );
}
