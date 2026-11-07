/**
 * Whether the Save button is live, and what its tooltip says.
 *
 * Extracted as a pure function because the rule it mirrors is not obvious and got it wrong
 * once: the button was gated on `awaitingAllocation`, which stays true from the FIRST Energy
 * Allocation Form of the turn to the last. A save taken halfway through reloaded with every
 * ship queued again and silently discarded the forms already submitted — including a declared
 * self-destruct (D5.1). Core now answers the narrower question with `pristineTurnBoundary`, and
 * this is the only place the view reads it.
 *
 * The server refuses the same cases regardless (GameController.saveGame), so this is a mirror
 * for the player's benefit, never the gate itself — same contract as every other preview in
 * this folder.
 */
export interface SaveButtonInput {
  /** A save request is already in flight. */
  saving: boolean;
  /** Game.isPristineTurnBoundary — the start of a turn, before anybody has allocated. */
  pristineTurnBoundary?: boolean | null;
  /** Game.isAwaitingAllocation — true for the whole allocation round. */
  awaitingAllocation?: boolean | null;
}

export interface SaveButtonState {
  disabled: boolean;
  title: string;
}

export function saveButtonState(input: SaveButtonInput): SaveButtonState {
  const { saving, pristineTurnBoundary, awaitingAllocation } = input;

  // `!= null` and not `!== undefined`: Jackson writes an absent boolean as `null` rather than
  // omitting the key, and `null !== undefined` is true — the trap this codebase has shipped
  // before. A null here means "the server did not say", which is not a reason to offer a save.
  const pristine = pristineTurnBoundary != null && pristineTurnBoundary;
  const allocating = awaitingAllocation != null && awaitingAllocation;

  if (pristine) {
    return {
      disabled: saving,
      title: 'Save this battle — it can be resumed later, by anybody',
    };
  }
  return {
    disabled: true,
    title: allocating
      ? 'A battle can only be saved before the first energy allocation of the turn is submitted'
      : 'A battle can only be saved between turns, during energy allocation',
  };
}
