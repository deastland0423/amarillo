/**
 * What the map canvas can currently see, and what it may therefore skip drawing.
 *
 * Deliberately NOT in `geometry.ts`: that module is a mirror of `com.sfb.utilities.MapUtils` and
 * says so at the top, where every function answers a rules question the server also answers. None
 * of this does. Culling is a drawing concern with no counterpart in core, and filing it next to the
 * bearing algorithm would blur a boundary that module exists to hold.
 *
 * Pure arithmetic with no constants of its own, because the caller owns the layout: HexGrid knows
 * a hex is SIZE wide and H tall and passes those in. A copy of either number here would be one
 * more place for them to drift apart.
 */

/** A slice of the world in world pixels — the units `hexCenter` speaks. */
export type ViewRect = { x: number; y: number; w: number; h: number };

/**
 * True if an axis-aligned box centred at (cx, cy) could put any ink inside the view.
 *
 * Inclusive at the edges on purpose. A hex touching the boundary by a single pixel still has that
 * pixel on screen, and the cost of drawing one row too many is a few microseconds, where the cost
 * of skipping one row too few is a visible gap at the edge of the map that only appears when
 * scrolled to exactly the wrong offset.
 */
export function boxIntersectsView(
  cx: number,
  cy: number,
  halfW: number,
  halfH: number,
  view: ViewRect,
): boolean {
  return cx + halfW >= view.x && cx - halfW <= view.x + view.w
      && cy + halfH >= view.y && cy - halfH <= view.y + view.h;
}
