import { describe, expect, it } from 'vitest';
import { boxIntersectsView, type ViewRect } from './viewport';

/**
 * The culling guard.
 *
 * HexGrid draws only the slice of the map on screen, which is what lets it repaint at full device
 * resolution at any zoom instead of stretching one fixed bitmap. The saving is real — at 7x a
 * 42x32 map has about a dozen of its 1344 hexes visible — but the failure mode is the quiet one
 * this codebase keeps meeting: a predicate that is one comparison too strict drops a row of hexes
 * at the edge, and it only shows at particular scroll offsets, on particular window sizes.
 *
 * So the boundary cases are the point here, not the obvious middle.
 */
describe('boxIntersectsView', () => {
  // A 100x80 window onto the world, its top-left corner at (200, 100).
  const view: ViewRect = { x: 200, y: 100, w: 100, h: 80 };
  const HALF = 10;

  const visible = (cx: number, cy: number) => boxIntersectsView(cx, cy, HALF, HALF, view);

  it('sees a box in the middle of the view', () => {
    expect(visible(250, 140)).toBe(true);
  });

  it('sees a box whose centre is outside but whose edge laps in', () => {
    expect(visible(195, 140)).toBe(true);   // 5px left of the edge, 10px half-width
    expect(visible(305, 140)).toBe(true);
    expect(visible(250, 95)).toBe(true);
    expect(visible(250, 185)).toBe(true);
  });

  it('counts a box touching the boundary exactly', () => {
    // Its right edge lands precisely on view.x: one column of pixels, still one column.
    expect(visible(view.x - HALF, 140)).toBe(true);
    expect(visible(view.x + view.w + HALF, 140)).toBe(true);
    expect(visible(250, view.y - HALF)).toBe(true);
    expect(visible(250, view.y + view.h + HALF)).toBe(true);
  });

  it('skips a box one pixel clear of the boundary, on every side', () => {
    expect(visible(view.x - HALF - 1, 140)).toBe(false);
    expect(visible(view.x + view.w + HALF + 1, 140)).toBe(false);
    expect(visible(250, view.y - HALF - 1)).toBe(false);
    expect(visible(250, view.y + view.h + HALF + 1)).toBe(false);
  });

  it('needs BOTH axes to overlap', () => {
    // Horizontally inside, vertically far below — a box that a one-axis test would wrongly keep.
    expect(visible(250, 400)).toBe(false);
    expect(visible(900, 140)).toBe(false);
  });

  it('sees a box far larger than the view', () => {
    expect(boxIntersectsView(250, 140, 5000, 5000, view)).toBe(true);
  });

  it('handles a zero-size view without claiming everything is visible', () => {
    const empty: ViewRect = { x: 0, y: 0, w: 0, h: 0 };
    expect(boxIntersectsView(0, 0, 1, 1, empty)).toBe(true);
    expect(boxIntersectsView(50, 0, 1, 1, empty)).toBe(false);
  });

  /**
   * The real geometry, at the zoom that motivated all of this. HexGrid's SIZE is 48 and a hex is
   * SQRT3 * 48 tall, so hex centres sit 72 world pixels apart horizontally. At 7x on a 1200px-wide
   * viewport the view is about 171 world pixels across — three or four columns — and the count
   * below is what keeps the saving honest rather than assumed.
   */
  it('keeps only a handful of a 42x32 map at high zoom', () => {
    const SIZE = 48;
    const SQRT3 = Math.sqrt(3);
    const H = SQRT3 * SIZE;
    const PADDING = 12;
    const hexCenter = (col: number, row: number): [number, number] => [
      PADDING + SIZE + (col - 1) * SIZE * 1.5,
      PADDING + H / 2 + (row - 1) * H + (col % 2 === 0 ? H / 2 : 0),
    ];

    const zoomed: ViewRect = { x: 1000, y: 800, w: 1200 / 7, h: 800 / 7 };
    let kept = 0;
    for (let col = 1; col <= 42; col++)
      for (let row = 1; row <= 32; row++) {
        const [cx, cy] = hexCenter(col, row);
        if (boxIntersectsView(cx, cy, SIZE, H / 2, zoomed)) kept++;
      }

    expect(kept).toBeGreaterThan(0);          // something is on screen
    expect(kept).toBeLessThan(42 * 32 / 20);  // and it is a small fraction of 1344
  });
});
