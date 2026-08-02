package com.sfb.objects;

import java.util.ArrayList;
import java.util.List;

/**
 * The Orion cartel fleet-quota (G15.44): a force-level check over every option
 * mount on an Orion fleet. A cartel has unlimited access to weapons from its
 * home empire, but only a fraction of the fleet's option mounts may draw from
 * operating-zone or outside empires.
 *
 * <p>Rules (per current ruling): the caps are a percentage of <em>all</em>
 * option mounts in the fleet (filled or empty), rounded to nearest — 20% for
 * operating-zone weapons, 10% for outside weapons — as two <em>independent</em>
 * pools. Universal weapons (no origin empire) and home-empire weapons are
 * unlimited and never counted.
 */
public final class CartelQuota {

    private CartelQuota() {}

    public static final double OPERATING_FRACTION = 0.20;
    public static final double OUTSIDE_FRACTION   = 0.10;

    public static final class Result {
        public boolean withinQuota;
        public int totalMounts;
        public int operatingCap;
        public int outsideCap;
        public int operatingUsed;
        public int outsideUsed;
        public final List<String> violations = new ArrayList<>();
    }

    /**
     * Evaluate the fleet's option-mount choices against a cartel's access tiers.
     * A null cartel means no cartel is declared → no quota is enforced.
     *
     * @param fleet   all ships in the Orion force (their option mounts are pooled)
     * @param cartel  the cartel the fleet belongs to (null = unconstrained)
     * @param catalog the option catalog, for each equipped option's origin empires
     */
    public static Result evaluate(List<Ship> fleet, OrionCartel cartel, OptionMountCatalog catalog) {
        int totalMounts = 0;
        List<String> selected = new ArrayList<>();
        for (Ship ship : fleet) {
            for (OptionMount mount : ship.getOptionMounts()) {
                totalMounts++;
                if (!mount.isEmpty() && mount.getOptionName() != null) {
                    selected.add(mount.getOptionName());
                }
            }
        }
        return evaluate(totalMounts, selected, cartel, catalog);
    }

    /**
     * Selection-based form: evaluate directly from the fleet's total option-mount
     * count and the list of chosen option names — used at COI submission, before
     * ships are equipped.
     */
    public static Result evaluate(int totalMounts, List<String> selectedOptionNames,
                                  OrionCartel cartel, OptionMountCatalog catalog) {
        Result r = new Result();
        r.totalMounts = totalMounts;

        if (cartel != null) {
            for (String name : selectedOptionNames) {
                if (name == null) {
                    continue;
                }
                OptionCatalogEntry entry = catalog.get(name);
                if (entry == null || entry.isUniversal()) {
                    continue; // universal weapons are unlimited
                }
                switch (cartel.accessForAny(entry.empires)) {
                    case OPERATING -> r.operatingUsed++;
                    case OUTSIDE   -> r.outsideUsed++;
                    case HOME      -> { /* unlimited */ }
                }
            }
        }

        r.operatingCap = (int) Math.round(totalMounts * OPERATING_FRACTION);
        r.outsideCap   = (int) Math.round(totalMounts * OUTSIDE_FRACTION);

        if (cartel != null) {
            if (r.operatingUsed > r.operatingCap) {
                r.violations.add(cartel.name + ": " + r.operatingUsed + " operating-zone option mounts exceed the limit of "
                        + r.operatingCap + " (20% of " + totalMounts + ")");
            }
            if (r.outsideUsed > r.outsideCap) {
                r.violations.add(cartel.name + ": " + r.outsideUsed + " outside-empire option mounts exceed the limit of "
                        + r.outsideCap + " (10% of " + totalMounts + ")");
            }
        }
        r.withinQuota = r.violations.isEmpty();
        return r;
    }
}
