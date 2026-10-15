import { describe, it, expect } from 'vitest';
import { ACCOUNT_KEY, resolveAccountId, mintAccountId } from './playerIdentity';

/** A storage double that can be told to fail, the way a private window does. */
function store(initial: Record<string, string> = {}, mode: 'ok' | 'throwRead' | 'throwWrite' = 'ok') {
  const data = { ...initial };
  return {
    data,
    getItem(key: string) {
      if (mode === 'throwRead') throw new DOMException('denied');
      return data[key] ?? null;
    },
    setItem(key: string, value: string) {
      if (mode === 'throwWrite') throw new DOMException('quota');
      data[key] = value;
    },
  };
}

describe('resolveAccountId', () => {
  it('mints an id and stores it under the account key', () => {
    const s = store();

    const id = resolveAccountId(s, () => 'minted-1');

    expect(id).toBe('minted-1');
    expect(s.data[ACCOUNT_KEY]).toBe('minted-1');
  });

  /** The whole purpose: the same browser must present the same identity next time. */
  it('returns the stored id rather than minting a second one', () => {
    const s = store({ [ACCOUNT_KEY]: 'already-mine' });
    let mints = 0;

    const id = resolveAccountId(s, () => `minted-${++mints}`);

    expect(id).toBe('already-mine');
    expect(mints).toBe(0);
  });

  it('is stable across repeated calls on one store', () => {
    const s = store();
    let n = 0;
    const mint = () => `minted-${++n}`;

    expect(resolveAccountId(s, mint)).toBe(resolveAccountId(s, mint));
  });

  /**
   * An identity that cannot be remembered is not a reason to refuse to play. A usable id still
   * comes back; it simply will not outlive the page, which is how rejoining behaved before any
   * of this existed.
   */
  it('still yields an id when storage cannot be read', () => {
    expect(resolveAccountId(store({}, 'throwRead'), () => 'fallback')).toBe('fallback');
  });

  it('still yields an id when storage cannot be written', () => {
    const s = store({}, 'throwWrite');

    expect(resolveAccountId(s, () => 'fallback')).toBe('fallback');
    expect(s.data[ACCOUNT_KEY]).toBeUndefined();
  });

  it('copes with storage being absent entirely', () => {
    expect(resolveAccountId(null, () => 'fallback')).toBe('fallback');
    expect(resolveAccountId(undefined, () => 'fallback')).toBe('fallback');
  });

  /** An empty string is not an identity — treat it as absent and mint. */
  it('treats a blank stored value as nothing held', () => {
    const s = store({ [ACCOUNT_KEY]: '' });

    expect(resolveAccountId(s, () => 'minted')).toBe('minted');
  });
});

describe('mintAccountId', () => {
  it('produces distinct non-empty ids', () => {
    const ids = new Set(Array.from({ length: 50 }, mintAccountId));

    expect(ids.size).toBe(50);
    for (const id of ids) expect(id.length).toBeGreaterThan(8);
  });
});
