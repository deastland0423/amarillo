package com.sfb.objects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sfb.properties.PlasmaType;
import com.sfb.properties.TurnMode;
import com.sfb.properties.WeaponArmingType;

/**
 * A plasma torpedo is a seeking weapon that tracks its target like a drone,
 * but deals damage that degrades with distance traveled.
 *
 * Damage at impact = max(0, baseStrength - distanceTraveled).
 * When damage reaches 0 the torpedo has exhausted itself and is removed.
 *
 * Enveloping plasma splits damage evenly across all six shields.
 * Shotgun mode is handled by the launcher creating multiple Plasma-F torpedoes.
 */
public class PlasmaTorpedo extends Unit implements Seeker {

    private PlasmaType plasmaType;
    private WeaponArmingType armingType;

    private Unit target;
    private Unit controller;
    private int launchImpulse;
    private int distanceTraveled = 0; // impulses since launch
    private double damageTaken = 0.0; // accumulated phaser damage (1 phaser pt = 0.5 damage)
    private Seeker.SeekerType seekerType = Seeker.SeekerType.PLASMA;
    private boolean identified = false;
    private boolean cloakLockRetained = false; // G13.3343 — won retention vs a cloaked target
    private boolean pseudoPlasma = false; // True if this torpedo is a pseudo-torpedo: behaves like a real torpedo but
                                          // deals zero damage on impact.

    // Damage tables by range for each plasma type. Index = distance traveled in
    // impulses.
    private static final int[] PLASMA_R_DAMAGE_BY_RANGE = { 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 35, 35, 35, 35,
            35, 25, 25, 25, 25, 25, 20, 20, 20, 20, 25, 10, 10, 10, 5, 1, 0 };
    private static final int[] PLASMA_S_DAMAGE_BY_RANGE = { 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 22, 22, 22, 22,
            22, 15, 15, 15, 15, 15, 10, 10, 10, 5, 1, 0 };
    private static final int[] PLASMA_G_DAMAGE_BY_RANGE = { 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 15, 15, 15, 15,
            15, 10, 10, 10, 5, 1, 0 };
    private static final int[] PLASMA_F_DAMAGE_BY_RANGE = { 20, 20, 20, 20, 20, 20, 15, 15, 15, 15, 15, 10, 10, 5, 5,
            1, 0 };
    private static final int[] PLASMA_D_DAMAGE_BY_RANGE = { 10, 10, 10, 10, 10, 10, 8, 8, 8, 8, 8, 5, 5, 2, 2, 1, 0 };

    private static final int[] PLASMA_K_DAMAGE_BY_RANGE = { 5, 5, 5, 5, 5, 5, 4, 4, 2, 2, 1 };

    private static final int SPEED = 32;

    /** Minimal instance for client-side rendering only — no game logic. */
    public static PlasmaTorpedo forRendering() {
        return new PlasmaTorpedo(PlasmaType.R, WeaponArmingType.STANDARD);
    }

    public PlasmaTorpedo(PlasmaType type, WeaponArmingType armingType) {
        this.sizeClass = 7;
        this.plasmaType = type;
        this.armingType = armingType;
        setTurnMode(TurnMode.Seeker);
        setSpeed(SPEED);
    }

    /**
     * Current damage strength.
     * Enveloping table value doubled, then phaser damage subtracted.
     * Standard: table value, then phaser damage subtracted.
     * Returns 0 if the torpedo has traveled beyond its table or been shot down.
     */
    public int getCurrentStrength() {
        int[] table = getDamageTable();

        if (distanceTraveled >= table.length)
            return 0;
        int base = isEnveloping() ? table[distanceTraveled] * 2 : table[distanceTraveled];
        return Math.max(0, base - (int) damageTaken);
    }

    /**
     * Compute per-shield damage distribution for an enveloping impact.
     * Returns an int[6] where index 0 = shield 1 ... index 5 = shield 6.
     * Base damage is divided evenly; remainder points distributed randomly,
     * at most 1 extra per shield.
     */
    public int[] computeEnvelopingDamage() {
        int total = getCurrentStrength();
        int base = total / 6;
        int remainder = total % 6;

        int[] damage = new int[6];

        for (int i = 0; i < 6; i++)
            damage[i] = base;

        // Pick 'remainder' distinct shield indices at random for the +1 bonus
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < 6; i++)
            indices.add(i);
        Collections.shuffle(indices);

        for (int i = 0; i < remainder; i++)
            damage[indices.get(i)]++;

        return damage;
    }

    /**
     * Distribute a given total (already ECM-reduced) enveloping across 6 shields.
     * Same random-remainder logic as the no-arg version.
     */
    public int[] computeEnvelopingDamage(int total) {
        int base = total / 6;
        int remainder = total % 6;
        int[] damage = new int[6];
        for (int i = 0; i < 6; i++)
            damage[i] = base;
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < 6; i++)
            indices.add(i);
        Collections.shuffle(indices);
        for (int i = 0; i < remainder; i++)
            damage[indices.get(i)]++;
        return damage;
    }

    /**
     * Apply phaser fire to this torpedo. Every 2 points of phaser damage
     * reduces torpedo strength by 1.
     */
    public void applyPhaserDamage(int phaserPoints) {
        damageTaken += phaserPoints * 0.5;
    }

    public double getDamageTaken() {
        return damageTaken;
    }

    private int[] getDamageTable() {
        switch (plasmaType) {
            case R:
                return PLASMA_R_DAMAGE_BY_RANGE;
            case S:
                return PLASMA_S_DAMAGE_BY_RANGE;
            case G:
                return PLASMA_G_DAMAGE_BY_RANGE;
            case D:
                return PLASMA_D_DAMAGE_BY_RANGE;
            case K:
                return PLASMA_K_DAMAGE_BY_RANGE;
            case F:

            default:
                return PLASMA_F_DAMAGE_BY_RANGE;
        }
    }

    public void incrementDistance() {
        distanceTraveled++;
    }

    public int getDistanceTraveled() {
        return distanceTraveled;
    }

    public PlasmaType getPlasmaType() {
        return plasmaType;
    }

    public void setPlasmaType(PlasmaType type) {
        this.plasmaType = type;
    }

    public void setDistanceTraveled(int d) {
        this.distanceTraveled = d;
    }

    public void setDamageTaken(double damage) {
        this.damageTaken = damage;
    }

    public WeaponArmingType getArmingType() {
        return armingType;
    }

    public boolean isEnveloping() {
        return armingType == WeaponArmingType.OVERLOAD;
    }

    // --- Seeker interface ---

    @Override
    public void setTarget(Unit target) {
        this.target = target;
    }

    @Override
    public Unit getTarget() {
        return target;
    }

    @Override
    public Seeker.SeekerType getSeekerType() {
        return seekerType;
    }

    @Override
    public void setSeekerType(SeekerType type) {
        this.seekerType = type;
    }

    @Override
    public boolean isSelfGuidingCapable() {
        return true;        // F3.42 names plasma torpedoes first, with no condition attached
    }

    /**
     * True once this torpedo has won its own G13.3343 retention roll against a
     * target that completed fade-out (or was launched on a retained lock-on,
     * G13.32). Consulted only while the target is fully cloaked; re-baselined
     * by the next fade-out-completion roll, so it never needs explicit clearing.
     */
    public boolean isCloakLockRetained() {
        return cloakLockRetained;
    }

    public void setCloakLockRetained(boolean retained) {
        this.cloakLockRetained = retained;
    }

    @Override
    public int getBuiltInEccm() {
        return 3;
    } // FP4.31: plasma torpedoes carry 3 built-in ECCM points

    @Override
    public int getEndurance() {
        return getCurrentStrength();
    }

    @Override
    public void setEndurance(int e) {
        /* not used — derived from distance */ }

    @Override
    public int getLaunchImpulse() {
        return launchImpulse;
    }

    @Override
    public void setLaunchImpulse(int i) {
        this.launchImpulse = i;
    }

    @Override
    public int getWarheadDamage() {
        return getCurrentStrength();
    }

    @Override
    public void setWarheadDamage(int d) {
        /* damage is table-driven — not settable directly */
    }

    @Override
    public void setController(Unit u) {
        this.controller = u;
    }

    @Override
    public Unit getController() {
        return controller;
    }

    @Override
    public void identify() {
        this.identified = true;
    }

    @Override
    public boolean isIdentified() {
        return identified;
    }

    public boolean isPseudoPlasma() {
        return pseudoPlasma;
    }

    public void setPseudoPlasma(boolean pseudo) {
        this.pseudoPlasma = pseudo;
    }

    /**
     * Impact the target. Returns current strength as damage, or 0 for a
     * pseudo-torpedo.
     * Caller is responsible for applying enveloping spread if isEnveloping().
     */
    @Override
    public int impact() {
        return pseudoPlasma ? 0 : getCurrentStrength();
    }

    /** F4.0 ballistic course: a target HEX instead of a target unit. Null when pursuing a unit. */
    private BallisticCourse ballisticCourse;

    @Override
    public BallisticCourse getBallisticCourse() {
        return ballisticCourse;
    }

    @Override
    public void setBallisticCourse(BallisticCourse course) {
        this.ballisticCourse = course;
    }

    // -------------------------------------------------------------------------
    // Saved game (see Seeker.exportState)
    // -------------------------------------------------------------------------

    /** What {@link #exportState} writes under {@link Seeker#SAVE_KIND}. */
    public static final String SAVE_KIND_PLASMA = "PLASMA";

    /**
     * A torpedo in flight, completely enough to rebuild it.
     *
     * <h2>Three fields that are easy to lose and expensive to lose</h2>
     * <ul>
     *   <li>{@code damageTaken} is accumulated point-defence fire (FP1.7) — half a point per
     *       phaser point. A torpedo two thirds worn down coming back fresh is the difference
     *       between a ship surviving and not.</li>
     *   <li>{@code distanceTraveled} is how far it has flown, and a plasma's warhead DECAYS with
     *       distance (FP1.5). Restoring it at zero would make an old torpedo hit like a new
     *       one.</li>
     *   <li>{@code pseudoPlasma} is whether this is a bluff (FP2.0), and it is HIDDEN
     *       INFORMATION. It must survive, because a pseudo that came back real would do damage
     *       it should never do — but it must never reach the enemy's client, which is the DTO's
     *       job and not this method's.</li>
     * </ul>
     *
     * <p>{@code cloakLockRetained} is the G13.3343 roll this torpedo already won against a
     * cloaked target; dropping it would make it re-roll something it has earned.
     */
    @Override
    public java.util.Map<String, Object> exportState() {
        java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
        state.put(Seeker.SAVE_KIND, SAVE_KIND_PLASMA);
        state.put("name", getName());
        state.put("plasmaType", plasmaType == null ? null : plasmaType.name());
        state.put("armingType", armingType == null ? null : armingType.name());

        if (getLocation() != null) {
            state.put("x", getLocation().getX());
            state.put("y", getLocation().getY());
        }
        state.put("facing", getFacing());
        state.put("speed", getSpeed());

        // References, by name. Resolved in pass two of Game.applyState.
        state.put("target", getTarget() == null ? null : getTarget().getName());
        state.put("controller", controller == null ? null : controller.getName());

        state.put("launchImpulse", launchImpulse);
        state.put("distanceTraveled", distanceTraveled);
        state.put("damageTaken", damageTaken);
        state.put("identified", identified);
        state.put("cloakLockRetained", cloakLockRetained);
        state.put("pseudoPlasma", pseudoPlasma);
        state.put("ballisticCourse",
                ballisticCourse == null ? null : ballisticCourse.exportState());
        return state;
    }

    /**
     * Rebuild a torpedo from a save, with its references already resolved by the caller.
     *
     * The plasma type and arming type go through the constructor because together they decide the
     * warhead; an unknown name returns null rather than a torpedo with no warhead at all.
     */
    public static PlasmaTorpedo fromState(java.util.Map<String, Object> state, Unit target,
            Unit controller, Unit groundTarget) {
        if (state == null)
            return null;
        String typeName = com.sfb.systemgroups.StateIo.text(state, "plasmaType");
        String armingName = com.sfb.systemgroups.StateIo.text(state, "armingType");
        if (typeName == null)
            return null;
        com.sfb.properties.PlasmaType type;
        com.sfb.properties.WeaponArmingType arming;
        try {
            type = com.sfb.properties.PlasmaType.valueOf(typeName);
            arming = armingName == null ? com.sfb.properties.WeaponArmingType.STANDARD
                    : com.sfb.properties.WeaponArmingType.valueOf(armingName);
        } catch (IllegalArgumentException e) {
            return null;
        }

        PlasmaTorpedo torpedo = new PlasmaTorpedo(type, arming);

        String name = com.sfb.systemgroups.StateIo.text(state, "name");
        if (name != null)
            torpedo.setName(name);
        if (state.get("x") instanceof Number x && state.get("y") instanceof Number y)
            torpedo.setLocation(new com.sfb.properties.Location(x.intValue(), y.intValue()));
        torpedo.setFacing(com.sfb.systemgroups.StateIo.integer(state, "facing",
                torpedo.getFacing()));
        torpedo.setSpeed(com.sfb.systemgroups.StateIo.integer(state, "speed",
                torpedo.getSpeed()));

        torpedo.setTarget(target);
        torpedo.setController(controller);

        torpedo.launchImpulse = com.sfb.systemgroups.StateIo.integer(state, "launchImpulse",
                torpedo.launchImpulse);
        torpedo.distanceTraveled = com.sfb.systemgroups.StateIo.integer(state,
                "distanceTraveled", torpedo.distanceTraveled);
        torpedo.damageTaken = com.sfb.systemgroups.StateIo.real(state, "damageTaken",
                torpedo.damageTaken);
        torpedo.cloakLockRetained = com.sfb.systemgroups.StateIo.flag(state, "cloakLockRetained",
                torpedo.cloakLockRetained);
        torpedo.pseudoPlasma = com.sfb.systemgroups.StateIo.flag(state, "pseudoPlasma",
                torpedo.pseudoPlasma);
        if (com.sfb.systemgroups.StateIo.flag(state, "identified", false))
            torpedo.identify();

        torpedo.setBallisticCourse(BallisticCourse.fromState(
                com.sfb.systemgroups.StateIo.child(state, "ballisticCourse"), groundTarget));
        return torpedo;
    }
}
