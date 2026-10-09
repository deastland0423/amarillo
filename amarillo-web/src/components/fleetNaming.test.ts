import { describe, expect, it } from 'vitest';
import { isGeneratedName, nameForNewShip } from './fleetNaming';

describe('nameForNewShip', () => {
  it('gives the first of a kind the hull its own name', () => {
    expect(nameForNewShip('F5', 'IKS Fury', [])).toBe('IKS Fury');
  });

  /** The case the whole module exists for: three F5s, one error avoided. */
  it('numbers the ones after it', () => {
    const taken: string[] = [];
    for (let i = 0; i < 3; i++) taken.push(nameForNewShip('F5', 'IKS Fury', taken));
    expect(taken).toEqual(['IKS Fury', 'F5 #2', 'F5 #3']);
  });

  /**
   * Lowest unused, not a count. Buy three, delete the middle one, buy a fourth: counting would
   * hand back "F5 #3" and recreate the duplicate this is here to prevent.
   */
  it('fills a gap left by a deletion instead of repeating a number', () => {
    expect(nameForNewShip('F5', 'IKS Fury', ['IKS Fury', 'F5 #3'])).toBe('F5 #2');
  });

  /** The validator compares case-insensitively and trimmed, so a clash is a clash. */
  it('treats case and surrounding space as the same name', () => {
    expect(nameForNewShip('F5', 'IKS Fury', ['  iks fury  '])).toBe('F5 #2');
    expect(nameForNewShip('F5', 'IKS Fury', ['IKS Fury', 'f5 #2'])).toBe('F5 #3');
  });

  /** A renamed first ship frees its name up again, which is the player's business, not ours. */
  it('reuses a name the player has freed', () => {
    expect(nameForNewShip('F5', 'IKS Fury', ['Something Else'])).toBe('IKS Fury');
  });

  it('numbers from 2 when the hull has no name of its own', () => {
    expect(nameForNewShip('F5', '', [])).toBe('F5 #2');
    expect(nameForNewShip('F5', '   ', [])).toBe('F5 #2');
  });

  /** Blanks in the fleet are not names and must not block one. */
  it('ignores empty entries when deciding what is taken', () => {
    expect(nameForNewShip('F5', 'IKS Fury', ['', '   '])).toBe('IKS Fury');
  });

  /** A type code carries "+" and "-", and both appear in the generated name verbatim. */
  it('handles a refit type code', () => {
    expect(nameForNewShip('CA+', 'USS Constellation', ['USS Constellation'])).toBe('CA+ #2');
    expect(nameForNewShip('GEN-F+', 'HMS Gendarme', ['HMS Gendarme'])).toBe('GEN-F+ #2');
  });
});

describe('isGeneratedName', () => {
  it('recognises a name it generated', () => {
    expect(isGeneratedName('F5', 'F5 #2')).toBe(true);
    expect(isGeneratedName('F5', 'F5 #17')).toBe(true);
  });

  it('leaves a chosen name alone', () => {
    expect(isGeneratedName('F5', 'IKS Fury')).toBe(false);
    expect(isGeneratedName('F5', 'F5 Fury')).toBe(false);
    expect(isGeneratedName('F5', undefined)).toBe(false);
    expect(isGeneratedName('F5', '')).toBe(false);
  });

  /**
   * The escaping test. "+" is a regex quantifier, so an unescaped "CA+" pattern matches "CAAA #2"
   * and — worse — fails to match the "CA+ #2" it actually produced.
   */
  it('does not let a type code act as a regular expression', () => {
    expect(isGeneratedName('CA+', 'CA+ #2')).toBe(true);
    expect(isGeneratedName('CA+', 'CAAA #2')).toBe(false);
    expect(isGeneratedName('GEN-F+', 'GEN-F+ #3')).toBe(true);
  });

  it('ignores case and surrounding space, as the validator does', () => {
    expect(isGeneratedName('F5', '  f5 #2 ')).toBe(true);
  });

  /** A ship of another type that happens to be numbered is not this type's generated name. */
  it('does not claim another type\'s numbering', () => {
    expect(isGeneratedName('F5', 'D7 #2')).toBe(false);
  });
});
