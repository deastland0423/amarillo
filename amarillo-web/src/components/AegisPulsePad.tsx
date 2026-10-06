import { useCallback, useEffect, useState } from 'react';
import { gameApi } from '../api/gameApi';
import type { FireCandidate } from './FireOrdersPad';
import type { ShipObject } from '../types/gameState';
import { useDraggable } from '../hooks/useDraggable';
import { weaponTitle } from './weaponTitle';

/**
 * The aegis pulse pad (D13.0): the EXTRA defensive firings, fired one at a time and resolved
 * as they happen.
 *
 * <h2>Why this is not part of the Fire Orders pad</h2>
 * That pad composes a SEALED declaration — orders are drafted, committed, and resolved together
 * once everyone has answered. Aegis is the opposite shape on purpose: D13.11 resolves aegis fire
 * immediately, so the interaction is a loop. Fire, see what died, fire again at whatever is still
 * coming. Folding that into a sealed round would mean choosing all four shots before seeing the
 * first one land, which is exactly the decision the rule hands back to the player.
 *
 * <h2>The thing a player must be told, or they will think a shot went missing</h2>
 * D13.14 makes the FIRST aegis firing coincide with the ship's ordinary volley. So a full system's
 * four firings are one volley plus THREE pulses, and a limited system's two are one volley plus
 * ONE. The pad therefore counts extras, never the allowance, and says so in as many words —
 * {@code aegisPulsesRemaining} exists as a separate DTO field for this reason, and a pad driven by
 * {@code aegisFirings} would offer a shot that does not exist.
 *
 * <h2>Automatic, because forgetting is the expensive mistake</h2>
 * The owner's call. Aegis exists to shoot down seekers already on their way, so a pad that waited
 * to be opened would be forgotten in exactly the impulse it was needed. It appears whenever there
 * is something to shoot and firings left to shoot it with, and can be dismissed for the impulse.
 */

interface Props {
  gameId: string;
  playerToken: string;
  /** The owner's own aegis ship. Everything here is owner-only; see D13.51. */
  ship: ShipObject;
  /** Bumped by the caller when the game state changes, so the target list refetches. */
  stateVersion: number;
  onClose: () => void;
}

/** A weapon this ship's aegis may fire, with the reason when it may not. */
interface PulseWeapon {
  /** The identity the server keys on — type + "-" + designator. */
  name: string;
  label: string;
  available: boolean;
  reason: string | null;
}

export default function AegisPulsePad(
  { gameId, playerToken, ship, stateVersion, onClose }: Props) {
  const drag = useDraggable({ left: 24, top: 120 });
  const [targets, setTargets] = useState<FireCandidate[] | null>(null);
  const [target, setTarget] = useState<string | null>(null);
  const [picked, setPicked] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const pulsesLeft = ship.aegisPulsesRemaining ?? 0;

  const refresh = useCallback(() => {
    gameApi.getAegisTargets(gameId, playerToken, ship.name)
      .then(rows => {
        setTargets(rows);
        // Keep the chosen target only while it is still legal — a seeker destroyed by the last
        // pulse must not stay selected and have the next pulse refused for it.
        setTarget(prev => (prev && rows.some(r => r.name === prev) ? prev : rows[0]?.name ?? null));
      })
      .catch(e => setError(e instanceof Error ? e.message : String(e)));
  }, [gameId, playerToken, ship.name]);

  useEffect(refresh, [refresh, stateVersion]);

  /**
   * Which weapons this pulse may use. Both refusals come from the server as separate fields
   * (D13.22) because they are different facts: one is the hull's wiring and permanent, the other
   * lasts the impulse. Shown rather than hidden, so a player can see WHY a gun is not offered.
   */
  const weapons: PulseWeapon[] = (ship.weapons ?? [])
    .filter(w => w.aegisControllable != null)       // absent entirely on a hull without aegis
    .map(w => {
      const inArc = targets?.find(t => t.name === target)?.weaponsInArc;
      const bears = inArc == null || inArc.includes(w.name);
      const reason = !w.functional ? 'destroyed'
        : !w.aegisControllable ? 'not on this aegis (D13.22)'
        : w.aegisBarredThisImpulse ? 'already fired outside aegis this impulse (D13.22)'
        : !bears ? 'does not bear'
        : null;
      return { name: w.name, label: weaponTitle(w), available: reason == null, reason };
    });

  const chosen = [...picked].filter(n => weapons.some(w => w.name === n && w.available));
  const canFire = target != null && chosen.length > 0 && pulsesLeft > 0 && !busy;

  /**
   * Invisible until there is something to shoot, which is what makes "automatic" bearable.
   * The caller mounts this whenever an aegis ship has firings left, so without this the pad would
   * appear in every Direct Fire impulse of every turn — and a panel that is usually noise is one a
   * player learns to dismiss without reading, which defeats the point of opening it for them.
   *
   * Returning null rather than having the caller decide keeps the eligibility question in one
   * place: this component already asks the server, and the board would need its own copy of the
   * same fetch to answer it.
   */
  if (targets == null || targets.length === 0) return null;

  function toggle(name: string) {
    setPicked(prev => {
      const next = new Set(prev);
      if (next.has(name)) next.delete(name); else next.add(name);
      return next;
    });
  }

  async function fire() {
    if (!canFire || target == null) return;
    setBusy(true);
    setError(null);
    const res = await gameApi.submitAction(gameId, playerToken, {
      type: 'AEGIS_PULSE',
      shipName: ship.name,
      targetName: target,
      weaponNames: chosen,
    });
    setBusy(false);
    if (!res.success) {
      setError(res.message ?? 'The pulse was refused');
      return;
    }
    // A fired weapon is barred from the next pulse by D13.22 anyway, so clearing the selection is
    // what the rules leave behind rather than a convenience.
    setPicked(new Set());
    refresh();
  }

  return (
    <div style={{ ...panelStyle, left: drag.position.left, top: drag.position.top }}>
      <div style={headerStyle} {...drag.handleProps} title="Drag to move">
        <span style={{ fontWeight: 700 }}>Aegis {String.fromCharCode(183)} {ship.name}</span>
        <button className="secondary" style={{ padding: '0 8px' }}
                title="Dismiss for this impulse" onClick={onClose}>
          {String.fromCharCode(10005)}
        </button>
      </div>

      <div style={{ fontSize: '0.72rem', color: '#d29922', marginBottom: 6 }}>
        {pulsesLeft} extra firing{pulsesLeft === 1 ? '' : 's'} left this impulse
      </div>
      {/* The sentence that stops "where did my fourth shot go?" — D13.14. */}
      <div style={{ fontSize: '0.68rem', color: '#6e7681', marginBottom: 8 }}>
        The first of this ship{String.fromCharCode(8217)}s {ship.aegisFirings ?? 0} aegis firings
        went with its ordinary volley (D13.14); these are the extras.
      </div>

      <>
          <div style={{ fontSize: '0.7rem', color: '#8b949e', margin: '0 0 3px' }}>Target</div>
          <div style={{ maxHeight: 132, overflowY: 'auto', marginBottom: 8 }}>
            {targets.map(t => (
              <div key={t.name}
                   onClick={() => setTarget(t.name)}
                   style={{
                     display: 'flex', gap: 6, cursor: 'pointer', padding: '2px 4px',
                     borderRadius: 3, fontSize: '0.78rem',
                     background: t.name === target ? '#2f6f4f' : 'transparent',
                   }}>
                <span style={{ flex: 1 }}>{t.name}</span>
                <span style={{ color: '#8b949e' }}>{t.kind.toLowerCase()}</span>
                <span style={{ color: '#8b949e' }}>{t.range} hex</span>
              </div>
            ))}
          </div>

          <div style={{ fontSize: '0.7rem', color: '#8b949e', margin: '0 0 3px' }}>Weapons</div>
          <div style={{ maxHeight: 176, overflowY: 'auto' }}>
            {weapons.map(w => (
              <label key={w.name}
                     title={w.reason ?? undefined}
                     style={{
                       display: 'flex', gap: 6, alignItems: 'center', padding: '1px 4px',
                       fontSize: '0.78rem', opacity: w.available ? 1 : 0.45,
                       cursor: w.available ? 'pointer' : 'default',
                     }}>
                <input type="checkbox" disabled={!w.available}
                       checked={picked.has(w.name)}
                       onChange={() => toggle(w.name)} />
                <span style={{ flex: 1 }}>{w.label}</span>
                {w.reason && <span style={{ color: '#6e7681', fontSize: '0.68rem' }}>
                  {w.reason}
                </span>}
              </label>
            ))}
          </div>
      </>

      {error && (
        <div style={{ fontSize: '0.72rem', color: '#f85149', marginTop: 6 }}>{error}</div>
      )}

      <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
        <button disabled={!canFire} onClick={fire}>
          {busy ? 'Firing…' : `Fire pulse (${chosen.length})`}
        </button>
        <button className="secondary" onClick={onClose}>Hold fire</button>
      </div>
    </div>
  );
}

const panelStyle: React.CSSProperties = {
  position: 'fixed',
  width: 320,
  zIndex: 45,          // above the SSD panel: this one is time-critical, that one is reference
  background: '#161b22',
  border: '1px solid #d29922',   // amber: a decision waiting, matching the arming vocabulary
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
