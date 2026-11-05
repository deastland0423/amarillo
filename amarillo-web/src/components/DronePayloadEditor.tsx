import type { CoiDroneAddition, CoiDroneModule, CoiDroneType } from '../api/gameApi';
import {
  additionRefusal, moduleRefusal, payloadUsed, spacesUsedLabel,
} from './droneComposition';
import type { DroneComposition } from './droneComposition';

/**
 * FD10.4's workshop, shared by the two places a drone is composed.
 *
 * <h2>Why it is its own file</h2>
 * It began inside {@code CoiDialog}, which was fine while a rack was the only thing that held
 * a drone. A fighter's RAIL holds one too (J4.23), and {@code FighterLoadoutDialog} is rendered
 * BY CoiDialog — so importing the editor back out of it would be a cycle. Nothing about the
 * editor was ever specific to a rack: it takes a drone, a composition and the room it may grow
 * into, and says nothing about where that room is.
 */

/**
 * One loaded drone's payload (FD10.2) — the third axis of the picker.
 *
 * The numeral in a drone's name carries its family and the suffix carries its speed, and that is
 * all a loadout could say until core learned to name a composed drone. Armor, ECM, probe,
 * swordfish and multi-warhead modules were built, priced and capped long before anyone could
 * ORDER one.
 *
 * What this deliberately does NOT show: the hull, the warhead, the speed or the price of what is
 * being built. Every one of those is derived from the composition by rules this view does not
 * hold and must not — FD12.21 adds hull per armor module, FD10.41 combines the warheads into
 * one explosion, FD12.132's chart takes a fast one-space drone from 32 to 12 for a full external
 * module, and FD2.22 charges the difference from the type-I-slows already in the ship's BPV. A
 * number here would be a second answer waiting to disagree with the server's.
 *
 * So it shows what was ticked, the space it fills, and how hard each part is to come by. The
 * ship's own budget line already reports what the server says it all costs.
 */
export function DronePayloadEditor({
  drone, comp, modules, additions, submunitionSpeeds, rackSpaceFree, onChange, onClose,
}: {
  drone:     CoiDroneType;
  comp:      DroneComposition;
  modules:   CoiDroneModule[];
  additions: CoiDroneAddition[];
  submunitionSpeeds: { name: string; speed: number; availability: string }[];
  /** Rack spaces this drone may grow into, its own footprint included (FD3.0). */
  rackSpaceFree: number;
  onChange: (next: DroneComposition) => void;
  onClose:  () => void;
}) {
  const capacity = drone.payloadSpaces ?? 0;
  const used     = payloadUsed(comp, modules);

  // FD10.431: a multi-warhead module carries type-VI drones instead of a warhead, and FD8.24
  // buys them an engine of their own. Nothing to choose until one is aboard.
  const carriesSubmunitions = comp.modules.some(
    n => (modules.find(m => m.name === n)?.submunitions ?? 0) > 0);

  function countOf(name: string): number {
    return comp.modules.filter(n => n === name).length;
  }
  function addModule(m: CoiDroneModule) {
    onChange({ ...comp, modules: [...comp.modules, m.name] });
  }
  function dropModule(name: string) {
    const at = comp.modules.lastIndexOf(name);
    if (at < 0) return;
    const next = comp.modules.filter((_, i) => i !== at);
    // FD8.21: a submunition speed belongs to a drone that carries submunitions. Taking the last
    // MW module off takes the engines with it, rather than leaving a choice with nothing to
    // apply to — which core refuses outright.
    const stillHasMw = next.some(
      n => (modules.find(m => m.name === n)?.submunitions ?? 0) > 0);
    onChange({ ...comp, modules: next,
      submunitionSpeed: stillHasMw ? comp.submunitionSpeed : null });
  }
  function toggleAddition(a: CoiDroneAddition, on: boolean) {
    let next = comp.additions.filter(n => n !== a.name);
    if (on) {
      // FD12.131: "No more than one external armor module can be added to any one drone." So
      // ticking one unticks the other rather than being refused on submission.
      if (a.externalArmor)
        next = next.filter(
          n => !(additions.find(x => x.name === n)?.externalArmor ?? false));
      next = [...next, a.name];
    }
    onChange({ ...comp, additions: next });
  }

  const tier = (t: string) => t === 'GENERAL' ? '' : ` · ${t.toLowerCase()}`;
  const sp = (n: number) => n === 0.5 ? '½sp' : `${n}sp`;

  return (
    <div className="coi-payload-editor">
      <div className="coi-payload-head">
        <strong>{comp.basis}</strong>
        <span className="coi-note">
          {' — payload '}{spacesUsedLabel(used)} / {capacity}
          {capacity === 1 ? ' space' : ' spaces'}
        </span>
        <button className="coi-drone-remove" onClick={onClose} title="Done">{'✕'}</button>
      </div>

      {modules.length > 0 && (
        <>
          <div className="coi-note">
            Payload (FD10.4). A module you name REPLACES what the drone came with, so a drone
            with nothing ticked here carries its standard warhead.
          </div>
          {modules.map(m => {
            const n = countOf(m.name);
            // Every reason the rules have for not letting this module on THIS drone, in one
            // tested place: FD10.443, FD12.131, FD10.4 and FD10.402. They live in
            // moduleRefusal now because one of them was missing from here and nothing on this
            // side could be asked the question.
            const refusal = moduleRefusal(comp, m, modules, additions, capacity);
            return (
              <div key={m.name} className="coi-payload-row">
                <button className="secondary coi-drone-add-btn"
                        disabled={n <= 0}
                        onClick={() => dropModule(m.name)}>{'−'}</button>
                <span className="coi-payload-count">{n}</span>
                <button className="secondary coi-drone-add-btn"
                        disabled={refusal != null}
                        title={refusal ?? undefined}
                        onClick={() => addModule(m)}>+</button>
                <span className="coi-payload-label">{m.label}</span>
                <span className="coi-note">
                  {sp(m.spaces)}
                  {m.strength > 0 && ` · ${m.strength} dmg`}
                  {m.hullBonus > 0 && ` · +${m.hullBonus} hull`}
                  {m.submunitions > 0 && ` · ${m.submunitions} type-VIs`}
                  {m.requiresForward && ' · forward only'}
                  {tier(m.availability)}
                </span>
              </div>
            );
          })}
        </>
      )}

      {additions.length > 0 && (
        <>
          <div className="coi-note" style={{ marginTop: '0.3rem' }}>
            Frame (FD10.53). Bolted on rather than carried, so it fills no payload space.
          </div>
          {additions.map(a => {
            const on = comp.additions.includes(a.name);
            // FD10.24: extended range turns a type-III into a two-space drone. Shown on the
            // label as well as checked for room, because it is the one modification that costs
            // a player RACK space and nothing else on the row would say so.
            const grows = a.name === 'EXTENDED_RANGE'
              && (drone.rackWithExtendedRange ?? drone.rack) > drone.rack;
            const refusal = additionRefusal(comp, a, modules,
              drone.rack, rackSpaceFree, drone.rackWithExtendedRange);
            return (
              <label key={a.name} className="coi-label coi-check">
                <input type="checkbox" checked={on}
                  disabled={refusal != null}
                  title={refusal ?? undefined}
                  onChange={e => toggleAddition(a, e.target.checked)} />
                {a.label}
                <span className="coi-note">
                  {a.hullBonus > 0 && ` — +${a.hullBonus} hull`}
                  {a.externalArmor && ' · costs speed (FD12.132)'}
                  {grows && ' · two rack spaces (FD10.24)'}
                  {tier(a.availability)}
                </span>
              </label>
            );
          })}
        </>
      )}

      {carriesSubmunitions && submunitionSpeeds.length > 0 && (
        <div className="coi-row" style={{ marginTop: '0.3rem' }}>
          <label className="coi-label">Submunition speed (FD8.11)</label>
          <select value={comp.submunitionSpeed ?? ''}
            onChange={e => onChange({ ...comp, submunitionSpeed: e.target.value || null })}>
            {/* FD8.45: nothing bought means they fly at a type-VI's own baseline, which FD2.227
                puts at 12 rather than 8. That is the free option, not a missing one. */}
            <option value="">Standard</option>
            {submunitionSpeeds.map(p => (
              <option key={p.name} value={p.name}>
                Speed {p.speed}{p.availability !== 'GENERAL'
                  ? ` (${p.availability.toLowerCase()})` : ''}
              </option>
            ))}
          </select>
        </div>
      )}
      {carriesSubmunitions && (
        <div className="coi-note">
          FD8.24: every submunition buys its own engine on top of the drone's, which is what
          makes a multi-warhead drone expensive.
        </div>
      )}
    </div>
  );
}
