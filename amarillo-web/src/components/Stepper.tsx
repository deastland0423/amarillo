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
  value, min, max, step = 1, onChange, label, editable = false,
}: {
  value: number; min: number; max: number; step?: number;
  onChange: (n: number) => void; label?: string;
  /**
   * Let the value be typed as well as stepped.
   *
   * For a control with a wide range, where stepping alone would be a chore: a ship's speed runs
   * from 0 to about 31, so "24" is two keystrokes and twenty-four clicks. The buttons are still
   * the point — they are what makes nudging a value easy — but taking away the ability to type a
   * number that was already there would be a trade, not an improvement.
   *
   * Left off by default: an energy line or a count of boarding parties spans a handful of values
   * and a text field would only invite a number the rules do not allow.
   */
  editable?: boolean;
}) {
  const clamp = (n: number) => Math.max(min, Math.min(max, n));
  return (
    <div className="ea-stepper">
      <button className="ea-step-btn" onClick={() => onChange(Math.max(min, value - step))} disabled={value <= min}>−</button>
      {editable ? (
        <input
          className="ea-step-input"
          type="number"
          min={min}
          max={max}
          value={value}
          // Clamped on the way in, so a typed 99 lands on the maximum rather than being
          // submitted and refused by the server.
          onChange={e => onChange(clamp(Math.trunc(Number(e.target.value) || 0)))}
        />
      ) : (
        <span className="ea-step-value">{value}</span>
      )}
      <button className="ea-step-btn" onClick={() => onChange(Math.min(max, value + step))} disabled={value >= max}>+</button>
      {label && <span className="ea-step-label">{label}</span>}
    </div>
  );
}
