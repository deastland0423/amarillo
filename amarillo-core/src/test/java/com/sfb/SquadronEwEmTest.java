package com.sfb;

import static org.junit.Assert.*;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.Squadron;
import com.sfb.objects.Unit;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.properties.Location;
import com.sfb.systemgroups.Energy;

/**
 * (C10.522) Lending EW while using Erratic Maneuvers, and receiving it.
 *
 * <h2>The rule, both sentences</h2>
 * "A unit using EM cannot lend EW points. A unit can receive lent EW while performing EM."
 *
 * <p>It is the second sentence that makes this worth a test class. The clause is a one-way ban,
 * and the obvious implementation — treat EM as "no EW dealings" — would break a legal and useful
 * play: a fighter jinking under EM while its squadron-mate feeds it ECM. C10.523 narrows the
 * first sentence for this craft in particular: an EW fighter (R1.F7) using EM "cannot operate
 * its electronic warfare (scout) capabilities", though it may still use active fire control.
 *
 * <p>Both lending paths need the check, because they share no code: {@code refusalReason} for an
 * EW fighter lending to its squadron-mates (J4.92), and {@code carrierRefusal} for a carrier
 * lending to its squadron (J4.93).
 *
 * <p>The EM announcement is brought into force here by calling {@code applyEmAnnouncement}
 * directly, which is exactly what Stage 6E does (C10.311) — the same shortcut
 * {@code CarrierEwLendingTest} takes with {@code startTurn}, and for the same reason: these
 * assertions are about the lending rule, not about the phase machine that schedules it.
 */
public class SquadronEwEmTest {

    private Game game;
    private Ship carrier;
    private Squadron squadron;
    private Fighter ewf;
    private Fighter wingman;

    @Before
    public void setUp() {
        game = new Game();
        Player kzinti = new Player();
        kzinti.setTeamName("Kzinti");

        carrier = new Ship();
        carrier.init(com.sfb.samples.KzintiShips.getKzinBC());
        carrier.setName("KHS Longsword");
        carrier.setLocation(new Location(10, 10));
        carrier.setFacing(1);
        carrier.setOwner(kzinti);
        carrier.setCarrierClass(com.sfb.properties.CarrierClass.CAPABLE);
        game.getShips().add(carrier);

        squadron = new Squadron("Gold", carrier);
        carrier.getShuttles().getSquadrons().add(squadron);

        ewf = CataloguedFighter.of("haas_e");
        ewf.setName("HAAS-E");
        ewf.setOwner(kzinti);
        ewf.setLocation(new Location(10, 12));
        ewf.setFacing(13);

        wingman = CataloguedFighter.of("haas");
        wingman.setName("HAAS-1");
        wingman.setOwner(kzinti);
        wingman.setLocation(new Location(10, 13));
        wingman.setFacing(13);

        game.getActiveShuttles().add(ewf);
        game.getActiveShuttles().add(wingman);
        assertNull(squadron.add(ewf));
        assertNull(squadron.add(wingman));

        game.startTurn();
        sweepLockOns();
    }

    // ---------------------------------------------------------------- the ban

    /** Sentence one: an EW fighter under EM lends nothing. */
    @Test
    public void anEwFighterUsingEmCannotLend() {
        assertNull(game.designateLentEwSource(wingman, ewf));
        assertFalse("fixture: it is lending before EM", game.lentEwTo(wingman).isNothing());

        intoEm(ewf);

        assertTrue("C10.522: a unit using EM cannot lend EW points",
                game.lentEwTo(wingman).isNothing());
    }

    /** And the readout says which clause, so a player can look it up and argue with it. */
    @Test
    public void theRefusalNamesTheClause() {
        assertNull(game.designateLentEwSource(wingman, ewf));
        intoEm(ewf);

        String why = game.ewLendRefusalFor(wingman);

        assertNotNull(why);
        assertTrue("expected C10.522 in: " + why, why.contains("C10.522"));
        assertTrue("and the reason, not just the citation: " + why,
                why.contains("Erratic Maneuvers"));
    }

    /** The same clause on the carrier's side (J4.93), which is a separate code path. */
    @Test
    public void aCarrierUsingEmCannotLendToItsSquadron() {
        generateCarrierEw(3, 3);
        wingman.addLockOn(carrier);
        assertNull(game.designateLentEwSource(wingman, carrier));
        assertFalse("fixture: the carrier is lending before EM", game.lentEwTo(wingman).isNothing());

        intoEm(carrier);

        assertTrue("C10.522 binds a carrier exactly as it binds a fighter",
                game.lentEwTo(wingman).isNothing());
    }

    // ---------------------------------------------------------------- the exception

    /**
     * Sentence two, and the one an over-broad guard would break: "A unit CAN receive lent EW
     * while performing EM." The recipient jinks; the loan still arrives.
     */
    @Test
    public void aUnitUsingEmStillReceivesLentEw() {
        assertNull(game.designateLentEwSource(wingman, ewf));

        intoEm(wingman);

        assertFalse("C10.522 second sentence — receiving is allowed under EM",
                game.lentEwTo(wingman).isNothing());
        assertNull("and nothing is refused", game.ewLendRefusalFor(wingman));
    }

    /** Both at once: the lender's EM is what stops it, never the recipient's. */
    @Test
    public void itIsTheLendersEmThatMattersNotTheRecipients() {
        assertNull(game.designateLentEwSource(wingman, ewf));
        intoEm(wingman);
        assertFalse("still arriving with only the recipient jinking",
                game.lentEwTo(wingman).isNothing());

        intoEm(ewf);

        assertTrue("now the LENDER is under EM, and that is the one that bites",
                game.lentEwTo(wingman).isNothing());
    }

    /** Ceasing EM restores the loan — the ban is a state, not a forfeit. */
    @Test
    public void ceasingEmRestoresTheLoan() {
        assertNull(game.designateLentEwSource(wingman, ewf));
        intoEm(ewf);
        assertTrue(game.lentEwTo(wingman).isNothing());

        int imp = game.getAbsoluteImpulse();
        assertTrue(game.announceErraticManeuvers(ewf, false).isSuccess());
        assertTrue(ewf.applyEmAnnouncement(imp));
        assertFalse("fixture: EM is off again", ewf.isUsingEm());

        assertFalse("the points come back (C10.522 bans the lending, not the fighter)",
                game.lentEwTo(wingman).isNothing());
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Put a unit into EM for real: pay for it, announce it (C10.3), and bring it into force the
     * way Stage 6E does (C10.311). Going through {@code announceErraticManeuvers} rather than
     * setting a flag means the eligibility rules are exercised too — a craft must have
     * committed its point of speed (C10.13), a ship must have bought the energy (C10.11).
     */
    private void intoEm(Unit unit) {
        if (unit instanceof Ship ship) {
            Energy e = new Energy();
            e.setLifeSupport(ship.getLifeSupportCost());
            e.setErraticManuvers(ship.getPerformanceData().getErraticCost());
            // Keep whatever squadron pool this turn already declared; re-allocating without it
            // would zero J4.931's pool and the test would pass for the wrong reason.
            if (squadron.hasCarrierEw())
                e.setSquadronEw(declaredPool(3, 3));
            ship.allocateEnergy(e);
            ship.startTurn();
            // C10.11's "did it pay" flag is set by Game.submitAllocation, NOT by
            // Ship.startTurn — and this fixture takes the direct allocateEnergy/startTurn
            // route on purpose, because going through submitAllocation re-runs the whole
            // turn-start queue and zeroes J4.931's squadron pool. So the flag is set by hand.
            ship.setPaidForEm(true);
            sweepLockOns();
        } else if (unit instanceof com.sfb.objects.shuttles.Shuttle craft) {
            assertTrue(game.commitShuttleEmSpeed(craft).isSuccess());
        }

        int imp = game.getAbsoluteImpulse();
        assertTrue(game.announceErraticManeuvers(unit, true).isSuccess());
        assertTrue("the announcement comes into force at the end of the impulse (C10.311)",
                unit.applyEmAnnouncement(imp));
        assertTrue(unit.getName() + " should be using EM", unit.isUsingEm());
    }

    private Map<String, int[]> declaredPool(int ecm, int eccm) {
        Map<String, int[]> declared = new LinkedHashMap<>();
        declared.put(squadron.getName(), new int[] { ecm, eccm });
        return declared;
    }

    /** J4.931's pool is filled by Ship.startTurn() from the stored allocation form. */
    private void generateCarrierEw(int ecm, int eccm) {
        Energy e = new Energy();
        e.setLifeSupport(carrier.getLifeSupportCost());
        e.setSquadronEw(declaredPool(ecm, eccm));
        carrier.allocateEnergy(e);
        carrier.startTurn();
        sweepLockOns();
        assertTrue("fixture: the carrier generated a pool (J4.931)", squadron.hasCarrierEw());
    }

    private void sweepLockOns() {
        for (com.sfb.objects.shuttles.Shuttle craft : game.getActiveShuttles())
            if (craft instanceof Fighter f)
                game.acquireFighterLockOns(f);
    }
}
