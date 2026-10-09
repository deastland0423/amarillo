import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { gameApi } from '../api/gameApi';
import { familiesOf, matchesSearch, resolveFamily, toggleRefit } from './shelfFamilies';
import { isGeneratedName, nameForNewShip } from './fleetNaming';
import type {
  CatalogShip, FleetSpec, FleetSummary, FleetValidation, FleetViolation,
} from '../api/gameApi';
import type { ShipObject } from '../types/gameState';
import SsdPanel from './SsdPanel';

interface Props {
  playerName: string;
  onLeave: () => void;
}

type View = 'list' | 'edit';

const BLANK: FleetSpec = {
  name: '', factions: [], year: 180, budget: 1000, ships: [],
};

/**
 * Where a ship's series sorts. A ship with none goes FIRST, so a mixed selection shows the
 * un-serried empires above the Romulan generations rather than stranding them below three
 * section headers they have nothing to do with.
 */
function seriesRank(ship: CatalogShip): number {
  return ship.series ? (ship.seriesOrder ?? Number.MAX_SAFE_INTEGER) : -1;
}

/** Role badges, in the order they read best on a row. */
function badgesFor(ship: CatalogShip): string[] {
  const out: string[] = [];
  if (ship.isLeader)      out.push('leader');
  if (ship.isEscort)      out.push('escort');
  if (ship.requiresEscort) out.push('needs escorts');
  if (ship.isBCH)         out.push('BCH');
  if (ship.isScout)       out.push('scout');
  return out;
}

/**
 * The roles a shelf filter can narrow to, and how each is recognised.
 *
 * Four because these are the four the fleet-construction rules actually talk about: S8.36 restricts
 * leaders, S8.315 obliges a carrier to bring escorts of its own empire, and G24.35 prices a scout
 * differently from the hull it was built on. A player assembling a legal force is looking for one of
 * these far more often than for "a cruiser".
 *
 * Every predicate reads a field the SERVER computed. None is derived here — a scout in particular is
 * not a flag at all but a count of ScoutChannel weapons (G24.0), and guessing at that client-side is
 * exactly the mistake that had me report a whole faction as missing its aegis earlier today.
 *
 * `carrier` is CAPABLE only, and that is the owner's call rather than an oversight. J4.62's CASUAL
 * carriers — 15 Federation hulls among them — have the facilities to rearm fighters belonging to
 * other ships in the fleet and fly none of their own, so a player looking for a carrier to buy does
 * not mean those. Including them would have put a frigate escort in the carrier list.
 *
 * It is also not requiresEscort, which is S8.315 fleet LEGALITY rather than J4.61 capability: a
 * casual carrier brings no escort obligation, and the two flags are kept apart in the ship data on
 * purpose.
 */
const SHELF_ROLES: Array<{ key: string; label: string; match: (s: CatalogShip) => boolean }> = [
  { key: 'leader',  label: 'Leader',  match: s => s.isLeader },
  { key: 'carrier', label: 'Carrier', match: s => s.carrierClass === 'CAPABLE' },
  { key: 'escort',  label: 'Escort',  match: s => s.isEscort },
  { key: 'scout',   label: 'Scout',   match: s => s.isScout },
];

export default function FleetBuilder({ playerName, onLeave }: Props) {
  const [view, setView]       = useState<View>('list');
  const [catalog, setCatalog] = useState<CatalogShip[]>([]);
  const [fleets, setFleets]   = useState<FleetSummary[]>([]);
  const [spec, setSpec]       = useState<FleetSpec>(BLANK);
  const [check, setCheck]     = useState<FleetValidation | null>(null);
  const [error, setError]     = useState('');
  const [busy, setBusy]       = useState(false);
  const [saved, setSaved]     = useState('');
  /**
   * The hull being inspected, or null. Held separately from the shelf row that opened it because
   * the viewer shows what the row cannot — shields, armament, bays — and that arrives from its own
   * endpoint. `viewing` is the label to show while the fetch is in flight, so the panel can open
   * immediately instead of after a round trip.
   */
  /**
   * Shelf role filters, by key. Empty shows everything.
   *
   * Several at once are OR-ed, not AND-ed: a fleet needs a leader AND escorts AND a carrier, so the
   * useful question is "show me the ships that are any of these", never "ships that are all of
   * them" — almost nothing is a scout and a leader at once, so AND would usually show an empty
   * shelf and read as a bug.
   */
  const [roleFilter, setRoleFilter] = useState<Set<string>>(new Set());
  /**
   * Free-text shelf filter. Empty shows everything.
   *
   * The grouping answers "what does this empire field"; it cannot answer "where is the CWL", and
   * with 355 hulls on the shelf that is the question a player who already knows what they want
   * arrives with. Matched against the type code, the class name and the line name, because those
   * are the three things a buyer knows a ship by — "CWL", "war cruiser" and "Lyran police" all
   * have to land somewhere.
   */
  const [search, setSearch] = useState('');
  const [viewing, setViewing]   = useState<CatalogShip | null>(null);
  const [viewShip, setViewShip] = useState<ShipObject | null>(null);
  const [viewError, setViewError] = useState('');

  // ---- loading ----------------------------------------------------------

  const refreshFleets = useCallback(async () => {
    try {
      setFleets(await gameApi.listFleets());
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not list fleets.');
    }
  }, []);

  useEffect(() => {
    refreshFleets();
  }, [refreshFleets]);

  /**
   * The catalogue is re-fetched when the date changes, because a carrier's PRICE moves with it:
   * S8.131 makes the date decide which fighters it flies and S8.11 charges for them, so a
   * Kzinti CVS is 243 points in Y170 and 315 in Y183. Quoting a stale shelf price would put the
   * builder at odds with the validator, which is the one thing the catalogue endpoint exists to
   * prevent.
   *
   * Debounced on the same 250ms as validation: the year is a text field, so typing "183" would
   * otherwise fetch three times.
   */
  const catalogTimer = useRef<number | undefined>(undefined);
  useEffect(() => {
    window.clearTimeout(catalogTimer.current);
    catalogTimer.current = window.setTimeout(() => {
      gameApi.listShips(undefined, spec.year)
        .then(setCatalog)
        .catch(e => setError(e instanceof Error ? e.message : 'Could not load the ship catalogue.'));
    }, 250);
    return () => window.clearTimeout(catalogTimer.current);
  }, [spec.year]);

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

  /**
   * Which refits the player has ticked, per hull family. Keyed "faction/baseType".
   *
   * Empty or absent means the base hull, which is what the shelf showed before any of this.
   */
  const [refitChoice, setRefitChoice] = useState<Record<string, string[]>>({});

  /** The shelf: the chosen empires, in service by the scenario date (S8.131). */

  const shelf = useMemo(() => {
    const chosen = new Set(spec.factions);
    const active = SHELF_ROLES.filter(r => roleFilter.has(r.key));
    return catalog
      .filter(s => chosen.has(s.faction))
      .filter(s => s.serviceYear <= spec.year)
      .filter(s => matchesSearch(s, search))
      // No filter chosen shows everything; several are OR-ed. The VALIDATOR remains the only
      // authority on legality — a narrowed shelf is an aid to finding a hull, never a claim that
      // what is on it makes a legal fleet.
      .filter(s => active.length === 0 || active.some(r => r.match(s)))
      // Series first where a ship has one, then lineOrder. Sorting by lineOrder alone would
      // interleave the generations, and sorting by the line CODE would be worse still:
      // alphabetically "BCH" sorts above "CA" and the freighters land between the destroyers and
      // the frigates, which is nobody's mental model of a fleet. Both orders live in
      // shiplines.json, so reordering that file reorders this screen.
      .sort((a, b) => (seriesRank(a) - seriesRank(b))
        || (a.lineOrder - b.lineOrder)
        || a.faction.localeCompare(b.faction)
        || a.type.localeCompare(b.type));
  }, [catalog, spec.factions, spec.year, roleFilter, search]);

  /**
   * The shelf in two levels: series sections, each holding line groups.
   *
   * An empire whose hulls fall into generations gets an outer section per generation — the
   * Romulans and their Eagle, Kestrel and Hawk series, where a player choosing a "Kestrel only"
   * fleet would otherwise have to pick through 38 hulls by name. A ship declaring no series lands
   * in a single unlabelled section, so every other empire renders exactly as it did before and
   * mixed selections put the un-serried hulls together.
   *
   * `shelf` is already in display order and Maps keep insertion order, so both levels come out
   * right with no second sort and no list of names to keep in step here.
   */
  const shelfBySeries = useMemo(() => {
    type LineGroup = { name: string; ships: CatalogShip[]; civilian: boolean };
    type Section   = { label: string | null; about?: string; lines: Map<string, LineGroup> };

    const sections = new Map<string, Section>();
    for (const ship of shelf) {
      const sectionKey = ship.series ?? '';
      if (!sections.has(sectionKey))
        sections.set(sectionKey, {
          label: ship.series ? (ship.seriesName || ship.series) : null,
          about: ship.seriesAbout,
          lines: new Map(),
        });
      const section = sections.get(sectionKey)!;

      const lineKey = ship.lineName || ship.line || 'Other';
      if (!section.lines.has(lineKey))
        section.lines.set(lineKey, {
          name: lineKey, ships: [], civilian: !!ship.lineCivilian,
        });
      section.lines.get(lineKey)!.ships.push(ship);
      // The ships array stays complete and in order; families are derived from it at render time
      // (see `familiesOf`) so a hull that is nobody's refit renders exactly as it always has.
    }

    // The first civilian line group WITHIN a section takes the dividing rule, never its first.
    return [...sections.entries()].map(([key, section]) => {
      const lines = [...section.lines.values()];
      const firstCivilian = lines.find(l => l.civilian);
      return {
        key,
        label: section.label,
        about: section.about,
        lines,
        ruleAbove: firstCivilian && lines[0].name !== firstCivilian.name
          ? firstCivilian.name : null,
      };
    });
  }, [shelf]);

  const lookup = useCallback(
    (faction: string | undefined, type: string) =>
      catalog.find(s => s.type === type && s.faction === (faction || spec.factions[0])),
    [catalog, spec.factions]);

  /** Ships still carrying the name the builder made up for them. */
  const generatedNames = spec.ships.filter(e => isGeneratedName(e.type, e.name)).length;
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
      // The first of a kind keeps the hull's own name; the rest are numbered. Two ships sharing a
      // name is a validation ERROR, not untidiness — a name is how an order addresses a unit — so
      // buying three F5s used to hand back an illegal fleet to be fixed by hand.
      ships: [...s.ships, {
        faction: ship.faction,
        type: ship.type,
        name: nameForNewShip(ship.type, ship.name, s.ships.map(e => e.name ?? '')),
        coiSpend: 0,
      }],
    }));
  }

  /**
   * Open the viewer on a hull without buying it.
   *
   * The shelf's problem until now was that a row's only affordance WAS buying: the cheapest way to
   * learn what a hull is was to add it and read the total. The panel opens on the click and fills
   * when the fetch lands, so an unfamiliar ship costs a look rather than a purchase.
   *
   * The fleet's year goes with the request because S8.131 makes it decide a carrier's air wing, so
   * the bays shown are the ones this fleet would actually fly.
   */
  function inspect(ship: CatalogShip) {
    setViewing(ship);
    setViewShip(null);
    setViewError('');
    gameApi.shipDetail(ship.faction, ship.type, spec.year)
      .then(setViewShip)
      .catch(e => setViewError(e instanceof Error ? e.message : String(e)));
  }

  function closeViewer() {
    setViewing(null);
    setViewShip(null);
    setViewError('');
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

  /**
   * Violations indexed by the ship they name, so a roster row can carry a marker.
   *
   * The summary list below the roster stays exactly as it was — the owner's point, and the right
   * one: "everything wrong with this fleet" is a question you want answered in one place, not
   * reassembled by scanning rows. This is a LOCATOR for that list, not a replacement. A fleet of
   * fourteen ships told "the carrier needs two escorts of its own empire" still leaves you hunting
   * for which row is the carrier.
   *
   * `shipName` has been on the wire since the validator was built and nothing has ever read it.
   * Violations about the fleet as a whole — over budget, no flagship — carry no ship name and
   * belong only in the list, so they fall out of this map on their own.
   */
  const violationsByShip = useMemo(() => {
    const out = new Map<string, FleetViolation[]>();
    for (const v of check?.violations ?? []) {
      if (!v.shipName) continue;
      const at = out.get(v.shipName);
      if (at) at.push(v); else out.set(v.shipName, [v]);
    }
    return out;
  }, [check]);

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

          {/* Search. Above the role chips because it is the coarser filter of the two, and the
              two compose: "Carrier" plus "kzinti" is a reasonable thing to ask for. */}
          {spec.factions.length > 0 && (
            <div className="fb-shelf-search">
              <input value={search} onChange={e => setSearch(e.target.value)}
                     placeholder="Find a hull — CWL, war cruiser, carrier…"
                     aria-label="Find a hull by type, class or line" />
              {search !== '' && (
                <button className="fb-search-clear" title="Clear the search"
                        aria-label="Clear the search"
                        onClick={() => setSearch('')}>×</button>
              )}
            </div>
          )}

          {/* Role filters. Inside the shelf, so the chosen empires already scope them — "Escort"
              here means an escort this fleet could actually field, not every escort in the game.
              The COUNT is the useful part: a chip reading 0 says the empire has none, which is a
              real answer and one the shelf could not give before. The Lyrans have no escorts and no
              carrier at all, and that is invisible while scrolling a list of what they do have. */}
          {spec.factions.length > 0 && (
            <div className="fb-faction-chips fb-role-chips">
              {SHELF_ROLES.map(role => {
                const available = catalog.filter(s => spec.factions.includes(s.faction)
                    && s.serviceYear <= spec.year && role.match(s)).length;
                const on = roleFilter.has(role.key);
                return (
                  <button key={role.key}
                          className={on ? 'fb-chip fb-chip-on' : 'fb-chip'}
                          disabled={available === 0 && !on}
                          title={available === 0
                            ? `No ${role.label.toLowerCase()} available to these empires by Y${spec.year}`
                            : `Show only ${role.label.toLowerCase()}s (and any other role selected)`}
                          onClick={() => setRoleFilter(prev => {
                            const next = new Set(prev);
                            if (next.has(role.key)) next.delete(role.key); else next.add(role.key);
                            return next;
                          })}>
                    {role.label} <span className="fb-hint">{available}</span>
                  </button>
                );
              })}
              {roleFilter.size > 0 && (
                <button className="fb-chip" title="Show every hull again"
                        onClick={() => setRoleFilter(new Set())}>
                  Clear
                </button>
              )}
            </div>
          )}

          {spec.factions.length === 0 && (
            <p className="subtitle">Choose an empire to see what it can field.</p>
          )}

          {/* A filter that matches nothing must say so. An empty shelf under a chosen empire is
              otherwise indistinguishable from a load failure, and the cause — a search, a role
              chip, or a date too early for anything — is the one thing the player needs told. */}
          {spec.factions.length > 0 && shelfBySeries.length === 0 && (
            <p className="subtitle">
              {search.trim() !== ''
                ? `Nothing matches “${search.trim()}” in service by Y${spec.year}.`
                : `These empires field nothing by Y${spec.year} under the chosen roles.`}
            </p>
          )}

          {shelfBySeries.map(section => (
            <div key={section.key || 'no-series'} className="fb-series-section">
              {/* Only a ship that declares a series gets a header; everything else renders
                  flush, exactly as the shelf did before series existed. */}
              {section.label && (
                <div className="fb-series-header" title={section.about}>{section.label}</div>
              )}
              {section.lines.map(group => (
                <div key={group.name}
                     className={'fb-line-group'
                       + (group.name === section.ruleAbove ? ' fb-line-group-civilian' : '')}>
                  <div className="fb-line-header">{group.name}</div>
                  {/* The row is a GRID, not a button.
                      It cannot be a button: the refit chips sit on it now, and a button inside a
                      button is invalid HTML — the same constraint that used to exile the chips to
                      a full-width line below. So the row carries three sibling controls instead:
                      each refit chip, the PRICE (which is the buy control), and inspect. */}
                  {familiesOf(group.ships).map(fam => {
                    const famKey = fam.base.faction + '/' + fam.base.type;
                    const chosen = refitChoice[famKey] ?? [];
                    // What this row currently buys. Null means the ticked combination was never
                    // fielded and so has no catalogue row — the Add button is disabled rather than
                    // quietly buying something else.
                    const ship = resolveFamily(fam, chosen) ?? fam.base;
                    const unavailable = resolveFamily(fam, chosen) == null;
                    const offers = (fam.base.refitsAvailable ?? [])
                      // A refit cannot be fielded before it exists (S3.24).
                      .filter(o => o.year <= spec.year);
                    return (
                    <div key={famKey} className="fb-shelf-item">
                      {/* The type code, which RENAMES itself as refits are ticked: a CA with the
                          + refit reads CA+, because resolveFamily returned that variant. */}
                      <span className="fb-shelf-type">{ship.type}</span>
                      {/* The CLASS, not the ship's own name. A buyer scanning the shelf wants to
                          know what a hull is — and typeName differs from the group header on 305 of
                          355 hulls, so a CC and a CVB under "Heavy Cruiser" finally read as a
                          command cruiser and a strike carrier. The ship's name is still there to
                          edit in the fleet list once it is bought, which is where it matters.
                          This is the column that TRUNCATES when the chips need the room: it is the
                          only thing on the row repeated in the inspect panel and the tooltip. */}
                      <span className="fb-shelf-name"
                            title={ship.typeName || ship.name}>{ship.typeName || ship.name}</span>
                      {/* The refits, inline. Each says what it costs, because that IS the
                          decision — "is the power pack worth 15 points?" — and separate shelf rows
                          asked a player to work it out by subtraction. Wraps rather than crushes:
                          the worst hulls (Lyran DW/DN/CW/BC, Klingon D6/D7) offer three. */}
                      {/* Role badges sit with the class name on the LEFT — they describe what the
                          hull IS, which is the same question typeName answers. The refit buttons
                          are a CONTROL and get the centre to themselves; interleaving the two put
                          "LEADER" between two things you could click. */}
                      <span className="fb-shelf-badges">
                        {badgesFor(ship).map(b => <span key={b} className="badge">{b}</span>)}
                      </span>
                      <span className="fb-shelf-refits">
                        {offers.map(o => {
                          const on = chosen.includes(o.code);
                          // Ticking a refit pulls in what it requires — the Klingon K refit
                          // arrives on top of B, so offering it alone would offer a ship that does
                          // not exist. See shelfFamilies.toggleRefit.
                          const withNeeds = toggleRefit(chosen, o.code, o.requires);
                          const other = resolveFamily(fam, withNeeds);
                          const buildable = other != null;
                          /**
                           * What this refit costs ON THIS HULL, given everything else ticked —
                           * the difference between the two resolved variants, not the flat `bpv`
                           * the data declares.
                           *
                           * The declared figure is 0 for every convertPower refit, so the AWR
                           * refit advertised "+0" and then charged 2. And it is not a constant: a
                           * conversion prices per reactor, so AWR is +2 on a bare CA and +4 once
                           * the + refit has installed two more APRs for it to convert. Only the
                           * resolved pair knows that.
                           *
                           * Ticked or not, the number means the same thing — the price of HAVING
                           * this refit — so it does not jump when clicked. Falls back to the
                           * declared cost only when the other variant does not exist, and the
                           * chip is disabled in that case anyway.
                           */
                          const delta = other == null ? o.bpv
                            : on ? ship.cost - other.cost : other.cost - ship.cost;
                          const label = o.label || o.name.replace(/ refit$/, '');
                          /**
                           * Refits this click would drag in that are not ticked yet.
                           *
                           * The price above is what the CLICK costs, which is the honest number —
                           * but when a refit requires another, that total silently includes the
                           * other one's cost while the other one sits on the same row showing its
                           * own price. The Federation CC reads "Plus Refit +10, AWR +12" and the
                           * two do not add up to 22, because the 12 already contains the 10. The
                           * tooltip is where that gets said; shortening the number instead would
                           * misquote what pressing the button charges.
                           */
                          const pulls = on ? []
                            : (o.requires ?? []).filter(c => !chosen.includes(c));
                          const pulledNames = pulls
                            .map(c => offers.find(x => x.code === c))
                            .map((x, i) => x
                              ? (x.label || x.name.replace(/ refit$/, ''))
                              : pulls[i]);
                          return (
                            <button key={o.code}
                                    className={'fb-refit-chip' + (on ? ' fb-refit-chip-on' : '')}
                                    disabled={!buildable && !on}
                                    aria-pressed={on}
                                    title={buildable || on
                                      ? `${o.name} — available Y${o.year}, `
                                        + `${delta >= 0 ? '+' : ''}${delta} BPV`
                                        + (pulledNames.length > 0
                                          ? ` (includes the ${pulledNames.join(' and ')} it requires)`
                                          : '')
                                      : `${o.name} was never fielded in this combination`}
                                    onClick={() => setRefitChoice(prev =>
                                      ({ ...prev, [famKey]: withNeeds }))}>
                              {label}
                              <span className="fb-refit-cost">
                                {delta >= 0 ? '+' : ''}{delta}
                              </span>
                            </button>
                          );
                        })}
                      </span>
                      {/* The PRICE IS THE BUY BUTTON. The row can no longer be one big target —
                          the chips are buttons and cannot nest — so the action goes on the number
                          the eye lands on after reading them, and it quotes what it will charge. */}
                      <button className="fb-shelf-buy"
                              disabled={unavailable}
                              title={unavailable
                                ? 'That combination of refits was never fielded, so there is no '
                                  + 'hull to buy yet (S3.24 would permit it)'
                                : `Add the ${ship.type} — ${ship.cost} points`}
                              aria-label={`Add the ${ship.type} for ${ship.cost} points`}
                              onClick={() => addShip(ship)}>
                        {ship.cost}
                        {ship.fighterBpv > 0 && (
                          <span className="fb-hint"> ({ship.bpv}+{ship.fighterBpv})</span>
                        )}
                      </button>
                      <button className="fb-shelf-inspect"
                              title={`View the ${ship.type} — arcs, shields and armament`}
                              aria-label={`View the ${ship.type}`}
                              onClick={() => inspect(ship)}>i</button>
                    </div>
                  );})}
                </div>
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
            // What the validator says about THIS ship. Worst severity wins the marker's colour:
            // a row with an error and an advisory is an error row.
            const faults = violationsByShip.get(entry.name ?? '') ?? [];
            const hasError = faults.some(v => v.severity === 'ERROR');
            // Still carrying the name the builder made up for it. Not a fault — the fleet is
            // legal — so this is the quietest mark on the row, and it yields to a real one.
            const unnamed = isGeneratedName(entry.type, entry.name);
            return (
              <div key={i}
                   className={'fb-fleet-ship'
                     + (faults.length === 0 ? (unnamed ? ' fb-fleet-ship-unnamed' : '')
                       : hasError ? ' fb-fleet-ship-error' : ' fb-fleet-ship-warn')}>
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
                {/* The locator. Its tooltip repeats the rule and message so the row answers the
                    question on its own, but the list below remains where you read them all. */}
                <span className={faults.length === 0 ? 'fb-ship-fault-slot'
                        : hasError ? 'fb-ship-fault fb-ship-fault-error' : 'fb-ship-fault'}
                      title={faults.length === 0 ? undefined
                        : faults.map(v => (v.rule ? v.rule + ': ' : '') + v.message).join('\n')}>
                  {faults.length === 0 ? '' : hasError ? '✗' : '⚠'}
                </span>
                <button className="fb-remove" title="Remove" onClick={() => removeShip(i)}>×</button>
              </div>
            );
          })}

          {/* How many ships are still carrying a name the builder invented.
              Deliberately NOT a violation: the fleet is legal, and the validator's list is for
              fleet-construction rules. Mixing a suggestion about names into it would make that
              list a mixed bag of "this is illegal" and "you might prefer". */}
          {generatedNames > 0 && (
            <p className="fb-unnamed-hint">
              {generatedNames === 1
                ? '1 ship has a generated name.'
                : `${generatedNames} ships have generated names.`}
              {' '}Click a name to change it.
            </p>
          )}

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

      {/* --- the ship viewer ---
          SsdPanel is the battle map's own panel, reused rather than reimplemented: it draws the
          arc diagram, the shield ring and the weapon list already, and a second copy would drift
          from it. It drags itself and sits above the builder, so it needs no layout here.
          `contacts` is empty because there is no battle to plot — the diagram then shows the
          hull's own arcs and nothing else, which is exactly the question a buyer is asking. */}
      {viewing && viewShip && (
        <SsdPanel ship={viewShip} isMine contacts={[]} openAt="centre"
                  onClose={closeViewer} />
      )}
      {viewing && !viewShip && (
        <div className="fb-viewer-pending">
          {viewError
            ? `Could not load the ${viewing.type}: ${viewError}`
            : `Loading the ${viewing.type}…`}
          <button className="fb-btn" onClick={closeViewer}>Close</button>
        </div>
      )}
    </div>
  );
}
