package com.sfb.objects.shuttles;

import com.sfb.objects.ShipSpec;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.WeaponFactory;
import com.sfb.properties.TurnMode;
import com.sfb.weapons.Weapon;

/**
 * A fighter built from its row in the shuttle catalogue (J4.4).
 * <p>
 * This replaces a class per fighter. There were sixteen of them — one each for the AAS, AAS-E,
 * HAAS, HAAS-E, TAAS, TAAS-E, TADS, TADS-E, TADSC, TADSC-E, DAS, DASC, Stinger-1, Stinger-2,
 * Stinger-H and Stinger-E — and between them they contained no behaviour at all: fourteen were a
 * constructor and nothing else,
 * and the other two overrode the same pair of J4.242 methods identically. Every one of them also
 * repeated its speed, hull, crippled threshold and BPV, which the catalogue already held, and the
 * only thing keeping the two copies honest was a test. That is how three EW variants came to carry
 * their base fighter's BPV.
 * <p>
 * So a fighter is a ROW now, and the cost of a new faction's suite is a data change rather than a
 * dozen files in three places. Weapon recipes go through {@link WeaponFactory}, the very same path
 * a cruiser's phasers take, so a fighter's armament is declared the way every other ship's is.
 *
 * <h2>What is still a class, and why</h2>
 * {@link Shuttle} and {@link Fighter} hold all the behaviour, and the craft that genuinely differ
 * keep their own types: {@link ScatterPack}, {@link SuicideShuttle} and {@link WildWeaselShuttle}
 * are seekers rather than fighters and override a dozen methods each, while
 * {@link AdminShuttle}, {@link GASShuttle} and {@link HTSShuttle} are not fighters at all.
 * <p>
 * A future fighter with real behaviour of its own is free to subclass {@link Fighter} as before —
 * nothing here forbids it. The point is only that carrying a different gun is not behaviour.
 */
public class CataloguedFighter extends Fighter {

    /**
     * Build the fighter a catalogue key names — "aas", "stinger1", "tadsc_e".
     * <p>
     * The replacement for a constructor call on one of the old per-fighter classes. {@link com.sfb.systemgroups.ShuttleBay#buildShuttle}
     * is the path the game itself uses, since it also has to handle the craft that are not
     * fighters; this is the direct route for a caller that knows it wants one.
     *
     * @throws IllegalArgumentException if the key is not a catalogued fighter, which is a
     *         programming error rather than a data condition — a typo must not quietly produce
     *         something else, which is exactly how "taas" once became an admin shuttle
     */
    public static CataloguedFighter of(String catalogType) {
        ShuttleCatalog.Entry entry = ShuttleCatalog.get(catalogType);
        if (entry == null)
            throw new IllegalArgumentException("no catalogued craft called '" + catalogType + "'");
        if (!entry.isFighter())
            throw new IllegalArgumentException(catalogType + " is catalogued as a "
                    + entry.kind + ", not a fighter");
        return new CataloguedFighter(entry);
    }

    /**
     * @param entry the catalogue row; its {@link ShuttleCatalog.Loadout} supplies the armament
     */
    public CataloguedFighter(ShuttleCatalog.Entry entry) {
        setCatalogType(entry.type);
        setTurnMode(TurnMode.Shuttle);
        setMaxSpeed(entry.speed);
        setCurrentSpeed(entry.speed);
        setHull(entry.hull);
        setCrippledHull(entry.crippled);
        setBpv(entry.bpv);

        ShuttleCatalog.Loadout load = entry.loadout;
        setTwoSeater(load.twoSeater());
        setMayLaunchAtDifferentTargets(load.mayLaunchAtDifferentTargets());
        setMayLaunchTwoStandardDrones(load.mayLaunchTwoStandardDrones());
        if (load.chaffPacks() > 0)
            setChaffPacks(load.chaffPacks());

        for (ShipSpec.WeaponSpec ws : load.weapons()) {
            Weapon w = WeaponFactory.build(ws, ws.arcs);
            if (w != null)
                getWeapons().addWeapon(w);
        }

        // Pods AFTER the weapons, because fitting one to a rail needs the rails to exist
        // (J4.962: a pod displaces a drone, so it takes a rail's place). Fixed pods are the
        // Hydran arrangement — built in, no rail involved, nothing to displace (J4.964).
        if (load.fixedEwPods() > 0)
            setFixedEwPods(load.fixedEwPods());
        if (load.ewPods() > 0)
            fitEwPods(load.ewPods());
    }
}
