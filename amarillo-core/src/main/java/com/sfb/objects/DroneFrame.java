package com.sfb.objects;

import java.util.List;

/**
 * FD10.21: the airframe a drone is built on, and the first of the three things FD10.2 says a
 * drone is made of — frame, propulsion (FD10.3) and payload (FD10.4).
 *
 * <blockquote>"There are two types of drone frames: the one-space and two-space types. Each
 * frame has space for a propulsion section and a payload section. The frame includes the
 * standard guidance package. There is also a third frame (the half-space type-VI), but as this
 * cannot be modified as extensively as the others it is not necessary to discuss it in detail.
 * <b>Only the speed can be varied</b>."</blockquote>
 *
 * <h2>Why RACK SPACES and PAYLOAD SPACES are separate numbers</h2>
 * They agree on every drone in the game today — a one-space frame has one payload space, a
 * two-space frame has two — which is exactly what makes assuming they are the same thing safe
 * right up until it is not. FD10.24 is the counterexample:
 *
 * <blockquote>"Type-III drones are one-space drones, but when given extended range (FD2.222),
 * they become type-III-XX, which are <b>two-space drones with one payload space</b>."</blockquote>
 *
 * <p>So the extended range consumes a payload space while the frame grows. Deriving capacity
 * from frame size would make the type-III-XX inexpressible, which is why these are two fields
 * rather than one. Nothing in the current roster exercises the difference; the rule does.
 *
 * <h2>Hull boxes come from here</h2>
 * Not from the rules directly — no clause says "a two-space drone has six hull" — but from the
 * data, where it holds without exception across all fourteen drones: 0.5 -&gt; 3, 1.0 -&gt; 4,
 * 2.0 -&gt; 6. Recorded as a derivation rather than fourteen copies of a number, and pinned by
 * {@code DroneSpecDerivationTest} against the values the enum carried before this existed.
 */
public enum DroneFrame {

    /**
     * The type-VI dogfight frame. FD2.51: "There are no variants (FD2.22) of type-VI drones
     * except for speed upgrades", which is FD10.21's "only the speed can be varied" said from
     * the other end. So it is NOT modular: its warhead is fixed and nothing can be attached.
     */
    HALF_SPACE(0.5, 0, 3, false, List.of(PayloadModule.DOGFIGHT_WARHEAD)),

    /** The standard frame: one rack space, one payload space. Type-I, II and III. */
    ONE_SPACE(1.0, 1, 4, true, List.of()),

    /** The heavy frame: two rack spaces, two payload spaces. Type-IV and V. */
    TWO_SPACE(2.0, 2, 6, true, List.of());

    /** Spaces this frame occupies in a drone rack (FD3.0 counts racks in spaces). */
    public final double rackSpaces;

    /** Payload spaces available to modules (FD10.4). Zero on a frame that takes none. */
    public final int payloadSpaces;

    /** Hull boxes the drone has as a target (F2.0). */
    public final int hull;

    /**
     * Whether a player may choose this frame's payload at all.
     *
     * <p>False for the half-space frame, which carries what it carries. A module list offered to
     * a non-modular frame is a programming error rather than an illegal loadout, and
     * {@link DroneSpec} refuses it.
     */
    public final boolean modular;

    /** What a non-modular frame carries. Empty for the modular ones, which are told. */
    public final List<PayloadModule> fixedPayload;

    DroneFrame(double rackSpaces, int payloadSpaces, int hull, boolean modular,
               List<PayloadModule> fixedPayload) {
        this.rackSpaces = rackSpaces;
        this.payloadSpaces = payloadSpaces;
        this.hull = hull;
        this.modular = modular;
        this.fixedPayload = fixedPayload;
    }
}
