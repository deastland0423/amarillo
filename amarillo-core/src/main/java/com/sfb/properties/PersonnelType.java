package com.sfb.properties;

/**
 * A kind of personnel a {@link com.sfb.objects.Manifest} can hold, and how many
 * personnel "spaces" one of them occupies (J2.211): a crew unit is worth two
 * boarding parties.
 *
 * <p>This is the PERSONNEL capacity pool. Cargo (G25.13) is a separate,
 * interacting track and is not modelled here.
 */
public enum PersonnelType {
    CREW_UNIT(2),
    BOARDING_PARTY(1),
    COMMANDO(1);

    /** Personnel spaces one of these occupies in a hold (J2.211). */
    public final int spaces;

    PersonnelType(int spaces) {
        this.spaces = spaces;
    }

    /** Human-readable name, pluralised for {@code count} (e.g. "2 boarding parties"). */
    public String label(int count) {
        String base;
        switch (this) {
            case CREW_UNIT:      base = "crew unit";      break;
            case BOARDING_PARTY: base = "boarding party"; break;
            case COMMANDO:       base = "commando";       break;
            default:             base = "unit";
        }
        if (count == 1) return base;
        return base.endsWith("y") ? base.substring(0, base.length() - 1) + "ies" : base + "s";
    }
}
