/**
 * G24.2112: one scout channel lends at most SIX points of EW, ECM and ECCM combined.
 *
 * The sidebar offers ECM and ECCM as two separate steppers over one shared allowance, so
 * neither can have a fixed ceiling — each one's maximum is what it already holds plus whatever
 * is left of the six. Pulled out of GameBoard because it is the only arithmetic in that control
 * and it encodes a rule number: inline in the JSX nothing could pin it, and a wrong ceiling
 * does not crash anything, it just silently lets a player promise EW the server will refuse.
 */
export function channelLendCeiling(ownPoints: number, pairTotal: number): number {
  return ownPoints + Math.max(0, CHANNEL_LEND_MAX - pairTotal);
}

/** The six points of EW one channel may lend, ECM and ECCM together (G24.2112). */
export const CHANNEL_LEND_MAX = 6;
