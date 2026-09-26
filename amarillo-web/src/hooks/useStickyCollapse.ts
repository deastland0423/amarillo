import { useEffect, useState } from 'react';

/**
 * A panel's collapsed state, remembered across impulses.
 *
 * The orders pads live for a whole segment and reappear on every impulse of it — thirty-two
 * a turn, on maybe three of which you actually fire. Collapsing one only lasted until the
 * next impulse re-rendered it open, so the choice had to be made again and again.
 *
 * Collapsed is the DEFAULT for a pad nobody has touched: the collapsed header keeps the
 * segment's mandatory actions (Commit, Pass) reachable, so a pad that starts small is out of
 * the way without being out of reach.
 *
 * Sits alongside the dragged position, which these pads already persist the same way.
 */

/** Just enough of Storage to read and write one flag; a fake in tests, localStorage in play. */
export interface FlagStore {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

/** The browser's store, or null where there is none (a test runner, a locked-down browser). */
function browserStore(): FlagStore | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage;
  } catch {
    return null;   // some browsers throw on the property itself when site data is blocked
  }
}

/**
 * Whether the panel should start collapsed. Anything unreadable — no storage, blocked
 * storage, a value written by some older version — reads as "never been set", which is
 * collapsed.
 */
export function readCollapsed(key: string, store: FlagStore | null = browserStore()): boolean {
  if (store === null) return true;
  try {
    const raw = store.getItem(key);
    if (raw === 'true') return true;
    if (raw === 'false') return false;
    return true;
  } catch {
    return true;
  }
}

/** Remember the choice. Failing to is not worth an error: the panel just forgets. */
export function writeCollapsed(
  key: string, collapsed: boolean, store: FlagStore | null = browserStore(),
): void {
  if (store === null) return;
  try { store.setItem(key, String(collapsed)); }
  catch { /* storage blocked; the panel forgets its size */ }
}

export function useStickyCollapse(storageKey: string): [boolean, (next: boolean) => void] {
  const [collapsed, setCollapsed] = useState<boolean>(() => readCollapsed(storageKey));

  useEffect(() => { writeCollapsed(storageKey, collapsed); }, [storageKey, collapsed]);

  return [collapsed, setCollapsed];
}
