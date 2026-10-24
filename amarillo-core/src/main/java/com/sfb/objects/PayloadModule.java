package com.sfb.objects;

/**
 * FD10.4: what goes in a drone's payload spaces.
 *
 * <p>FD10.41 defines the standard payload and gives it two sizes:
 *
 * <blockquote>"EXPLOSIVE MODULES are the standard drone payload (or warhead). There are two
 * sizes: 1/2 payload space = explosion strength six; 1 payload space = explosion strength
 * twelve. Two-space drones (such as type-IV) carry two one-payload space explosive modules (or
 * four 1/2-space modules)... All of the explosive modules on a given drone are combined into a
 * single explosion; they are not resolved as separate volleys. <b>There is no difference between
 * two 1/2-space and one 1.0-space module.</b>"</blockquote>
 *
 * <p>That last sentence is why warhead strength is a SUM rather than a lookup: a type-IV's 24
 * points are two twelves, or four sixes, and the rule says those are the same drone. Today every
 * one-space drone in the game carries exactly one full explosive module, so frame size and
 * warhead happen to agree; they stop agreeing the moment FD10.48's null drone or FD10.42's probe
 * module exists, which is the whole reason this is modelled rather than stored.
 *
 * <h2>Only the modules the current roster needs are here</h2>
 * The probe, ECM, armor and null modules are catalogued in FD10.42 through FD10.48 and each has
 * its own rules and its own behaviour — FD10.403 goes as far as to spell out which combinations
 * cannot exist. None is expressible yet and none is declared here, because an enum constant that
 * nothing can build is a claim this file should not be making.
 */
public enum PayloadModule {

    /** FD10.41: half a payload space, explosion strength six. */
    EXPLOSIVE_HALF(0.5, 6),

    /** FD10.41: one payload space, explosion strength twelve. The usual meaning of the term. */
    EXPLOSIVE_1(1.0, 12),

    /**
     * The type-VI's fixed warhead, which is not a module a player can choose and is here so that
     * {@link DroneFrame#HALF_SPACE} has something to carry.
     *
     * <p><b>Eight, and not six.</b> A half-space explosive module is strength six (FD10.41), but
     * a dogfight drone is not built from one: FD2.54 gives it a warhead that depends on what it
     * hits — two points against size class 4 and larger, four against size class 5, eight against
     * size class 6 and 7. Eight is the SIZE-6/7 figure, recorded here because that is the number
     * {@code DroneType.damage} has always carried and what {@code Drone.impact()} returns as the
     * nominal before {@code Drone.impact(Unit)} applies the rule.
     *
     * <p>So this constant is a nominal, not a strength. The rule lives in {@code Drone}, where it
     * already worked before this model existed and was deliberately left alone.
     */
    DOGFIGHT_WARHEAD(0.5, 8);

    /** Payload spaces consumed (FD10.4). */
    public final double spaces;

    /** Points this module contributes to the drone's single combined explosion (FD10.41). */
    public final int strength;

    PayloadModule(double spaces, int strength) {
        this.spaces = spaces;
        this.strength = strength;
    }
}
