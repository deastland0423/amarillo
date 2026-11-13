import type { ShipObject, WeaponState } from '../types/gameState';
import { hullGauges, powerGauges, worstGauge } from './systemGauge';
import { weaponTitleShort } from './weaponTitle';

/**
 * What each collapsed section of the SSD panel still says about itself.
 *
 * A section that hides its own headline makes collapsing a choice between seeing something and
 * knowing about it at all, so nobody collapses anything and the panel stays as tall as its
 * tallest ship — nineteen weapons on a B10. These keep the headline on the header, which is
 * what makes the control worth having.
 */

/**
 * "17/19 working · showing Phaser-3 10 (L + LR)".
 *
 * The selected weapon is named because selecting one LIGHTS ITS ARC on the diagram, and the
 * diagram does not collapse with the list. Without this, collapsing the weapons leaves an arc
 * burning on the map with nothing anywhere saying which weapon owns it. With it, collapsing
 * the list is not a compromise at all: it is the focused arc view the panel was built for.
 */
export function weaponsSummary(weapons: WeaponState[], selected: WeaponState | null): string {
  if (weapons.length === 0) return 'none with a firing arc';
  const working = weapons.filter(w => w.functional).length;
  const parts = [`${working}/${weapons.length} working`];
  // The SHORT title: .panel-section-summary is nowrap on a 450px panel, and this line already
  // carries the working count. It has to stand alone, since the list it names is shut.
  if (selected) parts.push(`showing ${weaponTitleShort(selected)} (${selected.arcLabel})`);
  return parts.join(' · ');
}

/**
 * "worst: aft 2/8", or "hull and power intact".
 *
 * Deliberately claims only what it measures. The systems section also carries crew, command,
 * support and the special-equipment line, none of which are gauges, so a cheerful "undamaged"
 * here would be a promise this function is in no position to make.
 */
export function systemsSummary(ship: ShipObject): string {
  const worst = worstGauge([...powerGauges(ship), ...hullGauges(ship)]);
  return worst === null
    ? 'hull and power intact'
    : `worst: ${worst.caption} ${worst.now}/${worst.max}`;
}
