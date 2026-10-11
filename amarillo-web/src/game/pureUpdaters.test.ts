import { describe, expect, it } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, sep } from 'node:path';

/**
 * No state updater may do anything but compute its next value.
 *
 * React calls `setX(prev => ...)` updaters more than once — StrictMode double-invokes them in
 * development on purpose, to surface the ones that are not pure. So an updater that also fires a
 * request, or calls another setter, does that work twice.
 *
 * Both instances we have had were invisible until a player hit them:
 *
 * 1. `GameBoard`'s combat-log flush called `setLog` inside a `setPendingCombat` updater, so the
 *    buffered fire results were appended twice. Reported 2026-10-11 as a whole fire declaration
 *    printed twice over — identical die rolls in both copies, which is what says it resolved once
 *    and was logged twice. The duplicate was the entire Direct Fire buffer in one contiguous
 *    block, not each broadcast doubled, which is what pointed at the flush rather than the wire.
 * 2. `useGameSocket` re-fetched the REST snapshot from inside a `setState` updater, so every
 *    broadcast arriving before ships were assigned cost two round trips.
 *
 * Neither shows up in a type-check, a lint run or any component test, and neither breaks
 * anything in a production build — StrictMode is development-only, which is exactly where the
 * game is played and where both were seen. Hence a source scan.
 */
describe('state updaters are pure', () => {
  const SRC = join(process.cwd(), 'src');

  /** setTimeout/setInterval take a callback, not an updater, and may do as they like. */
  const TIMERS = /^set(Timeout|Interval|Immediate)$/;

  /** Work that must not happen inside an updater: another setter, or a request. */
  const SIDE_EFFECT = /\b(set[A-Z]\w*\s*\(|gameApi\s*\.|fetch\s*\(|axios\s*\.)/;

  function sourceFiles(dir: string): string[] {
    return readdirSync(dir).flatMap(entry => {
      const path = join(dir, entry);
      if (statSync(path).isDirectory()) return sourceFiles(path);
      return /\.tsx?$/.test(entry) && !/\.test\.tsx?$/.test(entry) ? [path] : [];
    });
  }

  it('no updater fires a request or calls another setter', () => {
    const offences: string[] = [];

    for (const file of sourceFiles(SRC)) {
      const lines = readFileSync(file, 'utf8').split(/\r?\n/);
      for (let i = 0; i < lines.length; i++) {
        // An updater that opens a block: setX(prev => {  /  setX((prev) => {
        const opener = /\b(set[A-Z]\w*)\s*\(\s*\(?\s*\w*\s*\)?\s*=>\s*\{/.exec(lines[i]);
        if (!opener || TIMERS.test(opener[1])) continue;

        // Walk to the end of that block by brace depth, which is enough for source we control.
        let depth = 0;
        for (let j = i; j < Math.min(i + 60, lines.length); j++) {
          const text = j === i ? lines[j].slice(lines[j].indexOf('=>')) : lines[j];
          depth += (text.match(/\{/g) ?? []).length - (text.match(/\}/g) ?? []).length;
          if (j > i && SIDE_EFFECT.test(text.replace(/\/\/.*$/, ''))) {
            const where = relative(process.cwd(), file).split(sep).join('/');
            offences.push(`${where}:${j + 1} — ${opener[1]}'s updater does work: `
              + text.trim().slice(0, 70));
            break;
          }
          if (depth <= 0) break;
        }
      }
    }

    expect(offences, 'a state updater must only compute its next value — React may call it twice,'
      + ' so anything else here happens twice:\n  ' + offences.join('\n  ')).toEqual([]);
  });

  it('fixture: the scan finds the pattern it is looking for', () => {
    // Guards that pass because they never matched anything are the ones that rot. This proves the
    // walker still detects the shape, using the real GameBoard bug as the sample.
    const sample = [
      "      if (leaving) {",
      "        setPendingCombat(queued => {",
      "          if (queued.length > 0) {",
      "            setLog(prev => [...prev, ...queued]);",
      "          }",
      "          return [];",
      "        });",
      "      }",
    ];
    const opener = /\b(set[A-Z]\w*)\s*\(\s*\(?\s*\w*\s*\)?\s*=>\s*\{/.exec(sample[1]);
    expect(opener?.[1]).toBe('setPendingCombat');
    expect(SIDE_EFFECT.test(sample[3])).toBe(true);
  });
});
