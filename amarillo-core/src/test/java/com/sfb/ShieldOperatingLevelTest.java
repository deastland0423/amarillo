package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.properties.ShieldStatus;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;

/**
 * (D3.32)/(D3.33) Shields cost energy every turn, and a ship that does not pay does not have them.
 *
 * <p>Reported from a live game by the owner, 2026-11-06: "During EA I had a ship spend 0 energy on
 * shields. This should have meant that it had no shields during the next turn. But I'm seeing full
 * shields."
 *
 * <p>They were right, and the cause was that {@code Ship.shieldsStatus} was WRITE-ONLY.
 * {@code allocateEnergy} set it to Inactive correctly; nothing anywhere — damage, DTO, web, or a
 * single test — ever read it back. A field with no readers cannot be wrong on its own, which is
 * why this survived.
 *
 * <h2>The three levels</h2>
 * (D3.242) names them "off, minimum, standard", and (D3.32) prices them by size class: a size-3
 * cruiser pays 1 for minimum and 1 more for full, which is the "SHIELD COST = 1+1" printed on its
 * SSD.
 *
 * <p>(D3.33) is the one with teeth: at minimum level "only five of those boxes are operable", and
 * damage past them penetrates "even though fifteen undestroyed (but unpowered) shield boxes
 * remained". So an unpaid shield is not a destroyed shield — the boxes are still there, they are
 * just not doing anything.
 */
public class ShieldOperatingLevelTest {

    private Game game;
    private Ship ship;

    @Before
    public void setUp() {
        game = new Game();
        ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName("Lexington");
        ship.setLocation(new Location(10, 10));
        ship.setFacing(1);
        game.getShips().add(ship);
    }

    /** An allocation that pays for everything except shields. */
    private Energy paying(double shields) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(shields);
        return e;
    }

    private int shieldOne() {
        return ship.getShields().getShieldStrength(1);
    }

    // ------------------------------------------------------------------ the levels

    @Test
    public void payingTheFullCostGivesFullShields() {
        game.startTurn();
        game.submitAllocation(ship, paying(ship.getActiveShieldCost()));

        assertEquals(ShieldStatus.Active, ship.getShieldStatus());
        int before = shieldOne();
        assertEquals("all 30 boxes absorb", 0, ship.damageShield(1, 10));
        assertEquals(before - 10, shieldOne());
    }

    /**
     * Zero energy, zero protection. This is the owner's report, and the assertion that matters:
     * the damage must PENETRATE, not be absorbed by boxes nobody paid for.
     */
    @Test
    public void payingNothingMeansNoShieldAtAll() {
        game.startTurn();
        game.submitAllocation(ship, paying(0));

        assertEquals(ShieldStatus.Inactive, ship.getShieldStatus());
        int before = shieldOne();
        assertEquals("every point of it goes inside", 10, ship.damageShield(1, 10));
        assertEquals("and the boxes are untouched — unpowered, not destroyed (D3.33)",
                before, shieldOne());
    }

    /**
     * (D3.33): "If a given shield has twenty boxes, but is operated at minimum level, only five of
     * those boxes are operable. If six (or more) hits were scored on the shield in this condition,
     * the first five would destroy the minimum level shield and the remainder would penetrate."
     */
    @Test
    public void minimumShieldsStopExactlyFivePoints() {
        game.startTurn();
        game.submitAllocation(ship, paying(ship.getMinimumShieldCost()));

        assertEquals(ShieldStatus.Minimum, ship.getShieldStatus());
        int before = shieldOne();
        assertEquals("six points: five stopped, one through", 1, ship.damageShield(1, 6));
        assertEquals("and only the five operable boxes are gone", before - 5, shieldOne());
    }

    /**
     * (D3.331): "The five boxes of the minimum shield are the first to be destroyed and the last
     * to be repaired, even if the shield is at full power. Thus if the shield above was at full
     * power and took two damage points (reducing it to eighteen), and later was operated at
     * minimum power, it would only have three working boxes."
     *
     * <p>That is the rule's own worked example, with this hull's numbers: a 30-box shield that has
     * taken two hits has three working boxes at minimum, not five.
     */
    @Test
    public void previousDamageEatsIntoTheMinimumShieldFirst() {
        game.startTurn();
        game.submitAllocation(ship, paying(ship.getActiveShieldCost()));
        ship.damageShield(1, 2);                      // at full power, two points

        game.startTurn();
        game.submitAllocation(ship, paying(ship.getMinimumShieldCost()));

        assertEquals("three working boxes, not five (D3.331)", 1, ship.damageShield(1, 4));
    }

    // ------------------------------------------------------------------ what must NOT change

    /**
     * A unit that never files an allocation must keep its shields. Bases, ground bases and
     * anything auto-allocated would otherwise be stripped by this change, which would be a far
     * worse bug than the one being fixed.
     */
    @Test
    public void aUnitThatNeverAllocatesKeepsItsShields() {
        Ship fresh = new Ship();
        fresh.init(FederationShips.getFedCa());
        fresh.setName("Unallocated");

        assertEquals("the default is working shields", ShieldStatus.Active, fresh.getShieldStatus());
        assertEquals("and they absorb", 0, fresh.damageShield(1, 10));
    }

    /** A ship that pays again next turn gets its shields back. */
    @Test
    public void payingAgainRestoresThem() {
        game.startTurn();
        game.submitAllocation(ship, paying(0));
        assertEquals(ShieldStatus.Inactive, ship.getShieldStatus());

        game.startTurn();
        game.submitAllocation(ship, paying(ship.getActiveShieldCost()));

        assertEquals(ShieldStatus.Active, ship.getShieldStatus());
        assertEquals("absorbing once more", 0, ship.damageShield(1, 10));
    }
}
