import type { ArmingState } from './armingStatus';
import { ARMING_TITLE, ARMING_COLOUR } from './armingStatus';

/**
 * The coloured dot. Always paired with a word or a tooltip somewhere on the row — colour
 * alone would leave the state unreadable to anyone who cannot separate green from amber.
 */
export function ArmingDot({ state }: { state: ArmingState }) {
  return (
    <span
      title={ARMING_TITLE[state]}
      style={{
        width: '0.5rem', height: '0.5rem', borderRadius: '50%', flex: 'none',
        display: 'inline-block', background: ARMING_COLOUR[state],
        boxShadow: '0 0 0 1px rgba(0,0,0,0.5)',
      }} />
  );
}
