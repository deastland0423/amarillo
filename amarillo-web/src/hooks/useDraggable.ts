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
 */
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

    function onMove(ev: MouseEvent) {
      const start = dragRef.current;
      if (!start) return;
      setPosition({
        left: start.left + ev.clientX - start.x,
        top:  start.top  + ev.clientY - start.y,
      });
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
