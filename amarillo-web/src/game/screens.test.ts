import { describe, it, expect } from 'vitest';
import { screenFor } from './screens';

/**
 * Where a game belongs when a client picks it up — the one piece of real logic in the save UI.
 *
 * "Started" is not enough on its own. A battle restored from a save is already started, but its
 * ships belong to nobody: a save records which TEAM held each ship, never which person.
 * Assignment lives on the pre-game screen, so routing a restored battle to the map by its
 * `started` flag alone would strand the host with a fleet they cannot give to anybody — and no
 * way back, because the map has no assignment controls.
 */
describe('screenFor', () => {
  it('sends an ordinary started game to the map', () => {
    expect(screenFor({ started: true })).toBe('game');
  });

  it('sends a game that has not started to pre-game', () => {
    expect(screenFor({ started: false })).toBe('pregame');
  });

  /** The case this function was written for. */
  it('sends a restored battle with unclaimed ships to pre-game, despite being started', () => {
    expect(screenFor({ started: true, restored: true, unassignedShips: 2 })).toBe('pregame');
  });

  /** Once the host has handed them out it is an ordinary game, including after a reload. */
  it('sends a fully assigned restored battle to the map', () => {
    expect(screenFor({ started: true, restored: true, unassignedShips: 0 })).toBe('game');
  });

  /**
   * An older server does not send these fields at all, and Jackson serialises an absent value as
   * null rather than omitting it, so the guard must tolerate both and fall back to `started`.
   */
  it('falls back to started when the new fields are absent', () => {
    expect(screenFor({ started: true, restored: undefined })).toBe('game');
    expect(screenFor({ started: true, restored: true })).toBe('game');
  });
});
