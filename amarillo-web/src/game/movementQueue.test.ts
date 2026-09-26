import { describe, it, expect } from 'vitest';
import { movementPrompt } from './movementQueue';

/**
 * The playtest bug, in one line: on an impulse where both fleets had a ship to move, BOTH
 * players were told "Move: <their own ship>", and the server then refused whichever was not
 * at the head of the queue.
 *
 * The queue is global and slowest-first. Only its head may move — so only the head's owner
 * should be prompted, however many ships the other player has waiting.
 */

const MINE = ['USS Enterprise', 'USS Kearsarge'];
const isMine = (name: string) => MINE.includes(name);

describe('movementPrompt', () => {
  it('prompts me when my ship is at the head', () => {
    // Enterprise is slower, so it moves first.
    expect(movementPrompt(['USS Enterprise', 'IKV Saber'], isMine))
      .toEqual({ kind: 'move', ship: 'USS Enterprise' });
  });

  it('tells me to wait when the head is theirs, even though I also have a ship pending', () => {
    // The bug: this case used to read "Move: USS Enterprise" for me and "Move: IKV Saber"
    // for my opponent, at the same moment.
    expect(movementPrompt(['IKV Saber', 'USS Enterprise'], isMine))
      .toEqual({ kind: 'wait', ship: 'IKV Saber', mineNext: 'USS Enterprise' });
  });

  it('names my next ship so waiting is not mistaken for being forgotten', () => {
    const prompt = movementPrompt(['IKV Saber', 'IKS Fury', 'USS Kearsarge'], isMine);
    expect(prompt).toEqual({
      kind: 'wait', ship: 'IKV Saber', mineNext: 'USS Kearsarge',
    });
  });

  it('has nothing to add when I have no ship in the queue at all', () => {
    expect(movementPrompt(['IKV Saber'], isMine))
      .toEqual({ kind: 'wait', ship: 'IKV Saber', mineNext: null });
  });

  it('says nothing once the queue is empty', () => {
    expect(movementPrompt([], isMine)).toEqual({ kind: 'none' });
  });

  it('reads the head, not the list, so order is what decides', () => {
    // Same two ships, opposite order: the answer flips. Nothing else about the input changed.
    expect(movementPrompt(['USS Enterprise', 'IKV Saber'], isMine).kind).toBe('move');
    expect(movementPrompt(['IKV Saber', 'USS Enterprise'], isMine).kind).toBe('wait');
  });
});
