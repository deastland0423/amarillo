import type { CoiShipData } from '../api/gameApi';
import { droneLoadoutCost } from './coiDroneCost';

/**
 * What a ship's Commander's Option loadout costs against its budget (S3.2).
 *
 * The client half of core's `CoiLoadout.totalCost`, and the figure behind both the "x / y BPV"
 * counter and the guard that stops a player readying an over-budget ship.
 *
 * <h2>Why it is out here instead of inside the dialog</h2>
 * It lived in `CoiDialog.tsx` and was missing a term: boarding parties, commandos, T-bombs and
 * deck crews were counted and the drones were not. The server has priced drones since the
 * scenario year reached `CoiBudget.refusalFor`, so a loadout the dialog called affordable could
 * be refused on submission with nothing on screen to say why. A one-line omission in a function
 * nothing could call was exactly the shape of bug that stays hidden, so the function moved to
 * where a test can reach it — the same move `coiLabels` and `coiRackLoad` already made.
 *
 * <p>The per-item rates are S3.2's and are duplicated from core deliberately: this is a preview,
 * and the server's answer governs. The one thing that must not drift is WHICH items are counted.
 */

/**
 * The parts of a COI selection that cost something.
 *
 * Structural rather than imported, so this module does not depend on the dialog that uses it —
 * `ShipCoi` satisfies it by having these fields.
 */
export interface CoiCostInputs {
  extraBoardingParties: number;
  convertBpToCommando:  number;
  extraCommandoSquads:  number;
  extraTBombs:          number;
  extraDeckCrews:       number;
  droneRackLoadouts:    Record<number, string[]>;
}

export const COST_EXTRA_BP            = 0.5;
export const COST_CONVERT_TO_COMMANDO = 0.5;
export const COST_EXTRA_COMMANDO      = 1.0;
export const COST_TBOMB               = 4.0;
export const COST_EXTRA_DECK_CREW     = 0.5;

export function coiCost(c: CoiCostInputs, ship: CoiShipData): number {
  return c.extraBoardingParties * COST_EXTRA_BP
       + c.convertBpToCommando  * COST_CONVERT_TO_COMMANDO
       + c.extraCommandoSquads  * COST_EXTRA_COMMANDO
       + c.extraTBombs          * COST_TBOMB
       + c.extraDeckCrews       * COST_EXTRA_DECK_CREW
       + droneLoadoutCost(c.droneRackLoadouts, ship);
}
