/**
 * A durable identity for this browser, kept separate from the seat it holds in any one game.
 *
 * Two different identifiers, and keeping them apart is the point:
 *
 * - the **account id** here is durable and belongs to a person. It outlives a game, and the same
 *   one may hold seats in several games at once.
 * - a **seat token** (`playerToken` in `LobbyResult`) is per-game, and is what the server keys
 *   every piece of per-player state on. Ships bind to the seat.
 *
 * One string used to do both jobs on the server, which made rejoining destructive: a join minted
 * a fresh token every time, so coming back by game code arrived as a stranger while your ships
 * stayed bound to the token you left. Sending the account id on create and join lets the server
 * hand back the seat you already hold — the precondition for a game that can be saved and
 * resumed another day.
 *
 * This is deliberately NOT a login. There is no password and no proof: it identifies a browser,
 * not a person, so it is good enough to find your own seat again and nothing more. Treat it as a
 * convenience, never as authorisation.
 */

/** Where the durable id lives. Distinct from `amarillo_session`, which holds the current seat. */
export const ACCOUNT_KEY = 'amarillo_account';

/** The narrow slice of Storage this needs, so a test can supply its own. */
export interface IdentityStore {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

/**
 * Read the stored account id, or mint and store one.
 *
 * Every access is wrapped: a private window, cleared site data or a blocked-storage policy makes
 * `localStorage` throw rather than return null, and an identity that cannot be remembered is not
 * a reason to refuse to play. When storage is unavailable this still returns a usable id — it
 * just will not survive the page, which degrades rejoining back to how it behaved before.
 */
export function resolveAccountId(
  store: IdentityStore | null | undefined,
  mint: () => string,
): string {
  if (store) {
    try {
      const held = store.getItem(ACCOUNT_KEY);
      if (held) return held;
    } catch {
      return mint();
    }
    const fresh = mint();
    try {
      store.setItem(ACCOUNT_KEY, fresh);
    } catch {
      // Unwritable storage: the id is still good for this page's lifetime.
    }
    return fresh;
  }
  return mint();
}

/**
 * A random id. Prefers `crypto.randomUUID`, which is not present on every browser or over plain
 * HTTP on some, so there is a fallback — this id needs to be unique, not unguessable.
 */
export function mintAccountId(): string {
  const c: Crypto | undefined = typeof crypto !== 'undefined' ? crypto : undefined;
  if (c && typeof c.randomUUID === 'function') return c.randomUUID();
  return `acct-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

/** This browser's durable account id, minted on first use. */
export function accountId(): string {
  const store = typeof localStorage !== 'undefined' ? localStorage : null;
  return resolveAccountId(store, mintAccountId);
}
