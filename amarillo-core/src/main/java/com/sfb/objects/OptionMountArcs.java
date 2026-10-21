package com.sfb.objects;

import java.util.List;

/**
 * R8.R3: the firing arc of a weapon in an Orion option mount.
 *
 * <h2>The arc belongs to the WEAPON, not the mount</h2>
 * Every other hull in the game prints a weapon's arc beside it, because the weapon never moves.
 * An option mount is the exception: what sits in it is bought, so the sheet can only draw the
 * typical case. R8.R3 states the real rule, and it is keyed on the weapon:
 *
 * <pre>
 *   CENTERLINE   everything ............ FA
 *                swivel plasma torps ... FP
 *                drones and ADDs ....... FULL
 *
 *   WING         Photon ................ FA
 *                Disruptor, Fusion ..... FA *or* L+LF (left) / RF+R (right) — PLAYER'S CHOICE
 *                Phaser, Plasma-D ...... LS / RS
 *                Plasma-F .............. LP / RP
 *                Drone, ADD ............ FULL
 *                Hellbore, PPD ......... not allowed
 * </pre>
 *
 * <p>Until this existed, {@code OptionMountLoadout.equip} gave the weapon the mount's printed
 * arc, so a drone rack in an Orion CA's wing mount fired LS instead of FULL and a Plasma-F fired
 * LS instead of LP. The printed arc is still how we know WHERE a mount is — a left wing mount
 * prints a left-side arc — but it is not what the weapon in it fires through.
 *
 * <h2>The player's choice</h2>
 * G15.412 points at this rule, and the rule is explicit that the wing disruptor/fusion arc is
 * "depending on the taste of the Orion player" — a ship may carry a fusion beam bearing FA in
 * one wing and another bearing L+LF in the other. So it is a purchase-time decision like the
 * option itself (G15.415), not something derivable, and {@link WingArc} is how it is said.
 */
public final class OptionMountArcs {

    private OptionMountArcs() {}

    /** R8.R3's choice for a disruptor or fusion beam in a WING mount. Ignored elsewhere. */
    public enum WingArc {
        /** FA — bearing forward with the rest of the battery. */
        FORWARD,
        /** L + LF in a left wing mount, RF + R in a right one — bearing outboard. */
        OUTBOARD;

        /** FORWARD when nothing was said: the arc R8.R3 lists first, and the one every other
         *  option mount on the ship already bears, so an undeclared choice is the quiet one. */
        public static WingArc orDefault(WingArc choice) {
            return choice != null ? choice : FORWARD;
        }
    }

    /**
     * The arcs a weapon fires through in this mount, as arc labels.
     *
     * @param choice R8.R3's wing disruptor/fusion choice; null means {@link WingArc#FORWARD}.
     */
    public static List<String> arcsFor(OptionCatalogEntry entry, OptionMount mount, WingArc choice) {
        boolean wing = mount.getPosition() == OptionMount.Position.WING;
        String kind = entry.weaponSpec != null ? entry.weaponSpec.type : null;

        // Drones and ADDs bear FULL wherever they sit — the one row that does not care about
        // position, because a rack throws a seeker that flies off on its own.
        if ("DroneRack".equals(kind) || "ADD".equals(kind))
            return List.of("FULL");

        if (!wing)
            return List.of(isSwivelPlasma(entry) ? "FP" : "FA");

        boolean left = isLeftMount(mount);
        if ("Disruptor".equals(kind) || "Fusion".equals(kind))
            return WingArc.orDefault(choice) == WingArc.FORWARD
                    ? List.of("FA")
                    : left ? List.of("L", "LF") : List.of("RF", "R");
        if ("PlasmaLauncher".equals(kind) && entry.weaponSpec != null
                && "F".equals(entry.weaponSpec.plasmaType))
            return List.of(left ? "LP" : "RP");
        if ("Photon".equals(kind))
            return List.of("FA");
        // Phaser and Plasma-D, and the fallback for anything R8.R3 does not name: the side arc
        // the sheet prints, which is what every such mount has always drawn.
        return List.of(left ? "LS" : "RS");
    }

    /** True if this wing mount is the LEFT one, read off the arc its sheet prints. */
    static boolean isLeftMount(OptionMount mount) {
        for (String arc : mount.getArcs()) {
            String a = arc.toUpperCase();
            if (a.startsWith("L")) return true;
            if (a.startsWith("R")) return false;
        }
        return true;
    }

    /**
     * A swivel-mount plasma torpedo, which R8.R3 gives FP on the centerline where a fixed one
     * gets FA. Stated in the catalogue rather than read out of the option's NAME, so the fact
     * lives where the rest of the option's facts do.
     */
    static boolean isSwivelPlasma(OptionCatalogEntry entry) {
        return entry.swivel && entry.weaponSpec != null
                && "PlasmaLauncher".equals(entry.weaponSpec.type);
    }
}
