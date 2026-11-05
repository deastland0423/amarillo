import { useState } from 'react';
import type { CoiFighterLoadout, CoiFighterRail, CoiShipData } from '../api/gameApi';
import {
  bandDefault, differsFromBand, differsFromIssue, dronesForRail, fighterLoadoutCost, loadOf,
  modulesForRail, railRefusal, railsDifferingFromBand, resetFighter, setBandRail, setRail,
  typeBands,
} from './fighterLoadout';
import type { DroneCatalogue, FighterBand, LoadoutOverrides } from './fighterLoadout';
import { DronePayloadEditor } from './DronePayloadEditor';
import { formatDroneName, isModified, parseDroneName } from './droneComposition';

/**
 * Which drones each fighter flies (FD10.646, J4.23).
 *
 * <h2>Why a surface exists at all</h2>
 * FD10.646 settles what a fighter is ISSUED with, and the answer is an ordinary drone even on a
 * special rail: those rails "do not form an exception allowing additional limited availability
 * drones to be purchased". So every special drone a fighter carries is a purchase, and this is
 * where it is made. On a Klingon carrier it is the only place one can be made at all — the
 * conversion strips every drone rack off the hull, so the squadron is the whole of that ship's
 * drone capacity.
 *
 * <h2>Why bands by TYPE and not tabs by squadron</h2>
 * A carrier has one to four fighter types (one hull in the game has four) and at most two
 * squadrons, and J4.461 cuts squadrons at twelve slots in EW-first order — so the squadron
 * boundary falls ACROSS type rather than along it. A Kzinti CVA's six assault fighters are all
 * in its second squadron while its superiority fighters straddle both, which means "give my
 * assault fighters probe drones" would be a decision spread over two tabs. Of the sixty-six
 * hulls in the game that carry fighters, only ten carry more than twelve, so a squadron tab bar
 * would also be a single tab on fifty-six of them.
 *
 * <p>The squadron rides along as a badge, because J4.221's seeker handoff and J4.93's EW
 * lending both resolve by it and a player needs to see where a fighter sits.
 *
 * <h2>Default and exceptions</h2>
 * The band header sets a rail across the whole type; the grid below marks and edits the
 * fighters that differ. That is the shape of the decision: a carrier's twelve fighters fly the
 * same thing unless a reason is found for one or two of them.
 */
export function FighterLoadoutDialog({
  ship, overrides, onChange, onClose,
}: {
  ship:      CoiShipData;
  overrides: LoadoutOverrides;
  onChange:  (next: LoadoutOverrides) => void;
  onClose:   () => void;
}) {
  const fighters = ship.fighterLoadouts ?? [];
  const bands = typeBands(fighters);

  const catalogue: DroneCatalogue = {
    types:     ship.availableDroneTypes ?? [],
    modules:   ship.availableDroneModules ?? [],
    additions: ship.availableDroneAdditions ?? [],
  };

  const changed = fighters.filter(f => differsFromIssue(f, overrides)).length;
  const spend = fighterLoadoutCost(overrides, fighters, ship);

  return (
    <div className="fl-backdrop" onClick={onClose}>
      <div className="fl-dialog" onClick={e => e.stopPropagation()}>
        <div className="fl-header">
          <div>
            <h3>Fighter Loadouts</h3>
            <div className="coi-note">
              {ship.shipName} — what each fighter carries on its rails (FD10.646). A rail you
              leave alone flies what the hull issued it, which is already paid for (FD2.31).
            </div>
          </div>
          <button className="fl-close" onClick={onClose}>Close</button>
        </div>

        <div className="fl-summary">
          {changed === 0
            ? 'The squadron flies what it was issued.'
            : `${changed} of ${fighters.length} fighters changed`}
          {spend > 0 && <span className="fl-spend"> — {spend} points (FD2.45)</span>}
        </div>

        {bands.length === 0 && (
          <div className="coi-note">
            Nothing aboard carries drones on rails. A plasma fighter (J4.825) and a fighter
            whose every rail is podded (J4.962) both have nothing to load.
          </div>
        )}

        {bands.map(band => (
          <Band
            key={band.type}
            band={band}
            ship={ship}
            catalogue={catalogue}
            overrides={overrides}
            onChange={onChange}
          />
        ))}
      </div>
    </div>
  );
}

/** Where an open payload editor is pointed: the whole band's rail, or one fighter's. */
type EditTarget = { scope: 'band' | 'fighter'; railIndex: number };

function Band({
  band, ship, catalogue, overrides, onChange,
}: {
  band:      FighterBand;
  ship:      CoiShipData;
  catalogue: DroneCatalogue;
  overrides: LoadoutOverrides;
  onChange:  (next: LoadoutOverrides) => void;
}) {
  const [openFighter, setOpenFighter] = useState<string | null>(null);
  const [editing, setEditing] = useState<EditTarget | null>(null);
  const [refusal, setRefusal] = useState<string | null>(null);

  const standard = bandDefault(band, overrides);
  const open = band.fighters.find(f => f.name === openFighter) ?? null;

  // "2 standard · 2 light · 2 special", which is the fighter's whole drone capability in a line.
  const shape = Object.entries(
    band.rails.reduce<Record<string, number>>((acc, r) => {
      const key = r.railType.toLowerCase();
      acc[key] = (acc[key] ?? 0) + 1;
      return acc;
    }, {}))
    .map(([type, n]) => `${n} ${type}`)
    .join(' · ');

  /** Close any open editor when the thing it is pointed at goes away. */
  function selectFighter(name: string | null) {
    setOpenFighter(name);
    if (editing?.scope === 'fighter') setEditing(null);
    setRefusal(null);
  }

  function put(target: EditTarget, drone: string) {
    // The backstop. The pickers and the editor are both filtered to what this rail takes, so
    // this should not fire — but it is the same question the server will ask, and a composed
    // drone is assembled rather than picked, so the ways to build one a rail refuses are many.
    const rail = band.rails[target.railIndex];
    const why = railRefusal(rail, drone, catalogue);
    if (why) { setRefusal(why); return; }
    setRefusal(null);
    onChange(target.scope === 'band'
      ? setBandRail(overrides, band, target.railIndex, drone)
      : setRail(overrides, open!, target.railIndex, drone));
  }

  function valueAt(target: EditTarget): string {
    return target.scope === 'band'
      ? standard[target.railIndex]
      : loadOf(open!, overrides)[target.railIndex];
  }

  return (
    <div className="fl-band">
      <div className="fl-band-head">
        <span className="fl-band-name">{band.designation}</span>
        <span className="fl-band-count">×{band.fighters.length}</span>
        <span className="coi-note">{shape}</span>
      </div>

      <div className="fl-rails">
        {band.rails.map((rail, i) => (
          <RailPicker
            key={`band-${rail.designator}-${i}`}
            rail={rail}
            catalogue={catalogue}
            value={standard[i]}
            editing={editing?.scope === 'band' && editing.railIndex === i}
            title={`Sets rail ${rail.designator} on all ${band.fighters.length}`
              + ` ${band.designation} fighters`}
            onPick={drone => put({ scope: 'band', railIndex: i }, drone)}
            onEdit={() => {
              setRefusal(null);
              setEditing(editing?.scope === 'band' && editing.railIndex === i
                ? null : { scope: 'band', railIndex: i });
            }}
          />
        ))}
      </div>

      {editing?.scope === 'band' && (
        <PayloadFor
          target={editing}
          rail={band.rails[editing.railIndex]}
          drone={valueAt(editing)}
          ship={ship}
          catalogue={catalogue}
          refusal={refusal}
          note={`Applies to all ${band.fighters.length} ${band.designation} fighters.`}
          onChange={drone => put(editing, drone)}
          onClose={() => { setEditing(null); setRefusal(null); }}
        />
      )}

      <div className="fl-grid">
        {band.fighters.map((f, n) => (
          <FighterToken
            key={f.name}
            fighter={f}
            band={band}
            overrides={overrides}
            index={n + 1}
            open={openFighter === f.name}
            onClick={() => selectFighter(openFighter === f.name ? null : f.name)}
          />
        ))}
      </div>

      {open && (
        <div className="fl-fighter">
          <div className="fl-fighter-head">
            <span className="fl-fighter-name">{open.name}</span>
            {open.squadron && <span className="fl-badge">{open.squadron}</span>}
            <button
              className="fl-reset"
              disabled={!differsFromIssue(open, overrides)}
              onClick={() => { onChange(resetFighter(overrides, open)); setEditing(null); }}
            >
              As issued
            </button>
          </div>
          <div className="fl-rails">
            {open.rails.map((rail, i) => (
              <RailPicker
                key={`one-${rail.designator}-${i}`}
                rail={rail}
                catalogue={catalogue}
                value={loadOf(open, overrides)[i]}
                editing={editing?.scope === 'fighter' && editing.railIndex === i}
                title={`Rail ${rail.designator} on ${open.name} alone`}
                onPick={drone => put({ scope: 'fighter', railIndex: i }, drone)}
                onEdit={() => {
                  setRefusal(null);
                  setEditing(editing?.scope === 'fighter' && editing.railIndex === i
                    ? null : { scope: 'fighter', railIndex: i });
                }}
              />
            ))}
          </div>

          {editing?.scope === 'fighter' && (
            <PayloadFor
              target={editing}
              rail={open.rails[editing.railIndex]}
              drone={valueAt(editing)}
              ship={ship}
              catalogue={catalogue}
              refusal={refusal}
              note={`${open.name} alone.`}
              onChange={drone => put(editing, drone)}
              onClose={() => { setEditing(null); setRefusal(null); }}
            />
          )}
        </div>
      )}
    </div>
  );
}

/**
 * One fighter in the grid: the counter the map draws, framed by its state, over a pip per rail.
 *
 * <h2>Why the art, and why it is not enough on its own</h2>
 * It is the same counter {@code HexGrid} draws on the board, so the loadout screen and the
 * battle show the same object. But every fighter in a band is the same TYPE, so within a band
 * every counter is the same picture — the art is atmosphere and carries no information at all.
 * Eleven identical counters say exactly what eleven identical triangles said.
 *
 * <p>The pips are what the row is for. One per rail, in the order the band's pickers sit above
 * them, filled where that rail differs from the band — so a glance says WHICH rail was changed
 * on which fighter, where the old single marker could only say that one of them was. Their
 * shape follows the rail type, because a special rail is where a type-III or a multi-warhead
 * can go and that is the rail worth finding.
 *
 * <p>State lives on the FRAME rather than in the fill. Over a red Kzinti counter an amber
 * silhouette stops being legible, and the frame is the one part of the token the art does not
 * occupy.
 *
 * <p>22 of the game's 78 fighters have no counter drawn — the Federation line and the Hydran
 * Stinger-3 — and fall back to the plain shape, which is what the map does too.
 */
function FighterToken({
  fighter, band, overrides, index, open, onClick,
}: {
  fighter:   CoiFighterLoadout;
  band:      FighterBand;
  overrides: LoadoutOverrides;
  index:     number;
  open:      boolean;
  onClick:   () => void;
}) {
  const differing = railsDifferingFromBand(fighter, band, overrides);
  const exception = differsFromBand(fighter, band, overrides);
  const load = loadOf(fighter, overrides);

  return (
    <button
      className={'fl-token'
        + (exception ? ' fl-token-exception' : '')
        + (open ? ' fl-token-open' : '')}
      title={`${fighter.name}${fighter.squadron ? ` — ${fighter.squadron}` : ''}\n`
        + load.map((d, i) => `${fighter.rails[i]?.designator}: ${d}`).join('\n')}
      onClick={onClick}
    >
      <span className="fl-token-frame">
        {fighter.tokenArt
          ? <img className="fl-token-img" src={`/tokens/${fighter.tokenArt}`} alt="" />
          : (
            <svg viewBox="0 0 24 20" className="fl-token-shape" aria-hidden="true">
              <polygon points="12,2 23,19 1,19" />
            </svg>
          )}
      </span>
      <span className="fl-token-pips">
        {fighter.rails.map((rail, i) => (
          <span
            key={`${rail.designator}-${i}`}
            className={'fl-pip fl-pip-' + rail.railType.toLowerCase()
              + (differing[i] ? ' fl-pip-changed' : '')}
          />
        ))}
      </span>
      <span className="fl-token-n">{index}</span>
    </button>
  );
}

function RailPicker({
  rail, catalogue, value, editing, title, onPick, onEdit,
}: {
  rail:      CoiFighterRail;
  catalogue: DroneCatalogue;
  value:     string;
  editing:   boolean;
  title?:    string;
  onPick:    (drone: string) => void;
  onEdit:    () => void;
}) {
  const legal = dronesForRail(rail, catalogue);
  // A composed drone ("TypeIF+ECM") is not a catalogue name, so it would vanish from its own
  // picker and the select would show the wrong row. Kept at the head of the list.
  const options = legal.includes(value) ? legal : [value, ...legal];
  const composed = isModified(
    parseDroneName(value, catalogue.modules ?? [], catalogue.additions ?? []));

  return (
    <div className={'fl-rail' + (editing ? ' fl-rail-editing' : '')} title={title}>
      <span className="fl-rail-head">
        {rail.designator}
        <span className="fl-rail-type">{rail.railType.toLowerCase()}</span>
      </span>
      <div className="fl-rail-pick">
        <select value={value} onChange={e => onPick(e.target.value)}>
          {options.map(name => (
            <option key={name} value={name}>{name.replace('Type', 'Type-')}</option>
          ))}
        </select>
        {/* FD10.4's workshop, per rail. The same editor the drone racks use. */}
        <button
          className={'coi-drone-edit' + (composed ? ' fl-rail-composed' : '')}
          title={composed ? `Payload: ${value}` : 'Choose a payload (FD10.4)'}
          onClick={onEdit}
        >
          {composed ? '✦' : '⚙'}
        </button>
      </div>
    </div>
  );
}

/**
 * The payload workshop, pointed at one rail.
 *
 * <h2>What the rail changes about it</h2>
 * Two things, and neither is a new rule — both are the editor being handed a narrower world:
 * <ul>
 *   <li>The modules offered are {@link modulesForRail}'s, which drops the multi-warhead where
 *       J4.23 will not have it. A standard rail still gets every special type-I drone, which
 *       is the half of J4.23 easiest to over-apply.</li>
 *   <li>The room to grow into is the RAIL's capacity rather than a rack's free space, so
 *       FD12.13's external armor is refused where it would overflow (FD10.24's extended range
 *       the same way).</li>
 * </ul>
 */
function PayloadFor({
  target, rail, drone, ship, catalogue, refusal, note, onChange, onClose,
}: {
  target:    EditTarget;
  rail:      CoiFighterRail;
  drone:     string;
  ship:      CoiShipData;
  catalogue: DroneCatalogue;
  refusal:   string | null;
  note:      string;
  onChange:  (drone: string) => void;
  onClose:   () => void;
}) {
  const modules   = catalogue.modules ?? [];
  const additions = catalogue.additions ?? [];
  const comp = parseDroneName(drone, modules, additions);
  const basis = catalogue.types.find(t => t.name === comp.basis);
  if (!basis) return null;

  return (
    <div className="fl-payload">
      <div className="coi-note">
        Rail {rail.designator} ({rail.railType.toLowerCase()}) — {note}
      </div>
      {refusal && <div className="fl-refusal">{refusal}</div>}
      <DronePayloadEditor
        key={`${target.scope}-${target.railIndex}-${comp.basis}`}
        drone={basis}
        comp={comp}
        modules={modulesForRail(rail, modules)}
        additions={additions}
        submunitionSpeeds={ship.availableSubmunitionSpeeds ?? []}
        rackSpaceFree={rail.spaces}
        onChange={next => onChange(formatDroneName(next))}
        onClose={onClose}
      />
    </div>
  );
}

export default FighterLoadoutDialog;
