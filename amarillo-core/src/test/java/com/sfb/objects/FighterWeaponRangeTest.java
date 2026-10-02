package com.sfb.objects;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.Weapon;

/**
 * J1.31: "No shuttle (including fighters) can fire a direct-fire weapon to a range of more than
 * fifteen hexes. (Some weapons have shorter ranges specified, for example, photons are limited
 * to Range 12, disruptors to Range 10.) This range limit is not adjusted by pilot status."
 * <p>
 * The cap is applied where the weapon is MOUNTED, not inside the weapon, because the phaser a
 * fighter carries is the same class a cruiser carries. That was a live defect until the Klingon
 * Z-1 arrived: it is the first fighter in the data to carry a phaser-2, and a phaser-2 reaches
 * range 50. Every fighter before it carried a phaser-3 or phaser-G (both 15) or a heavy weapon
 * that caps itself at 10, so nothing had ever exercised the rule.
 * <p>
 * Note the rule lives in J1.3, the SHUTTLE combat section, not in J4 with the fighter rules —
 * which is why it was easy to miss while looking for it under fighters.
 */
public class FighterWeaponRangeTest {

    @Before
    public void loadCatalogue() throws Exception {
        ShuttleCatalog.load(new File("../data/shuttles/shuttles.json"));
    }

    /** No fighter in the catalogue may carry a weapon reaching past fifteen hexes. */
    @Test
    public void noFighterWeaponReachesBeyondFifteenHexes() {
        List<String> problems = new ArrayList<>();

        for (ShuttleCatalog.Entry entry : ShuttleCatalog.all()) {
            if (!entry.isFighter())
                continue;
            Fighter f = CataloguedFighter.of(entry.type);
            for (Weapon w : f.getWeapons().fetchAllWeapons()) {
                if (w instanceof DroneRail)
                    continue;                      // a rail launches, it does not fire
                if (w.getMaxRange() > CataloguedFighter.SHUTTLE_MAX_RANGE)
                    problems.add(entry.type + "'s " + w.getName() + " reaches "
                            + w.getMaxRange());
            }
        }

        assertTrue("J1.31 caps a shuttle-mounted direct-fire weapon at "
                + CataloguedFighter.SHUTTLE_MAX_RANGE + " hexes:\n  "
                + String.join("\n  ", problems), problems.isEmpty());
    }

    /**
     * The Klingon Z-1 is the case that exposed this: a phaser-2 on a ship reaches 50, and on a
     * fighter it must reach fifteen. Asserted on the specific hull so the regression has a name.
     */
    @Test
    public void theZ1sPhaser2IsCutToFifteen() {
        Fighter z1 = CataloguedFighter.of("z1");

        Weapon p2 = z1.getWeapons().fetchAllWeapons().stream()
                .filter(w -> "Phaser2".equals(w.getType()))
                .findFirst().orElseThrow(() -> new AssertionError("the Z-1 carries a phaser-2"));

        assertEquals("J1.31, not the phaser's own 50", 15, p2.getMaxRange());
    }

    /**
     * A CAP, not a setting: the rule's own examples are weapons that reach LESS far, and they
     * must keep their shorter figures. A fighter disruptor is Range 10 and stays Range 10.
     */
    @Test
    public void aShorterRangedWeaponKeepsItsOwnLimit() {
        Fighter das = CataloguedFighter.of("das");

        Weapon disruptor = das.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof com.sfb.weapons.FighterDisruptor)
                .findFirst().orElseThrow(() -> new AssertionError("the DAS carries a disruptor"));

        assertEquals("the disruptor's Range 10 survives a fifteen-hex cap",
                10, disruptor.getMaxRange());
    }

    /** And the cap itself refuses to extend anything, which is why it is not a setter. */
    @Test
    public void cappingNeverLengthensAWeaponsReach() {
        Fighter das = CataloguedFighter.of("das");
        Weapon disruptor = das.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof com.sfb.weapons.FighterDisruptor)
                .findFirst().orElseThrow(AssertionError::new);

        disruptor.capMaxRange(30);

        assertEquals("a cap above the weapon's own reach changes nothing",
                10, disruptor.getMaxRange());
    }
}
