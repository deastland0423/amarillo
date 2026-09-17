import { useCallback, useEffect, useMemo, useState } from 'react';
import { gameApi } from '../api/gameApi';
import type { DeploymentState, LobbyTerrain, Placement } from '../api/gameApi';
import type { MapObject } from '../types/gameState';
import HexGrid from './HexGrid';
import { FacingPicker } from './FacingPicker';

interface Props {
  gameId:      string;
  playerToken: string;
  faction:     string;
  terrain:     LobbyTerrain[];
  mapCols:     number;
  mapRows:     number;
  /** Nudges a refetch when the lobby says something changed. */
  revision:    number;
  /** Who else is still setting up, so the Done button can say so. */
  playersDone: number;
  playerCount: number;
  waitingOn:   string[];
}

/** A–F as the server writes them, and the internal facing each maps to. */
const HEADINGS: Record<string, number> = { A: 1, B: 5, C: 9, D: 13, E: 17, F: 21 };
const FACING_TO_LETTER: Record<number, string> = { 1: 'A', 5: 'B', 9: 'C', 13: 'D', 17: 'E', 21: 'F' };

export default function DeploymentPanel({
  gameId, playerToken, faction, terrain, mapCols, mapRows, revision,
  playersDone, playerCount, waitingOn,
}: Props) {
  const [state,    setState]    = useState<DeploymentState | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [error,    setError]    = useState('');
  const [busy,     setBusy]     = useState(false);

  const refresh = useCallback(async () => {
    try {
      setState(await gameApi.getDeployment(gameId, playerToken));
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load your setup.');
    }
  }, [gameId, playerToken]);

  useEffect(() => { refresh(); }, [refresh, revision]);

  const placed = useMemo(() => {
    const byShip = new Map<string, Placement>();
    for (const p of state?.placements ?? []) byShip.set(p.shipName, p);
    return byShip;
  }, [state]);

  /** Send the whole setup; the server replaces what it had, so moving is just placing again. */
  async function send(next: Placement[]) {
    setBusy(true); setError('');
    try {
      await gameApi.submitDeployment(gameId, playerToken, next);
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'That placement was refused.');
      await refresh();
    } finally {
      setBusy(false);
    }
  }

  function handleHexClick(col: number, row: number) {
    if (!selected || state?.done) return;
    const hex = `${String(col).padStart(2, '0')}${String(row).padStart(2, '0')}`;
    const existing = placed.get(selected);
    // Face the middle of the map by default; the player turns it afterwards if they like.
    const heading = existing?.heading ?? (col <= mapCols / 2 ? 'C' : 'F');

    const next = [...placed.values()].filter(p => p.shipName !== selected);
    next.push({ shipName: selected, hex, heading, speed: existing?.speed ?? 16 });
    send(next);
  }

  function setHeading(shipName: string, facing: number) {
    const letter = FACING_TO_LETTER[facing];
    const current = placed.get(shipName);
    if (!letter || !current) return;
    const next = [...placed.values()].map(p =>
      p.shipName === shipName ? { ...p, heading: letter } : p);
    send(next);
  }

  function takeBack(shipName: string) {
    send([...placed.values()].filter(p => p.shipName !== shipName));
    if (selected === shipName) setSelected(null);
  }

  async function autoArrange() {
    setBusy(true); setError('');
    try {
      await gameApi.autoArrangeDeployment(gameId, playerToken);
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not lay the fleet out.');
    } finally {
      setBusy(false);
    }
  }

  async function toggleDone() {
    setBusy(true); setError('');
    try {
      await gameApi.setDeploymentDone(gameId, playerToken, !state?.done);
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not change that.');
    } finally {
      setBusy(false);
    }
  }

  // ---- the map ----

  const mapObjects: MapObject[] = useMemo(() => {
    const objects: MapObject[] = terrain.map(t => ({
      type: 'TERRAIN',
      name: t.name ?? `${t.terrainType}-${t.hex}`,
      location: `<${Number(t.hex.slice(0, 2))}|${Number(t.hex.slice(2, 4))}>`,
      terrainType: t.terrainType as 'ASTEROID' | 'PLANET' | 'GAS_GIANT',
      radius: t.radius,
      rings: t.rings?.length ? t.rings : undefined,
    } as MapObject));

    // Only the fields the map actually draws: it reads name, location, facing and faction,
    // and guards the rest. A whole ShipObject would be a fiction here — none of it exists
    // until the battle is built.
    for (const p of placed.values()) {
      objects.push({
        type: 'SHIP',
        name: p.shipName,
        location: `<${Number(p.hex.slice(0, 2))}|${Number(p.hex.slice(2, 4))}>`,
        facing: HEADINGS[p.heading] ?? 1,
        faction,
      } as unknown as MapObject);
    }
    return objects;
  }, [terrain, placed, faction]);

  const zones = useMemo(() => {
    const hexes = state?.zone?.hexes ?? [];
    return hexes.length > 0
      ? [{ hexes, color: 'rgba(88, 166, 255, 0.16)', label: 'Your ground' }]
      : [];
  }, [state]);

  if (!state?.required) return null;

  const selectedPlacement = selected ? placed.get(selected) : undefined;

  return (
    <div className="card deploy" style={{ width: '100%', maxWidth: 960 }}>
      <div className="deploy-head">
        <h3 style={{ margin: 0 }}>Set up your fleet</h3>
        <span className="fb-hint">{placed.size} of {state.ships.length} placed</span>
      </div>

      {/* One line saying what to do now, rather than leaving it to be inferred. */}
      <p className={selected && !state.done ? 'deploy-say deploy-say-act' : 'deploy-say'}>
        {state.done
          ? waitingOn.length > 0
            ? <>Ready. Waiting for <strong>{waitingOn.join(', ')}</strong> — you can still change your mind.</>
            : <>Everyone is ready. The battle is about to begin.</>
          : selected
            ? <>Click a hex to put <strong>{selected}</strong> down.</>
            : placed.size < state.ships.length
              ? <>Pick a ship, then click a hex inside your ground — {state.zone?.describe ?? 'anywhere on the map'}.</>
              : <>Every ship is down. Turn any of them, or press Done.</>}
      </p>

      {error && <p className="error">{error}</p>}

      <div className="deploy-body">
        <div className="deploy-tray">
          {/* One list, not two: every ship, with where it stands if it stands anywhere. */}
          {state.ships.map(ship => {
            const at = placed.get(ship);
            return (
              <div key={ship} className="deploy-placed">
                <button className={selected === ship ? 'deploy-ship deploy-ship-on' : 'deploy-ship'}
                        disabled={state.done}
                        onClick={() => setSelected(ship)}>
                  <span className="deploy-ship-name">{ship}</span>
                  <span className="fb-hint">
                    {at ? `${at.hex} · facing ${at.heading}` : 'not placed'}
                  </span>
                </button>
                {at && (
                  <button className="fb-remove" title="Take back off the map"
                          disabled={state.done}
                          onClick={() => takeBack(ship)}>×</button>
                )}
              </div>
            );
          })}

          {selectedPlacement && !state.done && (
            <div className="deploy-facing">
              <div className="fb-panel-title">Which way {selected} faces</div>
              <FacingPicker
                value={HEADINGS[selectedPlacement.heading] ?? 1}
                onChange={(f: number) => setHeading(selectedPlacement.shipName, f)}
              />
            </div>
          )}

          <div className="button-row" style={{ marginTop: '0.75rem' }}>
            <button className="fb-btn" disabled={busy || state.done} onClick={autoArrange}>
              Auto-arrange
            </button>
            {(() => {
              // Same idiom as the turn-phase Ready button: red and pulsing once you are the
              // only one everyone is waiting for.
              const isLastHoldout = !state.done && playerCount > 1
                && playersDone === playerCount - 1;
              const label = state.done
                ? 'Not yet'
                : isLastHoldout
                  ? `⚠ Done — ${playersDone}/${playerCount} waiting`
                  : playersDone > 0
                    ? `Done (${playersDone}/${playerCount})`
                    : 'Done';
              return (
                <button
                  className={state.done ? 'secondary' : isLastHoldout ? 'btn-last-holdout' : ''}
                  disabled={busy || (!state.complete && !state.done)}
                  title={state.complete ? '' : 'Every ship must be set down first'}
                  onClick={toggleDone}>
                  {label}
                </button>
              );
            })()}
          </div>
        </div>

        <div className="deploy-map">
          <HexGrid
            mapCols={mapCols}
            mapRows={mapRows}
            mapObjects={mapObjects}
            myShips={state.ships}
            selectedName={selected}
            zones={zones}
            pickingHex={!!selected && !state.done}
            onHexClick={handleHexClick}
            onSelect={obj => { if (obj?.type === 'SHIP' && !state.done) setSelected(obj.name); }}
          />
        </div>
      </div>
    </div>
  );
}
