import { describe, expect, it } from 'vitest';
import { bankLabel, bankSummary, banksToDraw, boxLook, countStates } from './boxBank';
import type { BoxBank, BoxState, SsdBox } from '../types/gameState';

/**
 * The boxes that cool off, drawn rather than counted.
 *
 * The case these exist for: a bank with every box undamaged and every box spent. Three of the
 * five states mean "cannot use this now" and only one of them means damage, so any readout
 * that collapses them lies in one direction or the other. The old Support line printed
 * "transporters 3/3" for exactly that bank.
 */

const box = (number: number, state: BoxState, holding?: string): SsdBox =>
  ({ number, state, holding: holding ?? null });

const bank = (system: string, boxes: SsdBox[], holding: number | null = null): BoxBank => ({
  system,
  printed: boxes.length,
  intact:  boxes.filter(b => b.state !== 'DESTROYED').length,
  ready:   boxes.filter(b => b.state === 'UNUSED').length,
  holding,
  boxes,
});

describe('the row label', () => {
  it('shortens the SSD wording that will not fit the label column', () => {
    expect(bankLabel('Laboratories')).toBe('Labs');
    expect(bankLabel('Tractor Beams')).toBe('Tractors');
  });

  it('passes anything else through, so a new bank is readable before it is styled', () => {
    expect(bankLabel('Transporters')).toBe('Transporters');
    expect(bankLabel('Probes')).toBe('Probes');
  });
});

describe('the summary beside the boxes', () => {
  it('says only the size when every box is free', () => {
    // The boxes are already drawn green; counting them would be the same fact twice.
    expect(bankSummary(bank('Transporters',
      [box(1, 'UNUSED'), box(2, 'UNUSED'), box(3, 'UNUSED')]))).toBe('3');
  });

  it('names what is wrong the moment anything is', () => {
    expect(bankSummary(bank('Transporters',
      [box(1, 'UNUSED'), box(2, 'USED_THIS_TURN'), box(3, 'DESTROYED')])))
      .toBe('1 of 3 free · 1 used · 1 destroyed');
  });

  it('distinguishes a bank that is spent from one that is wrecked', () => {
    // The whole point. Both have nothing free; one is back next turn and one never is.
    const spent   = bank('Transporters', [box(1, 'USED_THIS_TURN'), box(2, 'USED_THIS_TURN')]);
    const wrecked = bank('Transporters', [box(1, 'DESTROYED'), box(2, 'DESTROYED')]);

    expect(bankSummary(spent)).toBe('0 of 2 free · 2 used');
    expect(bankSummary(wrecked)).toBe('0 of 2 free · 2 destroyed');
    expect(spent.intact).toBe(2);
    expect(wrecked.intact).toBe(0);
  });

  it('reports holding first, because it is what the player can still act on', () => {
    const t = bank('Tractor Beams',
      [box(1, 'HOLDING', 'IKV Saber'), box(2, 'COOLING_DOWN'), box(3, 'UNUSED')], 1);
    expect(bankSummary(t)).toBe('1 of 3 free · 1 holding · 1 cooling');
  });

  it('counts from the BOXES, so the words cannot disagree with the drawing', () => {
    // A bank whose flat fields are stale or wrong still describes itself correctly, because
    // the summary never reads them.
    const lying: BoxBank = {
      system: 'Transporters', printed: 3, intact: 99, ready: 99, holding: null,
      boxes: [box(1, 'UNUSED'), box(2, 'DESTROYED'), box(3, 'DESTROYED')],
    };
    expect(bankSummary(lying)).toBe('1 of 3 free · 2 destroyed');
  });
});

describe('how a box is drawn', () => {
  it('fills a free box and leaves a busy one hollow', () => {
    expect(boxLook(box(1, 'UNUSED')).fill).not.toBe('transparent');
    expect(boxLook(box(1, 'USED_THIS_TURN')).fill).toBe('transparent');
    expect(boxLook(box(1, 'COOLING_DOWN')).fill).toBe('transparent');
  });

  it('gives spent and cooling different colours, which is the distinction worth drawing', () => {
    // Both mean "not now". One clears at the turn boundary and the other has already survived
    // one, which is the difference between waiting two impulses and waiting ten.
    expect(boxLook(box(1, 'USED_THIS_TURN')).border)
      .not.toBe(boxLook(box(1, 'COOLING_DOWN')).border);
  });

  it('strikes through only a destroyed box', () => {
    const states: BoxState[] = ['UNUSED', 'USED_THIS_TURN', 'COOLING_DOWN', 'HOLDING'];
    for (const s of states)
      expect(boxLook(box(1, s)).struck, s).toBe(false);
    expect(boxLook(box(1, 'DESTROYED')).struck).toBe(true);
  });

  it('names what a beam holds, because a tug has several and they are not interchangeable', () => {
    expect(boxLook(box(2, 'HOLDING', 'IKV Saber')).title).toBe('holding IKV Saber');
  });

  it('survives a HOLDING box that arrives with no name', () => {
    // Jackson writes an absent value as null, so the guard has to be != null. `undefined`
    // never arrives over the wire, but a hand-built object in a test might.
    expect(boxLook({ number: 2, state: 'HOLDING', holding: null }).title).toBe('holding');
    expect(boxLook({ number: 2, state: 'HOLDING' }).title).toBe('holding');
  });

  it('gives every state a distinct hover word, since that is where they are named', () => {
    const states: BoxState[] = ['UNUSED', 'USED_THIS_TURN', 'COOLING_DOWN', 'HOLDING', 'DESTROYED'];
    const titles = states.map(s => boxLook(box(1, s)).title);
    expect(new Set(titles).size).toBe(states.length);
  });
});

describe('which banks are drawn', () => {
  it('draws nothing at all for a server that sends no banks', () => {
    // An older server, or a unit that is not a ship. The row must vanish, not render empty.
    expect(banksToDraw(undefined)).toEqual([]);
  });

  it('keeps the order the server sent, which is SSD order', () => {
    const banks = [bank('Laboratories', [box(1, 'UNUSED')]),
                   bank('Transporters', [box(1, 'UNUSED')]),
                   bank('Tractor Beams', [box(1, 'UNUSED')], 0)];
    expect(banksToDraw(banks).map(b => b.system))
      .toEqual(['Laboratories', 'Transporters', 'Tractor Beams']);
  });

  it('drops a bank with nothing printed rather than drawing a bare heading', () => {
    expect(banksToDraw([bank('Laboratories', [])]).length).toBe(0);
  });
});

describe('the state counts', () => {
  it('reports a zero for every state, so a caller never reads undefined', () => {
    const c = countStates(bank('Labs', [box(1, 'UNUSED')]));
    expect(c).toEqual({
      UNUSED: 1, USED_THIS_TURN: 0, COOLING_DOWN: 0, HOLDING: 0, DESTROYED: 0,
    });
  });
});
