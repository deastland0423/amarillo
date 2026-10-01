/**
 * The −/value/+ control used wherever a player buys a whole number of something.
 *
 * Lifted out of EnergyAllocationDialog when the hangar drawer needed it too: two copies of
 * a control is two places for the disabled-at-the-limit behaviour to drift apart.
 *
 * `label` is optional because the Commander's Options rows carry their own, on the LEFT: those
 * labels run to "T-bombs (4.0 ea, +1 free dummy each, max 6)", far too long to sit after the
 * buttons the way an energy line's one-word label does. Omitting it renders no trailing span at
 * all rather than an empty one.
 */
export function Stepper({
  value, min, max, step = 1, onChange, label,
}: { value: number; min: number; max: number; step?: number; onChange: (n: number) => void; label?: string }) {
  return (
    <div className="ea-stepper">
      <button className="ea-step-btn" onClick={() => onChange(Math.max(min, value - step))} disabled={value <= min}>−</button>
      <span className="ea-step-value">{value}</span>
      <button className="ea-step-btn" onClick={() => onChange(Math.min(max, value + step))} disabled={value >= max}>+</button>
      {label && <span className="ea-step-label">{label}</span>}
    </div>
  );
}
