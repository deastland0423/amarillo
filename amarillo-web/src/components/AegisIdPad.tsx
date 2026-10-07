import { useCallback, useEffect, useState } from 'react';
import { gameApi } from '../api/gameApi';
import type { AegisIdCandidate } from '../api/gameApi';
import type { ShipObject } from '../types/gameState';
import { useDraggable } from '../hooks/useDraggable';

/**
 * The aegis identification pad (D13.3): asking a full aegis system what an incoming seeking weapon
 * actually is.
 *
 * ## Why this is its own pad and not a tab on the pulse pad
 *
 * They happen at different times and answer different questions. Identification is a Ship System
 * Functions Stage action (6B4), taken while the seeker is still inbound and you are deciding
 * whether it is worth a shot. The pulse is Direct Fire (D13.11), taken once you have decided. A
 * player who can see both at once is being shown the second half of a decision before making the
 * first, and the two have no fields in common.
 *
 * ## What it must say, or the numbers look arbitrary
 *
 * Two allowances run out independently (D13.31, D13.32): six attempts a turn, and no more than
 * four in any one impulse. A ship that has spent four this impulse still has two in hand for the
 * next, so a pad showing one number would read as broken half the time. Both are shown.
 *
 * And each row shows the die it needs, from D13.31's table — automatic inside three hexes, 1-4 at
 * four, 1-3 at five, a bare 1 at six, nothing beyond. That is the whole decision: at six hexes an
 * attempt is a one-in-six, and a player who cannot see that will spend all six on a drone that was
 * never going to be read.
 *
 * ## D13.321's modifier, which is the subtle one
 *
 * A repeat attempt on the same seeker is one easier — but only if the previous attempt was in an
 * EARLIER impulse, because D13.322 has same-impulse attempts rolled simultaneously and not counting
 * as previous to each other. The server works that out and sends `repeat`; the pad only shows it.
 * Reproducing that here would be a second copy of a rule nobody would keep in step.
 *
 * ## Automatic, like the pulse pad, and for the same reason
 *
 * A seeker is identified while it is still coming at you, so a pad that waited to be opened would
 * be remembered one impulse too late. It appears when there is something to read and attempts left
 * to read it with, and is invisible otherwise.
 */

interface Props {
  gameId: string;
  playerToken: string;
  /** The owner's own full-aegis ship. Everything here is owner-only; see D13.51. */
  ship: ShipObject;
  /** Bumped by the caller when the game state changes, so the list refetches. */
  stateVersion: number;
  onClose: () => void;
}

/** D13.31's table as a phrase. 6 is "automatic" because no die beats it. */
function chanceText(needs: number): string {
  if (needs >= 6) return 'automatic';
  return `${needs} in 6`;
}

export default function AegisIdPad(
  { gameId, playerToken, ship, stateVersion, onClose }: Props) {
  const drag = useDraggable({ left: 24, top: 120 });
  const [targets, setTargets] = useState<AegisIdCandidate[] | null>(null);
  const [target, setTarget] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [last, setLast] = useState<string | null>(null);

  // Null means "this system cannot identify at all" (D13.412), which is why these are nullable on
  // the wire — a primitive would arrive as 0 and read as a full system out of attempts.
  const leftThisTurn = ship.aegisIdAttemptsThisTurn ?? 0;
  const leftThisImpulse = ship.aegisIdAttemptsThisImpulse ?? 0;
  const attemptsLeft = Math.min(leftThisTurn, leftThisImpulse);

  const refresh = useCallback(() => {
    gameApi.getAegisIdTargets(gameId, playerToken, ship.name)
      .then(rows => {
        setTargets(rows);
        // Keep the selection only while it is still offered: a seeker just identified drops off
        // the list, and leaving it selected would spend the next attempt on a refusal.
        setTarget(prev => (prev && rows.some(r => r.name === prev) ? prev : rows[0]?.name ?? null));
      })
      .catch(e => setError(e instanceof Error ? e.message : String(e)));
  }, [gameId, playerToken, ship.name]);

  useEffect(refresh, [refresh, stateVersion]);

  /**
   * Invisible unless there is something to read. The caller mounts this for any own full-aegis
   * ship, so without this the pad would open in every Activity phase of every turn — and a panel
   * that is usually noise is one a player learns to dismiss unread, which defeats opening it for
   * them.
   */
  if (targets == null || targets.length === 0) return null;

  const chosen = targets.find(t => t.name === target) ?? null;
  const canTry = chosen != null && attemptsLeft > 0 && !busy;

  async function attempt() {
    if (!canTry || chosen == null) return;
    setBusy(true);
    setError(null);
    const res = await gameApi.submitAction(gameId, playerToken, {
      type: 'AEGIS_IDENTIFY',
      shipName: ship.name,
      targetName: chosen.name,
    });
    setBusy(false);
    if (!res.success) {
      setError(res.message ?? 'The attempt was refused');
      return;
    }
    // The server's own sentence, which says whether it worked and what was found (D13.34). Shown
    // here as well as in the combat log, because this is where the player is looking.
    setLast(res.message ?? null);
    refresh();
  }

  return (
    <div style={{ ...panelStyle, left: drag.position.left, top: drag.position.top }}>
      <div style={headerStyle} {...drag.handleProps} title="Drag to move">
        <span style={{ fontWeight: 700 }}>
          Aegis ID {String.fromCharCode(183)} {ship.name}
        </span>
        <button className="secondary" style={{ padding: '0 8px' }}
                title="Dismiss for this impulse" onClick={onClose}>
          {String.fromCharCode(10005)}
        </button>
      </div>

      {/* Both allowances, because they run out independently (D13.31/D13.32). */}
      <div style={{ fontSize: '0.72rem', color: '#58a6ff', marginBottom: 6 }}>
        {leftThisImpulse} attempt{leftThisImpulse === 1 ? '' : 's'} left this impulse
        <span style={{ color: '#6e7681' }}>
          {' '}({leftThisTurn} left this turn)
        </span>
      </div>
      <div style={{ fontSize: '0.68rem', color: '#6e7681', marginBottom: 8 }}>
        Six attempts a turn, at most four in one impulse (D13.31/D13.32). Labs are untouched by
        this and may still be used (D13.33).
      </div>

      <div style={{ fontSize: '0.7rem', color: '#8b949e', margin: '0 0 3px' }}>
        Seeking weapon
      </div>
      <div style={{ maxHeight: 160, overflowY: 'auto', marginBottom: 8 }}>
        {targets.map(t => (
          <div key={t.name}
               onClick={() => setTarget(t.name)}
               title={t.closingOn ? `Pointed at ${t.closingOn}` : undefined}
               style={{
                 display: 'flex', gap: 6, cursor: 'pointer', padding: '2px 4px',
                 borderRadius: 3, fontSize: '0.78rem',
                 background: t.name === target ? '#1f4e79' : 'transparent',
               }}>
            <span style={{ flex: 1 }}>{t.name}</span>
            <span style={{ color: '#8b949e' }}>{t.kind.toLowerCase()}</span>
            <span style={{ color: '#8b949e' }}>{t.range} hex</span>
            <span style={{ color: t.needs >= 6 ? '#3fb950' : '#d29922', minWidth: 58,
                           textAlign: 'right' }}>
              {chanceText(t.needs)}
            </span>
            {/* D13.321, and worth a glyph: it is a whole point of the die. */}
            {t.repeat && <span style={{ color: '#3fb950' }} title="Repeat attempt: one easier (D13.321)">
              {String.fromCharCode(8722)}1
            </span>}
          </div>
        ))}
      </div>

      {/* The shuttle case, which is the point of D13.32 and reads as a bug without a word. */}
      <div style={{ fontSize: '0.68rem', color: '#6e7681', marginBottom: 6 }}>
        Shuttles are listed too: one may be a suicide shuttle or a weasel, and looking is the only
        way to know (D13.32).
      </div>

      {last && (
        <div style={{ fontSize: '0.72rem', color: '#3fb950', marginBottom: 6 }}>{last}</div>
      )}
      {error && (
        <div style={{ fontSize: '0.72rem', color: '#f85149', marginTop: 6 }}>{error}</div>
      )}

      <div style={{ display: 'flex', gap: 6, marginTop: 4 }}>
        <button disabled={!canTry} onClick={attempt}>
          {busy ? 'Scanning…' : 'Attempt'}
        </button>
        <button className="secondary" onClick={onClose}>Done</button>
      </div>
    </div>
  );
}

const panelStyle: React.CSSProperties = {
  position: 'fixed',
  width: 340,
  zIndex: 45,          // beside the pulse pad: both are time-critical
  background: '#161b22',
  border: '1px solid #58a6ff',   // blue: information, where the pulse pad's amber is a shot
  borderRadius: 6,
  padding: 10,
  boxShadow: '0 6px 24px rgba(0,0,0,0.5)',
};

const headerStyle: React.CSSProperties = {
  cursor: 'grab',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  marginBottom: 6,
};
