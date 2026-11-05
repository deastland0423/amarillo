import { useState } from 'react';
import type { CoiFighterRail, CoiShipData } from '../api/gameApi';
import {
  bandDefault, differsFromBand, differsFromIssue, fighterLoadoutCost, loadOf, railRefusal,
  resetFighter, setBandRail, setRail, typeBands,
} from './fighterLoadout';
import type { FighterBand, LoadoutOverrides } from './fighterLoadout';

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
  const [openFighter, setOpenFighter] = useState<string | null>(null);

  const catalogue = {
    types:     ship.availableDroneTypes ?? [],
    modules:   ship.availableDroneModules ?? [],
    additions: ship.availableDroneAdditions ?? [],
  };

  const changed = fighters.filter(f => differsFromIssue(f, overrides)).length;
  const spend = fighterLoadoutCost(overrides, fighters, ship);

  /** The drones this rail will take, with whatever it is carrying kept on the list. */
  function optionsFor(rail: CoiFighterRail, current: string): string[] {
    const legal = catalogue.types
      .filter(t => railRefusal(rail, t.name, catalogue) === null)
      .map(t => t.name);
    // A composed drone ("TypeIF+ECM") is not a catalogue name, so it would vanish from its own
    // picker and the select would show the wrong row. Kept at the head of the list.
    return legal.includes(current) ? legal : [current, ...legal];
  }

  function RailPicker({ rail, value, onPick, title }: {
    rail:   CoiFighterRail;
    value:  string;
    onPick: (drone: string) => void;
    title?: string;
  }) {
    return (
      <label className="fl-rail" title={title}>
        <span className="fl-rail-head">
          {rail.designator}
          <span className="fl-rail-type">{rail.railType.toLowerCase()}</span>
        </span>
        <select value={value} onChange={e => onPick(e.target.value)}>
          {optionsFor(rail, value).map(name => (
            <option key={name} value={name}>{name.replace('Type', 'Type-')}</option>
          ))}
        </select>
      </label>
    );
  }

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
            overrides={overrides}
            onChange={onChange}
            openFighter={openFighter}
            setOpenFighter={setOpenFighter}
            RailPicker={RailPicker}
          />
        ))}
      </div>
    </div>
  );
}

function Band({
  band, overrides, onChange, openFighter, setOpenFighter, RailPicker,
}: {
  band:        FighterBand;
  overrides:   LoadoutOverrides;
  onChange:    (next: LoadoutOverrides) => void;
  openFighter: string | null;
  setOpenFighter: (name: string | null) => void;
  RailPicker: (props: {
    rail: CoiFighterRail; value: string; onPick: (d: string) => void; title?: string;
  }) => React.ReactElement;
}) {
  const standard = bandDefault(band, overrides);
  const open = band.fighters.find(f => f.name === openFighter) ?? null;

  // "2 standard · 2 light · 2 special", which is the fighter's whole drone capability in a line.
  const shape = Object.entries(
    band.rails.reduce<Record<string, number>>((acc, r) => {
      acc[r.railType.toLowerCase()] = (acc[r.railType.toLowerCase()] ?? 0) + 1;
      return acc;
    }, {}))
    .map(([type, n]) => `${n} ${type}`)
    .join(' · ');

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
            key={`${rail.designator}-${i}`}
            rail={rail}
            value={standard[i]}
            title={`Sets rail ${rail.designator} on all ${band.fighters.length}`
              + ` ${band.designation} fighters`}
            onPick={drone => onChange(setBandRail(overrides, band, i, drone))}
          />
        ))}
      </div>

      <div className="fl-grid">
        {band.fighters.map((f, n) => {
          const exception = differsFromBand(f, band, overrides);
          return (
            <button
              key={f.name}
              className={'fl-token'
                + (exception ? ' fl-token-exception' : '')
                + (openFighter === f.name ? ' fl-token-open' : '')}
              title={`${f.name}${f.squadron ? ` — ${f.squadron}` : ''}\n`
                + loadOf(f, overrides)
                  .map((d, i) => `${band.rails[i]?.designator}: ${d}`).join('\n')}
              onClick={() => setOpenFighter(openFighter === f.name ? null : f.name)}
            >
              <svg viewBox="0 0 24 20" className="fl-token-art" aria-hidden="true">
                <polygon points="12,2 23,19 1,19" />
              </svg>
              <span className="fl-token-n">{n + 1}</span>
              {exception && <span className="fl-token-mark">✦</span>}
            </button>
          );
        })}
      </div>

      {open && (
        <div className="fl-fighter">
          <div className="fl-fighter-head">
            <span className="fl-fighter-name">{open.name}</span>
            {open.squadron && <span className="fl-badge">{open.squadron}</span>}
            <button
              className="fl-reset"
              disabled={!differsFromIssue(open, overrides)}
              onClick={() => onChange(resetFighter(overrides, open))}
            >
              As issued
            </button>
          </div>
          <div className="fl-rails">
            {open.rails.map((rail, i) => (
              <RailPicker
                key={`${rail.designator}-${i}`}
                rail={rail}
                value={loadOf(open, overrides)[i]}
                title={`Rail ${rail.designator} on ${open.name} alone`}
                onPick={drone => onChange(setRail(overrides, open, i, drone))}
              />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export default FighterLoadoutDialog;
