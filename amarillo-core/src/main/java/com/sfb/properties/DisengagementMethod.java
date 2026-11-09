package com.sfb.properties;

/**
 * How a unit may leave the battle (C7.0).
 *
 * <p>"There are four means of disengagement: acceleration, separation, sublight evasion, and
 * automatic." A scenario may permit any subset of them per side — SH47.41's "the Kzinti can only
 * disengage from the 01xx map edge" is a restriction of this kind, and so is a patrol scenario
 * that means a ship to run for a border rather than simply outrun its pursuer.
 *
 * <p>Only the three that are BUILT appear here, deliberately. A scenario naming a method this
 * enum does not have fails to load — {@code ScenarioSpec}'s mapper is a plain {@code
 * ObjectMapper}, so an unknown value throws rather than deserializing to null. That is the
 * intended behaviour: a scenario author who writes a rule the engine cannot enforce should be
 * told at once, not have it silently permitted.
 *
 * <ul>
 *   <li>{@link #ACCELERATION} — C7.1. Run at the maximum practical speed for a whole turn and
 *       still hold half the original warp power. Can be denied by the HULL as well as by the
 *       scenario: a plain freighter's SSD says outright that it cannot (R1.7).</li>
 *   <li>{@link #MAP_EDGE} — crossing off the edge of a fixed map. The rulebook files this under
 *       C7.4 ("See (C7.4) for fixed map disengagement") rather than giving it a clause of its
 *       own, but it is the one a player uses most, so it is named for what it is.</li>
 *   <li>{@link #SEPARATION} — C7.2. Shed a section and withdraw it.</li>
 * </ul>
 *
 * <p>Not here, and why: <b>sublight evasion</b> (C7.3) is unbuilt, and <b>automatic</b> (C7.4)
 * is not a player action — a scenario that designates a ship to leave takes it off the board
 * itself, so there is nothing for a permission to gate.
 */
public enum DisengagementMethod {
    ACCELERATION,
    MAP_EDGE,
    SEPARATION;

    /** What a refusal should call this, in a player-facing message. */
    public String label() {
        switch (this) {
            case ACCELERATION: return "by acceleration (C7.1)";
            case MAP_EDGE:     return "by leaving the map";
            case SEPARATION:   return "by separation (C7.2)";
        }
        return name();
    }
}
