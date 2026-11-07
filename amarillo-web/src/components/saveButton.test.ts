import { describe, it, expect } from 'vitest';
import { saveButtonState } from './saveButton';

/**
 * The Save button's gate. Written because the old one was wrong in a way nothing caught: it
 * read `awaitingAllocation`, so it stayed live through the whole allocation round and offered
 * a save that silently lost the forms already submitted.
 */
describe('saveButtonState', () => {
  it('is live at the start of a turn, before anybody has allocated', () => {
    const s = saveButtonState({
      saving: false,
      pristineTurnBoundary: true,
      awaitingAllocation: true,
    });

    expect(s.disabled).toBe(false);
    expect(s.title).toContain('resumed later');
  });

  it('is dead once one ship has allocated, and says which moment to use', () => {
    const s = saveButtonState({
      saving: false,
      pristineTurnBoundary: false,
      awaitingAllocation: true,
    });

    expect(s.disabled).toBe(true);
    expect(s.title).toContain('before the first energy allocation');
  });

  it('is dead mid-turn, with the older wording', () => {
    const s = saveButtonState({
      saving: false,
      pristineTurnBoundary: false,
      awaitingAllocation: false,
    });

    expect(s.disabled).toBe(true);
    expect(s.title).toContain('between turns');
  });

  it('stays dead while a save is already in flight', () => {
    const s = saveButtonState({
      saving: true,
      pristineTurnBoundary: true,
      awaitingAllocation: true,
    });

    expect(s.disabled).toBe(true);
  });

  /**
   * Jackson writes an absent boolean as `"field": null` rather than omitting the key, and
   * `null !== undefined` is true — so a guard written as `!== undefined` would read null as a
   * live save. An older server that does not send the field at all must not offer one either.
   */
  it('treats a null or missing flag as "no save", not as a yes', () => {
    expect(saveButtonState({
      saving: false,
      pristineTurnBoundary: null,
      awaitingAllocation: true,
    }).disabled).toBe(true);

    expect(saveButtonState({ saving: false }).disabled).toBe(true);
    expect(saveButtonState({ saving: false }).title).toContain('between turns');
  });
});
