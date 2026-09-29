package com.sfb.objects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sfb.objects.shuttles.Fighter;

/**
 * A named group of one carrier's fighters (J4.46).
 * <p>
 * "Players may organize fighter squadrons before the scenario begins within these rules."
 * The grouping is not decoration: three rules turn on membership, and none of them can be
 * expressed without it.
 * <ul>
 *   <li>J4.221 — an EW or two-seat fighter may accept control of a seeking weapon, but
 *       ONLY from fighters in its own squadron.</li>
 *   <li>J4.93 — a carrier lends EW to a squadron as a whole, not to individual fighters.</li>
 *   <li>J4.463 — how many EW fighters a carrier may field at all.</li>
 * </ul>
 *
 * <h2>What a squadron may hold</h2>
 * J4.462: twelve fighters, where a heavy fighter or a bomber counts as two — so a squadron
 * of size-2 fighters or size-3/4 bombers maxes out at six. Sizes may not be mixed.
 * <p>
 * J4.463: at most one EW fighter, and none at all in a squadron of fewer than eight —
 * subject to a carrier-wide exception this class does not judge, because it is about the
 * CARRIER's entitlement rather than the squadron's shape. {@code Shuttles} owns that.
 */
public class Squadron {

    /** J4.462: twelve size-1 fighters, and a heavy or a bomber spends two of the slots. */
    public static final int MAX_SLOTS = 12;

    /** J4.463: a squadron smaller than this may not hold an EW fighter. */
    public static final int MIN_FOR_EW_FIGHTER = 8;

    private final String name;
    private final Ship carrier;
    private final List<Fighter> fighters = new ArrayList<>();

    public Squadron(String name, Ship carrier) {
        this.name = name;
        this.carrier = carrier;
    }

    public String getName() { return name; }

    public Ship getCarrier() { return carrier; }

    public List<Fighter> getFighters() {
        return Collections.unmodifiableList(fighters);
    }

    public int size() { return fighters.size(); }

    /** J4.462's count, in which a heavy fighter or bomber is worth two ordinary ones. */
    public int slotsUsed() {
        int slots = 0;
        for (Fighter f : fighters)
            slots += f.squadronSlots();
        return slots;
    }

    /** J4.463: EW and two-seat fighters currently in this squadron. */
    public int ewFighterCount() {
        int n = 0;
        for (Fighter f : fighters)
            if (f.isTwoSeater())
                n++;
        return n;
    }

    /**
     * Why this fighter may not join, or null if it may (J4.461, J4.462, J4.463).
     * <p>
     * The EW test is deliberately only the per-SQUADRON half. Whether the carrier is
     * entitled to another EW fighter at all is J4.463's other half and depends on the
     * whole ship, so {@code Shuttles} asks it — a squadron cannot see its siblings.
     */
    public String refusal(Fighter candidate) {
        if (candidate == null)
            return "no fighter given";
        if (fighters.contains(candidate))
            return candidate.getName() + " is already in " + name;
        // J4.461: one carrier's fighters, and only its own. Only refused where the craft
        // SAYS it belongs elsewhere: a fighter still in its bay has no parent recorded —
        // that is stamped on launch — and is obviously the carrier's own.
        String parent = candidate.getParentShipName();
        if (carrier != null && parent != null && !parent.equals(carrier.getName()))
            return candidate.getName() + " is based on " + parent + ", not "
                    + carrier.getName()
                    + " — a squadron holds one carrier's fighters (J4.461)";
        if (slotsUsed() + candidate.squadronSlots() > MAX_SLOTS)
            return name + " is full — twelve fighters, a heavy counting two (J4.462)";
        // J4.462: "Different sizes of fighters cannot be mixed into a single squadron."
        if (!fighters.isEmpty()
                && fighters.get(0).squadronSlots() != candidate.squadronSlots())
            return name + " holds fighters of a different size, which may not be mixed"
                    + " (J4.462)";
        if (candidate.isTwoSeater() && ewFighterCount() >= 1)
            return name + " already has an EW fighter, and may have only one (J4.463)";
        return null;
    }

    /** Add a fighter if the rules allow. @return the refusal, or null on success. */
    public String add(Fighter fighter) {
        String no = refusal(fighter);
        if (no != null)
            return no;
        fighters.add(fighter);
        fighter.setSquadron(this);
        return null;
    }

    public boolean remove(Fighter fighter) {
        if (!fighters.remove(fighter))
            return false;
        if (fighter.getSquadron() == this)
            fighter.setSquadron(null);
        return true;
    }

    public boolean contains(Fighter fighter) {
        return fighters.contains(fighter);
    }

    /**
     * J4.463: whether this squadron is big enough to hold an EW fighter on its own merits.
     * <p>
     * A squadron of fewer than eight may still end up with one — the rule allows it where
     * the CARRIER qualified for two EW fighters and the fighters simply did not divide
     * evenly. That judgement belongs to the carrier, not here.
     */
    public boolean largeEnoughForEwFighter() {
        return size() >= MIN_FOR_EW_FIGHTER;
    }

    // -------------------------------------------------------------------------
    // Electronic warfare its carrier generates FOR it (J4.93, J4.931)
    // -------------------------------------------------------------------------

    private int carrierEcm;
    private int carrierEccm;

    /**
     * Points the carrier generated this turn for this squadron alone (J4.931).
     * <p>
     * Held on the squadron rather than the ship because the squadron is what the rule lends
     * to, and J4.933 makes the pools independent: "a carrier with more than twelve fighters
     * could divide them into squadrons and generate a SEPARATE SET of EW points for each group
     * (assuming it has the power; yes this means it could generate twelve EW)".
     * <p>
     * A separate pool from the carrier's own EW, which J4.931 insists on: "The same points
     * cannot be used by both the carrier and the fighters." That separation is also what
     * satisfies J4.932's bar on re-lending — these points are generated, never received, so
     * there is nothing borrowed here to pass on.
     */
    public int getCarrierEcm() { return carrierEcm; }

    public int getCarrierEccm() { return carrierEccm; }

    /** Whether the carrier put anything into this squadron's pool this turn. */
    public boolean hasCarrierEw() { return carrierEcm > 0 || carrierEccm > 0; }

    /**
     * Set what the carrier generated for this squadron, as declared at allocation.
     * <p>
     * The caller is responsible for the cap — {@code Shuttles.declareSquadronEw} applies
     * J4.931's "equal limit" — because the limit is the SHIP's sensor rating and a squadron
     * cannot see it.
     */
    public void setCarrierEw(int ecm, int eccm) {
        carrierEcm = Math.max(0, ecm);
        carrierEccm = Math.max(0, eccm);
    }

    /** A fresh pool each turn (J4.931 generates it at allocation, like any other EW). */
    public void resetCarrierEw() {
        carrierEcm = 0;
        carrierEccm = 0;
    }

    @Override
    public String toString() {
        return name + " (" + size() + " fighters"
                + (ewFighterCount() > 0 ? ", EW" : "") + ")";
    }
}
