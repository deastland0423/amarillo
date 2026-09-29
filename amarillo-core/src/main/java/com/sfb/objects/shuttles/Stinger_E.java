package com.sfb.objects.shuttles;

import java.util.List;

import com.sfb.properties.TurnMode;
import com.sfb.utilities.ArcUtils;
import com.sfb.weapons.PhaserG;

/**
 * Hydran Stinger-E electronic warfare fighter (R1.F7).
 * Year available: Y170. Speed 15. BPV 12. Hull 10, crippled at 7 damage.
 * Weapons: 1× Ph-G (FA). Two permanently-fitted EW pods (J4.96) where a Stinger-2 carries
 * its fusion beams.
 * <p>
 * A purpose-built airframe, not a conversion. R1.F7: the Hydrans, ISC and Tholians "built a
 * specific EW fighter which was the only type they used" and do not convert a standard
 * fighter to an EW type — which is the opposite of the Kzinti practice, where a {@link
 * Haas_E} is a HAAS whose two drone rails carry pods instead of drones (J4.962).
 * <p>
 * That difference is why the pods here are FIXED ({@link Fighter#setFixedEwPods}) rather
 * than rail-mounted. A Hydran fighter has no drone rails at all, so J4.962's "an EWP
 * replaces one drone" has nothing to work on; what was given up was the two fusion beams,
 * at the factory. The pods cost no speed either — the Stinger-E prints 15, the same as the
 * Stinger-2 — which rules out J4.9621's "extra" pods, whose whole signature is a point of
 * speed and dogfight rating apiece.
 * <p>
 * So it trades its entire offensive armament bar the phaser for four points of EW it can
 * hand to every fighter in its squadron (J4.93, J4.965). A crippled one loses both pods
 * (J1.3322) and is left with a Ph-3 and J4.47's two-and-two.
 */
public class Stinger_E extends Fighter {

    public Stinger_E() {
        setCatalogType("stinger_e");
        setTurnMode(TurnMode.Shuttle);
        setMaxSpeed(15);
        setCurrentSpeed(15);
        setHull(10);
        setCrippledHull(7);
        setBpv(12);
        // J4.43/F3.222: an EWF is a two-seat fighter, and the back seat is what lets it
        // accept transferred seeking weapons from its squadron (J4.221) and carry four pods
        // rather than two (J4.964). It has no rails of its own to launch any from.
        setTwoSeater(true);

        PhaserG ph = new PhaserG();
        ph.setDesignator("1");
        ph.setArcs(ArcUtils.FA);
        ph.setArcsFromJSON(List.of("FA"));
        getWeapons().addWeapon(ph);

        // Where a Stinger-2's two FighterFusions sit. Permanent: the SSD shows them as part
        // of the fighter, and they cannot be added or removed by a deck crew.
        setFixedEwPods(2);
    }
}
