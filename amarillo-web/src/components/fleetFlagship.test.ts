import { describe, it, expect } from 'vitest';
import { bestCommander } from './fleetFlagship';
import type { CommandCandidate } from './fleetFlagship';

function ship(name: string, commandRating: number, bpv = 100): CommandCandidate {
  return { name, commandRating, bpv };
}

describe('bestCommander', () => {
  it('gives the flag to the highest command rating', () => {
    expect(bestCommander([ship('FF', 2), ship('CA', 8), ship('DD', 4)])).toBe('CA');
  });

  /**
   * The case that rules out "first ship purchased".
   *
   * The escort was bought first and can lead two ships. Hand it the flag and a five-ship force
   * is refused by S8.21's command limit — a violation the player did not have before the default
   * existed, and a more confusing one than the missing flagship it replaced.
   */
  it('does not simply take the first ship on the roster', () => {
    expect(bestCommander([ship('FF Nimble', 2), ship('CA Valiant', 8)])).toBe('CA Valiant');
  });

  it('breaks a tie with the bigger ship', () => {
    expect(bestCommander([
      ship('CL', 6, 120), ship('CA', 6, 160), ship('DW', 6, 90),
    ])).toBe('CA');
  });

  /**
   * S8.21: a hull with no command rating cannot hold the flag. A force entirely of them has no
   * flagship to assign, which the validator reports as its own fault — "nothing here can lead a
   * battle force" — rather than as an unmade choice.
   */
  it('finds nobody when no hull can command', () => {
    expect(bestCommander([ship('FF', 0), ship('PC', 0)])).toBeUndefined();
  });

  it('finds nobody in an empty force', () => {
    expect(bestCommander([])).toBeUndefined();
  });

  /**
   * A nameless entry is not a candidate. `FleetSpec.flagship` is matched by NAME, so returning an
   * unnamed ship would fill the post on screen and leave it empty everywhere that counts.
   */
  it('skips a ship with no name, however well it commands', () => {
    expect(bestCommander([
      { name: undefined, commandRating: 10, bpv: 300 }, ship('CA', 6),
    ])).toBe('CA');
  });

  it('leaves the roster it was given alone', () => {
    const roster = [ship('FF', 2), ship('CA', 8)];
    bestCommander(roster);
    expect(roster.map(s => s.name)).toEqual(['FF', 'CA']);
  });
});
