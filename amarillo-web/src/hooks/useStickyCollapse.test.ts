import { describe, it, expect } from 'vitest';
import { readCollapsed, writeCollapsed, type FlagStore } from './useStickyCollapse';

/**
 * The orders pads reappear on every impulse of their segment — thirty-two a turn — and
 * collapsing one only lasted until the next render opened it again. So the choice is
 * remembered, and a pad nobody has touched starts collapsed.
 */

function fakeStore(initial: Record<string, string> = {}): FlagStore {
  const data = { ...initial };
  return {
    getItem: (k) => (k in data ? data[k] : null),
    setItem: (k, v) => { data[k] = v; },
  };
}

/** A browser with site data switched off: every call throws. */
const blockedStore: FlagStore = {
  getItem: () => { throw new Error('blocked'); },
  setItem: () => { throw new Error('blocked'); },
};

describe('sticky collapse', () => {
  it('starts collapsed when nothing has been stored', () => {
    expect(readCollapsed('pad', fakeStore())).toBe(true);
  });

  it('remembers being opened', () => {
    const store = fakeStore();
    writeCollapsed('pad', false, store);

    expect(readCollapsed('pad', store)).toBe(false);
  });

  it('remembers being collapsed again', () => {
    const store = fakeStore();
    writeCollapsed('pad', false, store);
    writeCollapsed('pad', true, store);

    expect(readCollapsed('pad', store)).toBe(true);
  });

  it('keeps each pad separate', () => {
    const store = fakeStore();
    writeCollapsed('fire', false, store);

    expect(readCollapsed('fire', store)).toBe(false);
    expect(readCollapsed('launch', store)).toBe(true);
  });

  it('treats anything it cannot read as never set', () => {
    expect(readCollapsed('pad', fakeStore({ pad: 'yes please' }))).toBe(true);
    expect(readCollapsed('pad', null)).toBe(true);
    expect(readCollapsed('pad', blockedStore)).toBe(true);
  });

  it('does not throw when storage is blocked', () => {
    expect(() => writeCollapsed('pad', false, blockedStore)).not.toThrow();
    expect(() => writeCollapsed('pad', false, null)).not.toThrow();
  });
});
