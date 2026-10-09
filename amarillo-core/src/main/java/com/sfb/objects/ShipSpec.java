package com.sfb.objects;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfb.properties.Faction;
import com.sfb.properties.TurnMode;
import com.sfb.weapons.Weapon;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ShipSpec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // --- Top-level fields ---
    public String faction;
    /** The SSD's "Type" line: this exact variant, e.g. "CA+", "D7C". Identity, not family. */
    public String type;
    /**
     * The family this variant serves in, e.g. "CA" for a D7C. Catalogued in
     * data/shiplines/shiplines.json; what the leader rules (S8.36) compare.
     */
    public String line;

    /**
     * A generation of hulls within one empire, where an empire's ships fall into eras that are
     * worth telling apart. Only the Romulans use it so far, and their history is why it exists:
     * they never developed warp power themselves.
     * <ul>
     *   <li><b>EAGLE</b> — their own pre-warp warships, retrofitted with warp technology once the
     *       Klingons supplied it. War Eagle, Warbird, Snipe, Battle Hawk, King Eagle.</li>
     *   <li><b>KESTREL</b> — Klingon hulls sold to them to bridge the gap while they learned.
     *       Every K-prefixed Romulan hull: K4R, K5R, K5S, K7R, KR, KRC, KRL, K9RB, KRV, K5D.</li>
     *   <li><b>HAWK</b> — original Romulan designs, once they understood the technology.
     *       Skyhawk, Sparrowhawk, Firehawk, Condor.</li>
     * </ul>
     * <b>Not a rule and not read by anything</b> — it is here so the lineage can be written down
     * beside the ship, the same reason {@link #note} and {@link #typeName} are declared. Declared
     * rather than PARKED in {@code ShipJsonKeyGuardTest} on that distinction: parked keys are
     * systems awaiting code (OAKDISC, Tholian webs), and this is permanent description. If the
     * series ever acquires a rules effect it becomes a catalogued vocabulary like {@link #line},
     * with a guard; until then a typo costs nothing but the label.
     * <p>
     * Deliberately generic rather than {@code romulanSeries}: nothing about the field is Romulan,
     * and another empire's generations would use it unchanged.
     */
    public String series;
    public String name;
    public String tokenArt;  // optional path to a PNG token image, e.g. "federation/constitution.png"
    public int serviceYear;
    public int bpv;
    public int epv;
    public int commandRating;
    public String turnMode;
    public int sizeClass;
    public double moveCost;
    public int breakdown;
    public int bonusHets;
    public boolean nimble;
    public int stealthBonus; // Orion Stealth Bonus in ECM points (G15.8), 0 if none
    public Boolean canDoubleEngines; // G15.28: null/absent = capable (default); set false for freighters & the one non-doubling warship

    // --- Fleet-building classifications (S8.0 patrol scenarios). All default false. ---
    /** Leader variant (CWL, DWL, DDL, CC…): restricted by S8.36/S8.361 when fleet building. */
    public boolean isLeader;
    /**
     * A remark for whoever reads the file. Never rendered, never a rule — it exists so a
     * ship's oddities can be written down beside the ship. Declared rather than parked
     * because it is permanent by design, not a system waiting to be built.
     */
    public String note;

    /**
     * The class written out, e.g. "Commando Cruiser" for type "CMC".
     *
     * {@link #type} is the SSD designation and is what the counter and the tooltip have
     * always shown; this is the name a player would say out loud. Every ship file has
     * carried it since the data was written — nothing read it until now.
     */
    public String typeName;

    /** Carrier escort: cannot be fielded except as part of a carrier group (S8.311). */
    public boolean isEscort;

    /**
     * A base rather than a ship: starbase, battle station, base station, outpost.
     *
     * <h2>Why this had to be added rather than used</h2>
     * {@link Ship#isBase()} has existed for some time and gates a real rule — G24.135, a base
     * never blinds its own scout channels when it fires — but nothing could ever turn it on.
     * There was no field here, no key in {@code toMap}, and no ship file set it; the only two
     * places it was ever true were two tests. So every base the game might have fielded would
     * have blinded itself like a ship.
     *
     * <p>The third field found in this shape, after {@code crewQuality} (every ship was NORMAL)
     * and {@code epv} (a primitive that could not say "unset", which made two scouts free). The
     * pattern to watch for is a flag the rules read and the data cannot write.
     */
    public boolean isBase;

    /**
     * Aegis fire control fitted to this hull (D13.0): "NONE", "LIMITED" or "FULL".
     * <p>
     * Deliberately NOT derived from {@link #isEscort}. Most aegis ships are carrier escorts,
     * but D13.0 names the Klingon D5 as a non-escort that has it, and plenty of escorts predate
     * the system. The two answer different questions and one field cannot do both.
     * <p>
     * Nor is it derived from {@link #serviceYear}: the pre- and post-Y175 versions are separate
     * hulls with their own years and BPVs — Kzinti EFF (Y168, limited) against AFF (Y175, full).
     * See {@link com.sfb.properties.AegisLevel}.
     */
    public String aegis;

    /**
     * Weapon TYPES this ship's aegis may control, when it cannot control everything.
     * <p>
     * D13.22's default is that "the aegis system can control all direct-fire weapons", and
     * absent or empty means exactly that. The clause continues "unless noted otherwise, for
     * example the D5" — so a hull may name a shorter list, and the Klingon D5's is its ADDs
     * and its four phaser-3s.
     * <p>
     * Type names, matching {@code Weapon.getType()} and the "type" in this file's own weapon
     * entries: "ADD", "Phaser3". Not designators — a restriction that happened to name every
     * weapon of a type would be the same list written less robustly, and would rot the moment
     * a variant renumbered its mounts.
     */
    public java.util.List<String> aegisWeapons;
    /**
     * Cannot be fielded without an escort group (S8.315): a size class 2 ship of this kind
     * needs three escorts, class 3 two, class 4 one, at least one of them size class 4.
     * <p>
     * Also decides S8.321's fighter limit — a true carrier's fighters count against the battle
     * force's allowance, a hybrid's do not (S8.322) — which is unimplemented but will read
     * this flag, not carrierClass: a Hydran Ranger is a CAPABLE carrier whose Stingers do NOT
     * count. Named for the escort constraint because that is what it enforces today and
     * nothing says only a carrier can need escorts.
     */
    public boolean requiresEscort;
    /**
     * What kind of carrier this is (J4.61/J4.62) — which decides capabilities, not fleet
     * legality: extra deck crews, EW lent to fighters, and S4.1's weapon status provisions.
     * Separate from requiresEscort because a Hydran fighter ship is a capable carrier that is
     * fielded without escorts. Not inferable from bay contents: plenty of ships carry a few
     * fighters without being carriers.
     */
    public String carrierClass;

    /**
     * G21.0 crew quality: "poor", "normal" or "outstanding". <b>Null means normal</b>, which is
     * what the great majority of ships are — G21.0 reckons "a fleet organization of 100 ships might
     * have ten poor crews and five outstanding ones at any given time".
     * <p>
     * A property of the HULL here because some ships are poor by design: the Klingon F5J Penal
     * Frigate is crewed by prisoners. G21.0's own distribution talk is about assigning quality
     * across a fleet at the start of a campaign ("dreadnoughts and all size class 2 ships never
     * have outstanding crews. Bases and scouts always have average crews"), so a scenario-level
     * override belongs beside this eventually — this is the floor, not the ceiling.
     * <p>
     * Parsed leniently by {@code Crew.init}, which treats anything it does not recognise as
     * normal, so a typo costs the declaration rather than the load. The string rather than an enum
     * matches {@link #aegis} and {@link #carrierClass}.
     * <p>
     * <b>Two sites honour it today</b> and both arrived from other rules rather than from G21:
     * {@code BoardingResolver} shifts hit-and-run and boarding rolls (D7.72/D7.73), and
     * {@code Game.collisionDie} has a poor crew lose the nimble die-shift (C11.33). G21.1 and
     * G21.2's own adjustment lists — direct fire, manoeuvre, systems — are NOT built.
     * <p>
     * G21.142 keeps this off shuttles: "admin shuttle pilots are always treated as good", so
     * {@code Game.crewQualityFor} answers for a Ship and nothing else.
     */
    public String crewQuality;
    /**
     * Spaces of spare drones this carrier holds for its fighters (J4.7).
     * <p>
     * Per ship, out of Annex #7G, and declared rather than derived: the rulebook prints only
     * the Kzinti CV's 150 and there is no formula behind the rest. J4.72 makes this the TOTAL
     * — the ready racks and the fighters' own loads come out of it, not on top of it.
     * <p>
     * Zero means no supply, which is the honest answer for every hull whose Annex #7G line we
     * have not read: its racks hold what they hold and cannot be refilled.
     */
    public double droneStorageSpaces;

    /**
     * FD2.445: spaces of spare drones a cargo box holds, when this ship's description says its
     * cargo boxes carry drones. <b>Null means they do not</b> — which is almost every hull.
     * <p>
     * Opt-in because the rule is: "Some drone-armed ships have cargo boxes to store extra drones.
     * Unless otherwise specified a cargo box will hold 50 spaces of spare drones. It does not have
     * them automatically, however, unless specified in the ship description." Deriving
     * {@code cargo * 50} for everything would give the Hydran Caravan's 32 freight boxes 1600
     * spaces of drones, and the Federation CVL's ten survey boxes 500 on top of the 250 of FIGHTER
     * supply it already has.
     * <p>
     * A rate rather than a total, so it cannot drift from the hull. The D5D's SSD reads "This ship
     * has 200 spaces of extra drones in its cargo boxes (50/box)" — four boxes at 50, and the 200
     * follows. {@code Integer} rather than {@code int} so "says nothing" and "says none" stay
     * different facts.
     * <p>
     * NOT the same thing as {@link #droneStorageSpaces}, which is FD2.443/J4.7 fighter supply and
     * is not linked to cargo boxes at all. See {@link com.sfb.systemgroups.CargoDroneStore}.
     */
    public Integer cargoDroneSpacesPerBox;
    /**
     * Heavy battlecruiser. No more than one may be in a battle force, though it needs no
     * squadron of followers and may be there alongside the one allowed size class 2 ship
     * (S8.333).
     */
    public boolean isBCH;

    /**
     * Annex #3's <b>"D%"</b> or <b>"DB"</b> marking: a unit allowed a higher proportion of special
     * drones than its empire normally gets, and a bigger Commander's Option budget to buy them with.
     *
     * <p><b>Declared, not derived</b> — and that is the rulebook's own shape, not a shortcut. FD10.622
     * and FD10.632 each list three exceptions to the ordinary caps, and only two of them can be
     * computed: Kzinti ships are a faction check, carriers with ten or more fighters (or five heavy
     * ones) come from the complement. The third is "any other unit with 'D%' or 'DB' in the notes
     * column of the Master Ship Chart" — a CHART MARKING, so a boolean on the hull is the faithful
     * representation of it rather than a workaround.
     *
     * <p><b>It does two things.</b> FD10.622/FD10.632 double the caps — Restricted 25% to 50%,
     * Limited 10% to 20%. And S3.223 raises the Commander's Option budget from 20% of Effective
     * Combat BPV to <b>30%</b>, with the extra tenth spendable only on "extra or improved drones".
     *
     * <p><b>One flag covers both markings, but a DB ship is only CONDITIONALLY the same as a D% one.</b>
     * They are different categories — D% is mostly carriers, PFTs and auxiliaries, DB the
     * drone-bombardment ships (FD10.671 defines the marking) — and S3.222 gives a DB ship two modes:
     *
     * <ul>
     *   <li><b>On an independent bombardment mission</b> it may be loaded entirely with type-III-XX
     *       drones, but uses its <b>normal racial percentages</b> for special warheads — NOT the
     *       doubled ones — and pays normal rack costs with free reloads plus 25% of the cost of the
     *       drones in its cargo boxes. It trades the better caps for a cheap hold of heavy drones.
     *   <li><b>Otherwise</b>, "a drone bombardment ship is treated as a D% ship (S3.223), i.e., as if
     *       it were a carrier with ten or more fighters" — doubled caps and the 30% budget.
     * </ul>
     *
     * <p><b>This flag implements the second branch, and that is correct while bombardment missions do
     * not exist in this engine.</b> Nothing can assign one, so "not on such a mission" is true of every
     * scenario we can express, and S3.222 says plainly what to do in that state. When missions arrive
     * this becomes a two-state question and the first branch needs building — with FD10.671's trap in
     * it: a type-III-XX is a two-space drone with a SINGLE payload space (FD10.24), so "six type-B
     * drone racks would hold 36 spaces of drones but only eighteen payload spaces for warhead
     * calculation purposes", and FD10.641 takes its percentages of warhead spaces. A bombardment load
     * halves the basis the caps are reckoned against.
     *
     * <p>The DB roster is in the Master Annex File's drone-storage table: Federation NCD, CAD, CLD,
     * NDC, VDB; Klingon D5D, D5DX, D6D, DWD, DDP, P-D8; Kzinti DF, SDF, SDW, CD, MDC, YCD, YDF.
     */
    public boolean dPercent;

    public int[] shields;

    // --- Nested specs ---
    public HullSpec hullBoxes;
    public PowerSpec power;
    public ControlSpec control;
    public TableSpec tables;
    public AuxiliarySpec auxiliary;
    public CrewSpec crewData;

    public List<WeaponSpec> weapons;
    public List<OptionMountSpec> optionMounts; // Orion "OPT" boxes (G15.4); empty until filled at setup
    public List<ShuttleBaySpec> shuttleBays;
    /** If present, applied instead of faction default Y175 upgrades. Empty lists = fully exempt. */
    public Y175Upgrades y175Upgrades;

    /**
     * REFITS this hull may be fitted with, and the historical names of the combinations.
     *
     * <h2>Why a hull declares its own refits</h2>
     * <b>S3.24</b>: "If a ship never received a particular refit (as noted in either its unit
     * description or on its SSD) it cannot purchase that refit." So eligibility is a property of the
     * hull, and the list being here IS that eligibility. Nothing is faction-wide, because the
     * conventions are not: the Klingon K refit swaps phaser-2s for phaser-1s, the Lyran B refit is a
     * power pack, and the same refit raises different shields to different numbers on different
     * hulls.
     *
     * <h2>Why variants have to be NAMED and cannot be derived</h2>
     * The resulting type code does not follow from the base and the refit. A D7 with the K refit is a
     * D7K, but a D7C with the K refit is a <b>D7L</b> — the owner's point, 2026-10-07, and the reason
     * is that D7CK would collide confusingly with D7K. The Lyran CWB is a CW with three refits at
     * once and says none of them in its name. So {@link #variants} maps a set of refit codes to the
     * code history gave it, and only an unnamed combination falls back to a derived one.
     *
     * <h2>What this replaces</h2>
     * One file per combination. That enumeration was lossy as well as long: the Lyran CWB silently
     * contains the + and the phaser refits, so "power pack without the phaser refit" could not be
     * expressed at all. Refits as independent switches can express every combination; the variant
     * list only says what to CALL the ones that happened.
     */
    public List<RefitSpec> refits;
    public List<VariantSpec> variants;

    /**
     * On a SYNTHESISED variant: the base hull's type, and the refits applied to reach this one.
     *
     * <p>Set by {@link RefitResolver}, never written in a file — a hull on disk is always a base.
     * Two things need it. A guard that asks whether a marked hull is justified has to be able to look
     * at the hull the marking came from: the D6DB inherits the D6D's Annex #3 "DB", and the annex has
     * no D6DB row to point at. And the fleet-builder shelf groups rows by the base hull, which is the
     * whole reason this work started — grouping by name would have merged the D6 with the D7.
     */
    public String refitOf;
    public List<String> appliedRefits;

    /**
     * One refit: what it costs, when it arrived, and what it does to this hull.
     *
     * <p>The diff is stated as replacements and additions rather than as a whole ship, so that two
     * refits can be applied together without either one overwriting the other's work. Only the
     * fields a refit actually touches are set; everything else is inherited from the base hull.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RefitSpec {
        /** The marker history uses: "B", "K", "u", "+", "p". */
        public String code;
        /** For a player: "B refit", "Phaser-1 refit". */
        public String name;
        /**
         * What the fleet builder writes on the refit's button, where {@link #name} is too long or
         * too cryptic to read at a glance.
         *
         * <p>Optional, and absent on almost every refit: the shelf falls back to the name with a
         * trailing " refit" stripped. That fallback is fine for "Phaser-1 refit" and useless for
         * the Lyran and Federation "+ refit", which renders as a bare "+" and then collides with
         * the cost beside it — "+ +10" is not a thing anyone can read. A hull that wants to call
         * that button "Plus Refit" or "Power Upgrade" says so here.
         *
         * <p>Presentation only. Nothing in the rules engine reads it, and it never affects what a
         * refit costs or does — {@link #code} remains the identity.
         */
        public String label;
        /** Year the refit became available (S3.24 gates purchase on the scenario year). */
        public int year;
        /** BPV this refit adds. The deltas proved additive across every family. */
        public int bpv;
        /** Other refit codes this one needs — the Klingon K refit arrives on top of B's shields. */
        public List<String> requires;

        // ---- the diff. Null or absent means "this refit does not touch that".
        /** Replacement shield box counts, all six. */
        public int[] shields;
        /** Extra EPV, where a refit changes it. */
        public Integer epv;
        /**
         * Changes to the base's power, auxiliary and control blocks, <b>named field by field</b>:
         * {@code "power": { "apr": 5, "battery": 2 }}.
         *
         * <p>Maps rather than nested specs, and this is the trap that forces it: every field in
         * {@link PowerSpec}, {@link AuxiliarySpec} and {@link ControlSpec} is a primitive, so a
         * {@code PowerSpec} carrying only {@code apr} would also carry {@code leftWarp = 0} and
         * merging it would strip the hull's warp engines. A primitive cannot say "I am not setting
         * this"; a map simply does not contain the key.
         */
        public java.util.Map<String, Object> power;
        public java.util.Map<String, Object> auxiliary;
        public java.util.Map<String, Object> control;
        /** Weapons added outright. */
        public List<WeaponSpec> addWeapons;
        /** Weapons swapped in place, keeping their designators and arcs. */
        public List<WeaponSwap> replaceWeapons;
        /**
         * Any OTHER field of the hull this refit replaces, by name:
         * {@code "set": { "crewData": {...}, "hullBoxes": {...} }}.
         *
         * <p>The named fields above cover what most refits do — shields, power, a weapon swap. But a
         * big refit rebuilds the ship: the Federation GSC's carrier conversion changes its crew, its
         * shuttle bays, its drone storage AND the two flags that make it a carrier at all, and
         * without this those changes were dropped in silence, because Jackson ignores a key the spec
         * does not declare. Found by the migration's equivalence check.
         *
         * <p>Whole-value replacement, not a merge, and applied by reflection against the real field
         * so a misspelling fails loudly rather than doing nothing. For the three primitive BLOCKS
         * use {@link #power}, {@link #auxiliary} and {@link #control} instead — those merge field by
         * field, because a primitive cannot say "I am not setting this".
         */
        public java.util.Map<String, Object> set;
        /**
         * A refit that CONVERTS one power system into another, however many there are.
         *
         * <p>The Federation AWR refit replaces a hull's auxiliary power reactors with warp reactors,
         * and its effect depends on the ship it is fitted to: a bare DN has two APRs, the improved
         * DN+ has four. An absolute {@code "awr": 2} is therefore wrong on one of them, and so is a
         * flat BPV — the cost is one point per reactor, so the identical refit block is +2 on the DN
         * and +4 on the DN+ with nothing restated.
         *
         * <p>The first refit in the data whose effect is not a fixed delta. Carried by the CA, CC,
         * NCL, OCL, FFG, DN and DN+ — and the last two are separate FILES, which is the point: the
         * Federation counted the DN+ a different ship rather than a refitted DN, so the conversion
         * has to read the hull it lands on rather than the hull above it.
         */
        public PowerConversion convertPower;
        /**
         * Replaces the hull's Y175 upgrade block.
         * <p>
         * Here because the data puts it here: a D6 declares none and a D6B declares one, so the block
         * travelled with the B refit. Whether that is deliberate is a question for the owner — an
         * ABSENT block means the faction default applies, so the base is not necessarily missing
         * anything. The migration preserves what the files said rather than deciding.
         */
        public Y175Upgrades y175Upgrades;
    }

    /**
     * A weapon upgrade: every {@code from} at the listed designators becomes a {@code to}.
     * <p>
     * Stated by designator rather than positionally because that is how the SSD reads — "phasers 3
     * through 6 become phaser-1s" — and because a positional rule would silently move if the base
     * hull's weapon order ever changed.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WeaponSwap {
        /** The weapon type this applies to. */
        public String from;
        /** A new type, where the refit replaces the weapon outright (Phaser-2 to Phaser-1). */
        public String to;
        /**
         * Properties to change in place: {@code "set": { "range": 30 }} for the Klingon disruptor
         * refit, {@code "set": { "rackType": "TYPE_A" }} for the rack upgrade that travels with it.
         * <p>
         * This exists because the first version of the model had only {@code from}/{@code to}, and
         * the equivalence test caught what that missed: the B refit also lengthens the D6's
         * disruptors from 22 to 30 and upgrades its racks. A diff that compares weapons only by type
         * and designator cannot see either.
         */
        public java.util.Map<String, Object> set;
        /** Which designators, or all of that type when omitted. */
        public List<String> designators;
    }

    /** One power system converted into another, priced per unit (the Federation AWR refit). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PowerConversion {
        /** The field converted away, e.g. "apr". */
        public String from;
        /** What it becomes, e.g. "awr". */
        public String to;
        /** BPV per unit converted. One, for the AWR refit. */
        public int bpvEach;
    }

    /** What to call one combination of refits, where history named it. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VariantSpec {
        /** The refit codes, in any order. */
        public List<String> refits;
        /** The type code this combination is known by: "D6K", "D7L", "CWB". */
        public String type;
        /** Overrides the base's typeName when the refit renames the ship (D5C -> D5L). */
        public String typeName;
        /** Overrides the derived service year, where the combination arrived later. */
        public Integer serviceYear;
        /** The default ship name for this variant, as the base hull has one. */
        public String name;
    }

    // -------------------------------------------------------------------------
    // Inner spec classes
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HullSpec {
        public int fhull;
        public int ahull;
        public int chull;
        public int armor;
        public int cargo;
        public int barracks; // Not a real hull box type, but used for boarding party calculations
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OptionMountSpec {
        public String position;   // "CENTERLINE" | "WING" (G15.43)
        public String designator; // e.g. "A"
        public List<String> arcs; // e.g. ["FA"]
        public WeaponSpec weapon; // pinned weapon (G15.4); null = empty mount, player-chosen at setup
        public double bpvCost;    // BPV delta of the pinned option (Annex #8B); may be negative/fractional
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PowerSpec {
        public int leftWarp;
        public int rightWarp;
        public int centerWarp;
        public int impulse;
        public int apr;
        public int awr;
        public int battery;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ControlSpec {
        public int bridge;
        public int emergency;
        public int auxCon;
        public int flag;
        public int security;
        public double controlMod;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TableSpec {
        public int[] damageControl;
        public int[] sensor;
        public int[] scanner;
        public int excess;
    }

    /**
     * A bay's fighter complement by ROLE, resolved to types by the year (J4.4).
     * <p>
     * The alternative was to list concrete fighters in {@code shuttles}, which can only ever be
     * right for one era: a Kzinti CVS entered service in Y170 flying AAS, re-equipped with HAAS
     * in Y173, TAAS in Y177, TADS in Y180 and TADSC in Y183, and none of that changes its SSD.
     * Authoring five files per carrier would multiply every hull correction by five and leave
     * the fighters' BPV — which feeds the carrier's S3.211 option budget — to be maintained by
     * hand in each.
     * <p>
     * So the ship declares the shape of its complement and {@code fighterLines} in
     * shuttles.json decides what fills it. Counts are per role; a role the era has no type for
     * falls back to the standard fighter.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FighterComplementSpec {
        /** Line name as keyed in shuttles.json, e.g. "kzinti-attack". */
        public String line;
        /**
         * SUPERIORITY fighters: the general-purpose type, able to intercept enemy fighters and
         * pose some threat to ships. The annex's own word — "two will be superiority fighters
         * and two will be assault fighters" — and the fallback every other role resolves to.
         */
        public int superiority;
        /**
         * ATTACK fighters: dedicated to attacking ships, but WITHOUT a heavy weapon. Carried on
         * many kinds of carrier rather than reserved to the biggest, which is what separates
         * them from {@link #assault}.
         */
        public int attack;
        /**
         * ASSAULT fighters: carrying a heavy weapon — disruptors or photons, and plasma-F for
         * the Romulans and Gorns. The annex labels them "A" in the spare-shuttle column and
         * calls them "single-space assault fighters", distinguishing them from the two-space
         * heavy fighters it labels "H".
         * <p>
         * Mostly found on the largest carriers and on bases, but deliberately NOT enforced as a
         * size class rule: the annex gives the size class 3 Klingon D6V an assault fighter
         * ("The spare fighter on the D6V labeled 'A' is an assault fighter, I.e., Z-D or Z-P").
         * Doctrine for whoever authors the data, not a constraint the engine imposes.
         */
        public int assault;
        /**
         * ELITE fighters: a superiority type on its own programme, flown by very few hulls and
         * evolving on its own schedule. The Federation F-14 is the case - F-14, F-14A, F-14B,
         * carried by the CVA while every other carrier flies the F-4 to F-18 progression. A
         * role rather than a line of its own because a bay MIXES programmes: the CVA's two
         * bays hold six elite and six assault fighters apiece.
         */
        public int elite;
        /**
         * EW fighters (J4.463 caps how many a carrier may field), of whatever kind the line
         * flies. Enough for a bay with one programme in it.
         */
        public int ew;
        /**
         * The EW fighter of a NAMED programme, for a bay that flies two and so cannot leave it
         * to the line. The CVA carries an F-14 squadron and an A-10 squadron across two bays
         * of twelve, so one bay's EW fighter is an F-14E ({@code elite_ew}) and the other's an
         * A-10E ({@code assault_ew}).
         * <p>
         * Spelled out as fields rather than held in a map on purpose: a map would accept
         * {@code assualt_ew} and seat no fighters at all, silently. These keep
         * {@code ShipJsonKeyGuardTest} able to fail the build on a typo, which is the failure
         * this data has had most often.
         */
        public int superiority_ew;
        public int elite_ew;
        public int attack_ew;
        public int assault_ew;

        /**
         * NOT a role: a HEAVY fighter is the two-space category the annex labels "H", and it
         * breaks the bay's one-craft-per-space model, so it is not expressible yet. J4.463
         * counts heavy fighters in their own right ("five or more heavy fighters"). The name is
         * reserved — see the fighter-lines notes.
         */
        public int total() {
            return superiority + elite + attack + assault
                 + ew + superiority_ew + elite_ew + attack_ew + assault_ew;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ShuttleBaySpec {
        public List<String> shuttles;  // e.g. ["admin", "admin", "gas"]
        /**
         * Fighters by role, resolved by year (J4.4). Sits alongside {@code shuttles} rather
         * than replacing it: the admin shuttles sharing the bay stay listed literally, because
         * nothing about them changes with the year.
         */
        public FighterComplementSpec fighters;
        public int          launchTubes; // J1.54 — 0 means standard hatch only
        /**
         * J1.58: "tunnel" for a bay with doors at both ends, each hatch working
         * independently at the full J1.50 rate. The Kzinti CV, CVS, CVL, MCV and CVE are
         * built this way, as is the Federation CVS. Absent means an ordinary one-hatch bay.
         * <p>
         * A hatch is not a launch tube: a tube cannot recover a shuttle (J1.541) and will
         * not pass an administrative shuttle or a heavy fighter (J1.542). A second hatch
         * has no such limits, which is why a tunnel deck is counted here and not as tubes.
         */
        public String       type;
        /**
         * J1.53: balcony positions belonging to THIS bay — outside parking on a mechanical
         * track. Absent or zero means the bay has no balcony, which is almost every bay.
         * <p>
         * Per bay rather than per ship, and the Federation CVA is why: its SSD reads "Each
         * Size-1 Fighter bay has six balcony positions: Total of 12", so its two fighter bays
         * have six each and its admin bay has none. A per-ship figure could not say that.
         * The Gorn BC is three and three.
         * <p>
         * Not a count of shuttle boxes. A balcony position is not a {@link ShuttleBaySpec}
         * space at all — see {@code ShuttleBay.getBalcony()} for what it is and is not.
         */
        public int          balconyPositions;
        /**
         * J4.62/J4.621: how many of THIS bay's shuttle boxes can SERVICE a fighter, on a ship that
         * carries no fighters of its own - a "casual carrier", which J4.62 says is "most carrier
         * escorts, the Hydran Pegasus and Gendarme, and many WYN ships".
         * <p>
         * Named for the rulebook's own general term. J4.89: "The variously described ready racks
         * (J4.822) and storage boxes (J4.88) can be included in the general term 'ready rack' or
         * 'fighter facility' or 'weapons charge storage facility' or 'capacitor' for purposes of
         * these rules."
         * <p>
         * So the four species J4.73 lists - ready rack, photon freezer, plasma-F stasis box,
         * fusion and hellbore stores - share one name, and this is it. Which one a box gets depends
         * on the fighter it serves, so a Hydran escort declaring this gets fusion capacitors and no
         * rack at all; a field called {@code readyRacks} read as a contradiction there, even though
         * J4.89 would also have allowed that name.
         * <p>
         * J4.89's last sentence is worth keeping in view: "Some carriers have two or more kinds of
         * fighter facilities; this requires additional restrictions." A single count per bay cannot
         * express a mixed bay, and will need to become a per-box declaration if one ever appears.
         * <p>
         * A count of BOXES, and that is the point: J4.822 puts the fitting in a shuttle box and
         * J4.831 destroys it with the box, so an escort's facilities take damage exactly as a
         * carrier's do and need no separate answer on the DAC. G33.43 shows the two numbers differ
         * - an escort with "four shuttle boxes with two ready racks" - so this cannot be inferred
         * from the box count.
         * <p>
         * What they SERVICE is not declared here. J4.621: "the fighters on the carrier will
         * determine what type of ready racks are on the escort", so the bay names a {@code
         * fighters} LINE with no counts, the player picks the actual model as a Commander's Option,
         * and the scenario year resolves it. Naming a fighter type HERE would be wrong in every
         * year but one; naming it in a COI choice is right, because the year is settled by then.
         */
        public int          fighterFacilities;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AuxiliarySpec {
        public int transporters;
        public int tractors;
        public int labs;
        public int probes;
        public int shuttles;
        public int tBombs;
        public int dummyTBombs;
        public int nuclearSpaceMines;
        public int cloakCost;
        public boolean derfacs;
        public int uim;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CrewSpec {
        public int totalCrew;
        public int boardingParties;
        /**
         * Deck crews (J4.814). <b>Integer, not int</b>, so that "the file says nothing" and "the
         * file says none" are different facts.
         * <p>
         * As a primitive these were the same value, and two layers then conspired: the map build
         * below dropped any count that was not {@code > 0}, and {@code Crew.init} turns an absent
         * key into the J4.814 default of two. So {@code "deckCrews": 0} silently became TWO. Found
         * 2026-10-03 while mutation-testing the facilities guard — a hull mutated to zero crews
         * kept passing, because it was not actually running with zero.
         * <p>
         * This is the trap CLAUDE.md records for the DTO boundary, one layer further in: a
         * primitive cannot say "not applicable", so it cannot say "deliberately none" either.
         */
        public Integer deckCrews;
        public int minCrew;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Y175RackUpgrade {
        public String designator;   // matches weapon designator in the ship JSON
        public String upgradeTo;    // DroneRackType name, e.g. "TYPE_B", "TYPE_C"
        public int    extraReloads; // additional reload sets (e.g. Federation TYPE_G +1)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Y175AddUpgrade {
        public String designator;  // matches weapon designator
        public String upgradeTo;   // AddType name, e.g. "ADD_12"
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Y175Upgrades {
        public List<Y175RackUpgrade> racks = new ArrayList<>();
        public List<Y175AddUpgrade>  adds  = new ArrayList<>();
        public int refitCost = 0;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WeaponSpec {
        public String type;
        public String designator;
        public List<String> arcs;
        /** For disruptors: range (e.g. 30 or 15) */
        public int range;
        /** For plasma launchers: "R", "S", "G", "F" etc. */
        public String plasmaType;
        /** For plasma launchers: legal facing directions on launch */
        public List<String> launchDirections;
        /** For drone racks: "TYPE_F", "TYPE_G" */
        public String rackType;
        /**
         * For drone RAILS: "LIGHT", "STANDARD", "SPECIAL", "HEAVY" (J4.231).
         * <p>
         * Required, not defaulted. The rail decides what fits on it and what that costs, so a
         * rail of the wrong type quietly accepts the wrong drones — a worse failure than the
         * factory refusing to build it.
         */
        public String railType;
        /** For drone racks: number of spaces */
        public int spaces;
        /** For ADD: type string */
        public String addType;
        /** For ScoutChannel: DAC hit location of the weapon it replaced (G24.17), e.g. "torp", "phaser". */
        public String dacHitLocation;
    }

    // -------------------------------------------------------------------------
    // JSON loading
    // -------------------------------------------------------------------------

    public static ShipSpec fromJson(File file) throws IOException {
        return MAPPER.readValue(file, ShipSpec.class);
    }

    // -------------------------------------------------------------------------
    // Conversion to Map for Ship.init()
    // -------------------------------------------------------------------------

    /**
     * Convert this spec into the Map<String, Object> format expected by
     * Ship.init().
     */
    public Map<String, Object> toInitMap() {
        Map<String, Object> m = new HashMap<>();

        m.put("faction", Faction.valueOf(faction));
        m.put("type", type);
        if (line != null && !line.isBlank())
            m.put("line", line);
        m.put("name", name);
        if (tokenArt != null) m.put("tokenart", tokenArt);
        m.put("serviceyear", serviceYear);
        m.put("bpv", bpv);
        // Only when the hull SAYS so. G24.35 gives a scout a split value, economic over combat,
        // and a hull that lists one BPV is telling us the two are equal — which is what Ship's
        // constructor does with an absent epv, and has always meant to.
        //
        // That fallback was dead. `epv` is a primitive int, so an omitted key arrives as 0 and
        // this put asserted it; the constructor's `values.get("epv") == null` could never be
        // true for a JSON-loaded ship. The Klingon D6D and the Kzinti CD are scouts that list
        // only a BPV, so FleetValidator.costOf asked for their economic value, got 0, and sold
        // two 113-point cruisers for nothing. Found in the fleet builder, where the CD showed a
        // cost of 0 beside a SCOUT badge.
        //
        // The same primitive-cannot-say-nothing trap as the DTO fields in CLAUDE.md, one layer
        // further in. Guarded now by ShipCostTest.
        if (epv > 0)
            m.put("epv", epv);
        if (commandRating > 0)
            m.put("commandrating", commandRating);
        m.put("turnmode", TurnMode.valueOf(turnMode));
        m.put("sizeclass", sizeClass);
        m.put("movecost", moveCost);
        m.put("breakdown", breakdown);
        m.put("bonushets", bonusHets);
        if (nimble)
            m.put("nimble", true);
        if (isLeader)
            m.put("isleader", true);
        if (typeName != null)
            m.put("typename", typeName);
        if (isEscort)
            m.put("isescort", true);
        if (isBase)
            m.put("isbase", true);
        if (aegis != null)
            m.put("aegis", aegis);
        if (aegisWeapons != null && !aegisWeapons.isEmpty())
            m.put("aegisweapons", aegisWeapons);
        if (requiresEscort)
            m.put("requiresescort", true);
        if (carrierClass != null)
            m.put("carrierclass", carrierClass);
        // Absent means normal, so an unstated quality is simply left out of the map and
        // Crew.init's default stands. Nothing had ever put this key in: the enum, the DTO field
        // and both rules sites existed, and every ship in the game was NORMAL because no entry
        // point reached them.
        if (crewQuality != null)
            m.put("crewquality", crewQuality);
        if (droneStorageSpaces > 0)
            m.put("dronestoragespaces", droneStorageSpaces);
        if (cargoDroneSpacesPerBox != null)
            m.put("cargodronespacesperbox", cargoDroneSpacesPerBox);
        if (isBCH)
            m.put("isbch", true);
        if (dPercent)
            m.put("dpercent", true);
        if (stealthBonus > 0)
            m.put("stealthbonus", stealthBonus);
        if (canDoubleEngines != null)
            m.put("candoubleengines", canDoubleEngines);
        if (optionMounts != null && !optionMounts.isEmpty())
            m.put("optionmounts", buildOptionMounts());

        // Shields
        if (shields != null && shields.length >= 6) {
            m.put("shield1", shields[0]);
            m.put("shield2", shields[1]);
            m.put("shield3", shields[2]);
            m.put("shield4", shields[3]);
            m.put("shield5", shields[4]);
            m.put("shield6", shields[5]);
        }

        // Hull boxes
        if (hullBoxes != null) {
            if (hullBoxes.fhull > 0)
                m.put("fhull", hullBoxes.fhull);
            if (hullBoxes.ahull > 0)
                m.put("ahull", hullBoxes.ahull);
            if (hullBoxes.chull > 0)
                m.put("chull", hullBoxes.chull);
            if (hullBoxes.cargo > 0)
                m.put("cargo", hullBoxes.cargo);
            if (hullBoxes.armor > 0)
                m.put("armor", hullBoxes.armor);
        }

        // Power
        if (power != null) {
            if (power.leftWarp > 0)
                m.put("lwarp", power.leftWarp);
            if (power.rightWarp > 0)
                m.put("rwarp", power.rightWarp);
            if (power.centerWarp > 0)
                m.put("cwarp", power.centerWarp);
            if (power.impulse > 0)
                m.put("impulse", power.impulse);
            if (power.apr > 0)
                m.put("apr", power.apr);
            if (power.awr > 0)
                m.put("awr", power.awr);
            if (power.battery > 0)
                m.put("battery", power.battery);
        }

        // Control
        if (control != null) {
            m.put("bridge", control.bridge);
            m.put("emer", control.emergency);
            m.put("auxcon", control.auxCon);
            if (control.security > 0)
                m.put("security", control.security);
            m.put("controlmod", control.controlMod);
            if (control.flag > 0)
                m.put("flag", control.flag);
        }

        // Tables
        if (tables != null) {
            m.put("damcon", tables.damageControl);
            m.put("sensor", tables.sensor);
            m.put("scanner", tables.scanner);
            m.put("excess", tables.excess);
        }

        // Auxiliary
        if (auxiliary != null) {
            m.put("trans", auxiliary.transporters);
            m.put("tractor", auxiliary.tractors);
            m.put("lab", auxiliary.labs);
            m.put("probe", auxiliary.probes);
            m.put("shuttle", auxiliary.shuttles);
            if (auxiliary.tBombs > 0)
                m.put("tbombs", auxiliary.tBombs);
            if (auxiliary.dummyTBombs > 0)
                m.put("dummytbombs", auxiliary.dummyTBombs);
            if (auxiliary.nuclearSpaceMines > 0)
                m.put("nuclearspacemines", auxiliary.nuclearSpaceMines);
            if (auxiliary.cloakCost > 0)
                m.put("cloakcost", auxiliary.cloakCost);
            if (auxiliary.derfacs)
                m.put("derfacs", true);
            if (auxiliary.uim > 0)
                m.put("uim", auxiliary.uim);
        }

        // Shuttle bays — use object format when launch tubes are specified (J1.54)
        if (shuttleBays != null && !shuttleBays.isEmpty()) {
            List<Object> bayList = new ArrayList<>();
            for (ShuttleBaySpec bay : shuttleBays) {
                List<String> shuttles = bay.shuttles != null ? bay.shuttles : new ArrayList<>();
                // A role-based complement forces the object form, since the plain list has
                // nowhere to carry it.
                if (bay.launchTubes > 0 || bay.type != null || bay.fighters != null
                        || bay.balconyPositions > 0 || bay.fighterFacilities > 0) {
                    Map<String, Object> bayMap = new HashMap<>();
                    bayMap.put("shuttles", shuttles);
                    if (bay.launchTubes > 0)
                        bayMap.put("launchTubes", bay.launchTubes);
                    if (bay.type != null)
                        bayMap.put("type", bay.type);
                    // A balcony alone forces the object form too — the plain list has nowhere
                    // to carry it, and a bay with a balcony and nothing else would lose it.
                    if (bay.balconyPositions > 0)
                        bayMap.put("balconyPositions", bay.balconyPositions);
                    // J4.62: a casual carrier's racks, which likewise force the object form.
                    if (bay.fighterFacilities > 0)
                        bayMap.put("fighterFacilities", bay.fighterFacilities);
                    if (bay.fighters != null) {
                        Map<String, Object> f = new HashMap<>();
                        f.put("line", bay.fighters.line);
                        f.put("superiority", bay.fighters.superiority);
                        f.put("elite", bay.fighters.elite);
                        f.put("attack", bay.fighters.attack);
                        f.put("assault", bay.fighters.assault);
                        f.put("ew", bay.fighters.ew);
                        f.put("superiority_ew", bay.fighters.superiority_ew);
                        f.put("elite_ew", bay.fighters.elite_ew);
                        f.put("attack_ew", bay.fighters.attack_ew);
                        f.put("assault_ew", bay.fighters.assault_ew);
                        bayMap.put("fighters", f);
                    }
                    bayList.add(bayMap);
                } else {
                    bayList.add(shuttles);
                }
            }
            m.put("shuttlebays", bayList);
        }

        // Crew
        if (crewData != null) {
            m.put("crew", crewData.totalCrew);
            m.put("boardingparties", crewData.boardingParties);
            m.put("minimumcrew", crewData.minCrew);
            // != null, not > 0: a file stating zero deck crews must reach Crew as zero rather
            // than being dropped here and defaulted back to two by Crew.init.
            if (crewData.deckCrews != null)
                m.put("deckcrews", crewData.deckCrews);
        }

        // Weapons
        if (weapons != null) {
            m.put("weapons", buildWeapons());
        }

        return m;
    }

    // -------------------------------------------------------------------------
    // Weapon building
    // -------------------------------------------------------------------------

    private List<OptionMount> buildOptionMounts() {
        List<OptionMount> list = new ArrayList<>();
        for (OptionMountSpec ms : optionMounts) {
            OptionMount.Position pos = "WING".equalsIgnoreCase(ms.position)
                    ? OptionMount.Position.WING
                    : OptionMount.Position.CENTERLINE;
            List<String> arcs = (ms.arcs == null || ms.arcs.isEmpty()) ? List.of("FULL") : ms.arcs;
            OptionMount mount = new OptionMount(pos, ms.designator, arcs);
            if (ms.weapon != null) {
                // Pinned weapon (G15.4): build it with the MOUNT's arc — the mount
                // defines where the option fires, not the weapon's native arc.
                Weapon w = WeaponFactory.build(ms.weapon, arcs);
                if (w != null) {
                    mount.setWeapon(w);
                }
                mount.setBpvCost(ms.bpvCost);
            }
            list.add(mount);
        }
        return list;
    }

    private List<Weapon> buildWeapons() {
        List<Weapon> list = new ArrayList<>();
        for (WeaponSpec ws : weapons) {
            Weapon w = WeaponFactory.build(ws, ws.arcs);
            if (w != null) {
                // ESG capacitor presence (G23.24) is decided by the SCENARIO year in
                // ScenarioLoader (a Y120 hull in a Y167+ battle still has capacitors),
                // not here — weapons are built before the battle year is known.
                list.add(w);
            }
        }
        return list;
    }

}
