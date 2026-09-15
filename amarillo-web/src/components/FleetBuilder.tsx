import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { gameApi } from '../api/gameApi';
import type {
  CatalogShip, FleetSpec, FleetSummary, FleetValidation, FleetViolation,
} from '../api/gameApi';

interface Props {
  playerName: string;
  onLeave: () => void;
}

type View = 'list' | 'edit';

const BLANK: FleetSpec = {
  name: '', factions: [], year: 180, budget: 1000, ships: [],
};

/** Role badges, in the order they read best on a row. */
function badgesFor(ship: CatalogShip): string[] {
  const out: string[] = [];
  if (ship.isLeader)      out.push('leader');
  if (ship.isEscort)      out.push('escort');
  if (ship.isTrueCarrier) out.push('carrier');
  if (ship.isBCH)         out.push('BCH');
  if (ship.isScout)       out.push('scout');
  return out;
}

export default function FleetBuilder({ playerName, onLeave }: Props) {
  const [view, setView]       = useState<View>('list');
  const [catalog, setCatalog] = useState<CatalogShip[]>([]);
  const [fleets, setFleets]   = useState<FleetSummary[]>([]);
  const [spec, setSpec]       = useState<FleetSpec>(BLANK);
  const [check, setCheck]     = useState<FleetValidation | null>(null);
  const [error, setError]     = useState('');
  const [busy, setBusy]       = useState(false);
  const [saved, setSaved]     = useState('');

  // ---- loading ----------------------------------------------------------

  const refreshFleets = useCallback(async () => {
    try {
      setFleets(await gameApi.listFleets());
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not list fleets.');
    }
  }, []);

  useEffect(() => {
    gameApi.listShips()
      .then(setCatalog)
      .catch(e => setError(e instanceof Error ? e.message : 'Could not load the ship catalogue.'));
    refreshFleets();
  }, [refreshFleets]);

  // ---- validation -------------------------------------------------------
  // The rules live on the server. Every change asks again rather than the browser
  // guessing, so the panel can never disagree with what the lobby will decide.

  const validateTimer = useRef<number | undefined>(undefined);
  useEffect(() => {
    if (view !== 'edit') return;
    if (spec.ships.length === 0) { setCheck(null); return; }

    window.clearTimeout(validateTimer.current);
    validateTimer.current = window.setTimeout(() => {
      gameApi.validateFleet(spec)
        .then(setCheck)
        .catch(e => setError(e instanceof Error ? e.message : 'Could not validate.'));
    }, 250);
    return () => window.clearTimeout(validateTimer.current);
  }, [spec, view]);

  // ---- derived ----------------------------------------------------------

  const allFactions = useMemo(
    () => [...new Set(catalog.map(s => s.faction))].sort(),
    [catalog]);

  /** The shelf: the chosen empires, in service by the scenario date (S8.131). */
  const shelf = useMemo(() => {
    const chosen = new Set(spec.factions);
    return catalog
      .filter(s => chosen.has(s.faction))
      .filter(s => s.serviceYear <= spec.year)
      .sort((a, b) => a.line.localeCompare(b.line)
        || a.faction.localeCompare(b.faction)
        || a.type.localeCompare(b.type));
  }, [catalog, spec.factions, spec.year]);

  /** Grouped by line, which is what makes a hundred hulls legible. */
  const shelfByLine = useMemo(() => {
    const groups = new Map<string, CatalogShip[]>();
    for (const ship of shelf) {
      const key = ship.lineName || ship.line || 'Other';
      if (!groups.has(key)) groups.set(key, []);
      groups.get(key)!.push(ship);
    }
    return [...groups.entries()];
  }, [shelf]);

  const lookup = useCallback(
    (faction: string | undefined, type: string) =>
      catalog.find(s => s.type === type && s.faction === (faction || spec.factions[0])),
    [catalog, spec.factions]);

  const spent = check?.totalCost ?? 0;
  const remaining = spec.budget - spent;

  // ---- editing ----------------------------------------------------------

  function startNew() {
    setSpec({ ...BLANK, author: playerName, name: '' });
    setCheck(null);
    setSaved('');
    setView('edit');
  }

  async function openFleet(id: string) {
    setBusy(true); setError(''); setSaved('');
    try {
      const { fleet, validation } = await gameApi.getFleet(id);
      setSpec(fleet);
      setCheck(validation);
      setView('edit');
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not open that fleet.');
    } finally {
      setBusy(false);
    }
  }

  function toggleFaction(faction: string) {
    setSpec(s => ({
      ...s,
      factions: s.factions.includes(faction)
        ? s.factions.filter(f => f !== faction)
        : [...s.factions, faction],
    }));
  }

  function addShip(ship: CatalogShip) {
    setSpec(s => ({
      ...s,
      ships: [...s.ships, { faction: ship.faction, type: ship.type, name: ship.name, coiSpend: 0 }],
    }));
  }

  function removeShip(index: number) {
    setSpec(s => {
      const ships = s.ships.filter((_, i) => i !== index);
      const gone = s.ships[index];
      // A removed flagship leaves the post vacant rather than silently moving it.
      const flagship = s.flagship === gone.name ? undefined : s.flagship;
      return { ...s, ships, flagship };
    });
  }

  function renameShip(index: number, name: string) {
    setSpec(s => {
      const wasFlagship = s.flagship === s.ships[index].name;
      const ships = s.ships.map((sh, i) => (i === index ? { ...sh, name } : sh));
      return { ...s, ships, flagship: wasFlagship ? name : s.flagship };
    });
  }

  async function save() {
    if (!spec.name.trim()) { setError('Give the fleet a name first.'); return; }
    setBusy(true); setError(''); setSaved('');
    try {
      const res = await gameApi.saveFleet({ ...spec, author: spec.author || playerName });
      setSpec(s => ({ ...s, id: res.id }));
      setCheck(res.validation);
      setSaved('Saved.');
      refreshFleets();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save.');
    } finally {
      setBusy(false);
    }
  }

  async function remove(id: string) {
    setBusy(true); setError('');
    try {
      await gameApi.deleteFleet(id);
      refreshFleets();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not delete.');
    } finally {
      setBusy(false);
    }
  }

  // ---- rendering --------------------------------------------------------

  function violationRow(v: FleetViolation, i: number) {
    const isError = v.severity === 'ERROR';
    return (
      <li key={i} className={isError ? 'fb-violation fb-violation-error' : 'fb-violation'}>
        <span className="fb-violation-mark">{isError ? '✗' : '⚠'}</span>
        {v.rule && <span className="fb-violation-rule">{v.rule}</span>}
        <span className="fb-violation-text">{v.message}</span>
      </li>
    );
  }

  if (view === 'list') {
    return (
      <div className="fb">
        <div className="fb-topbar">
          <h1>Fleets</h1>
          <div className="fb-topbar-actions">
            <button className="fb-btn fb-btn-primary" onClick={startNew}>New fleet</button>
            <button className="fb-btn" onClick={onLeave}>Back</button>
          </div>
        </div>

        {error && <p className="error">{error}</p>}

        {fleets.length === 0 && (
          <p className="subtitle">
            No fleets yet. Build one ahead of time and it will be waiting when you start a battle.
          </p>
        )}

        <div className="fb-fleet-list">
          {fleets.map(f => (
            <div key={f.id} className="fb-fleet-row">
              <div className="fb-fleet-main">
                <span className="fb-fleet-name">{f.name || f.id}</span>
                <span className={f.legal ? 'fb-legal' : 'fb-illegal'}>
                  {f.legal ? 'legal' : 'illegal'}
                </span>
              </div>
              <div className="fb-fleet-meta">
                <span>{f.factions.join(' + ') || '—'}</span>
                <span>Y{f.year}</span>
                <span>{f.totalCost} / {f.budget} pts</span>
                <span>{f.shipCount} ships</span>
                {f.author && <span>by {f.author}</span>}
              </div>
              <div className="fb-fleet-actions">
                <button className="fb-btn" disabled={busy} onClick={() => openFleet(f.id)}>Open</button>
                <button className="fb-btn fb-btn-danger" disabled={busy} onClick={() => remove(f.id)}>Delete</button>
              </div>
            </div>
          ))}
        </div>
      </div>
    );
  }

  // ---- the builder ------------------------------------------------------

  return (
    <div className="fb">
      <div className="fb-topbar">
        <h1>{spec.name || 'New fleet'}</h1>
        <div className="fb-topbar-actions">
          {saved && <span className="fb-saved">{saved}</span>}
          <button className="fb-btn fb-btn-primary" disabled={busy} onClick={save}>Save</button>
          <button className="fb-btn" onClick={() => { setView('list'); refreshFleets(); }}>Back</button>
        </div>
      </div>

      {error && <p className="error">{error}</p>}

      {/* Conditions. Terrain and map size are agreed per battle (S8.15, S8.135),
          so they are not here — a fleet outlives the map it was built for. */}
      <div className="fb-conditions">
        <label className="fb-field">
          <span>Fleet name</span>
          <input value={spec.name} placeholder="Border patrol"
                 onChange={e => setSpec(s => ({ ...s, name: e.target.value }))} />
        </label>
        <label className="fb-field fb-field-narrow">
          <span>Year</span>
          <input type="number" value={spec.year}
                 onChange={e => setSpec(s => ({ ...s, year: Number(e.target.value) || 0 }))} />
        </label>
        <label className="fb-field fb-field-narrow">
          <span>Budget</span>
          <input type="number" value={spec.budget}
                 onChange={e => setSpec(s => ({ ...s, budget: Number(e.target.value) || 0 }))} />
        </label>
      </div>

      <div className="fb-field">
        <span>Empires <span className="fb-hint">(more than one for an allied force — S8.6)</span></span>
        <div className="fb-faction-chips">
          {allFactions.map(f => (
            <button key={f}
                    className={spec.factions.includes(f) ? 'fb-chip fb-chip-on' : 'fb-chip'}
                    onClick={() => toggleFaction(f)}>
              {f}
            </button>
          ))}
        </div>
      </div>

      <div className="fb-shipyard">
        {/* --- the shelf --- */}
        <div className="fb-panel">
          <div className="fb-panel-title">
            Shipyard
            {spec.factions.length > 0 && <span className="fb-hint"> · in service by Y{spec.year}</span>}
          </div>

          {spec.factions.length === 0 && (
            <p className="subtitle">Choose an empire to see what it can field.</p>
          )}

          {shelfByLine.map(([lineName, ships]) => (
            <div key={lineName} className="fb-line-group">
              <div className="fb-line-header">{lineName}</div>
              {ships.map(ship => (
                <button key={ship.faction + ship.type} className="fb-shelf-row"
                        onClick={() => addShip(ship)}>
                  <span className="fb-shelf-type">{ship.type}</span>
                  <span className="fb-shelf-name">{ship.name}</span>
                  <span className="fb-shelf-badges">
                    {badgesFor(ship).map(b => <span key={b} className="badge">{b}</span>)}
                  </span>
                  <span className="fb-shelf-cost">
                    {ship.cost}
                    {ship.fighterBpv > 0 && (
                      <span className="fb-hint"> ({ship.bpv}+{ship.fighterBpv})</span>
                    )}
                  </span>
                </button>
              ))}
            </div>
          ))}
        </div>

        {/* --- the force --- */}
        <div className="fb-panel">
          <div className="fb-panel-title">Your fleet</div>

          {spec.ships.length === 0 && (
            <p className="subtitle">Nothing bought yet. Click a ship to add it.</p>
          )}

          {spec.ships.map((entry, i) => {
            const ship = lookup(entry.faction, entry.type);
            const isFlagship = spec.flagship === entry.name;
            const canCommand = (ship?.commandRating ?? 0) > 0;
            return (
              <div key={i} className="fb-fleet-ship">
                <button
                  className={isFlagship ? 'fb-flag fb-flag-on' : 'fb-flag'}
                  title={canCommand
                    ? 'Make this the flagship'
                    : 'This ship has no command rating and cannot lead a fleet (S8.21)'}
                  disabled={!canCommand}
                  onClick={() => setSpec(s => ({ ...s, flagship: entry.name }))}>★</button>
                <span className="fb-shelf-type">{entry.type}</span>
                <input className="fb-ship-name" value={entry.name ?? ''}
                       onChange={e => renameShip(i, e.target.value)} />
                <span className="fb-shelf-cost">{ship?.cost ?? '?'}</span>
                <button className="fb-remove" title="Remove" onClick={() => removeShip(i)}>×</button>
              </div>
            );
          })}

          {/* --- the budget --- */}
          <div className="fb-budget">
            <div className="fb-budget-row">
              <span>Spent</span>
              <span className={remaining < 0 ? 'fb-over' : ''}>{spent} / {spec.budget}</span>
            </div>
            <div className="fb-budget-track">
              <div className="fb-budget-fill"
                   style={{
                     width: `${Math.min(100, spec.budget > 0 ? (spent / spec.budget) * 100 : 0)}%`,
                     background: remaining < 0 ? '#f85149' : '#f0c040',
                   }} />
            </div>
            <div className="fb-budget-row fb-hint">
              <span>{spec.ships.length} ships</span>
              <span>{remaining >= 0 ? `${remaining} left` : `${-remaining} over`}</span>
            </div>
          </div>

          {/* --- what is wrong with it --- */}
          {check && (
            <div className="fb-violations">
              {check.violations.length === 0 && (
                <p className="fb-legal">✓ A legal battle force.</p>
              )}
              <ul>{check.violations.map(violationRow)}</ul>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
