import { useRef, useState } from 'react';

/**
 * Drag a floating panel by a handle.
 *
 * Extracted from EnergyAllocationDialog, which had it inline, so the SSD panel could move
 * too without a second copy of the same twenty lines going slowly out of step with the
 * first.
 *
 * Listeners go on the window rather than the handle, so a fast drag that outruns the
 * pointer keeps tracking instead of dropping the panel mid-flight, and they are removed on
 * mouse-up.
 *
 * The drag is BOUNDED, because an unbounded one is a trap: drag a panel off the top of the
 * window and its title bar goes with it, taking the close button and the only grip for
 * dragging it back. The panel can hang off an edge, but its handle always keeps a foot on
 * screen.
 */

/** How much of the handle must stay visible horizontally. */
const MIN_VISIBLE = 80;

export interface Draggable {
  /** Spread onto the panel: absolute position in viewport coordinates. */
  position: { left: number; top: number };
  /** Spread onto whatever should act as the title bar. */
  handleProps: { onMouseDown: (e: React.MouseEvent) => void };
  /** For repositioning from outside, e.g. when a panel is reopened. */
  setPosition: (p: { left: number; top: number }) => void;
}

export function useDraggable(initial: { left: number; top: number }): Draggable {
  const [position, setPosition] = useState(initial);
  const dragRef = useRef<{ x: number; y: number; left: number; top: number } | null>(null);

  function onMouseDown(e: React.MouseEvent) {
    if (e.button !== 0) return;
    // A title bar usually carries a close button. Starting a drag from it would be
    // harmless but makes the button feel unresponsive, so let the control have the event.
    if ((e.target as HTMLElement).closest('button, input, select, textarea')) return;

    e.preventDefault();
    dragRef.current = { x: e.clientX, y: e.clientY, left: position.left, top: position.top };

    // Where the handle sits inside the panel, measured once: the bounds below are about
    // keeping the HANDLE reachable, not the panel, since the handle is what carries the
    // close button and the grip.
    const handle = e.currentTarget as HTMLElement;
    const rect = handle.getBoundingClientRect();
    const offsetX = rect.left - position.left;
    const offsetY = rect.top - position.top;

    function clamp(left: number, top: number) {
      const maxLeft = window.innerWidth - MIN_VISIBLE - offsetX;
      const minLeft = MIN_VISIBLE - rect.width - offsetX;
      const minTop = -offsetY;                                   // handle flush with the top
      const maxTop = window.innerHeight - offsetY - rect.height; // and never past the bottom
      return {
        left: Math.min(Math.max(left, minLeft), maxLeft),
        top: Math.min(Math.max(top, minTop), Math.max(minTop, maxTop)),
      };
    }

    function onMove(ev: MouseEvent) {
      const start = dragRef.current;
      if (!start) return;
      setPosition(clamp(
        start.left + ev.clientX - start.x,
        start.top + ev.clientY - start.y,
      ));
    }
    function onUp() {
      dragRef.current = null;
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    }

    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  }

  return { position, handleProps: { onMouseDown }, setPosition };
}
