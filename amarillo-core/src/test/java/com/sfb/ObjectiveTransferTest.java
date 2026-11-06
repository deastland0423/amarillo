package com.sfb;

import com.sfb.Game.ActionResult;
import com.sfb.objects.Objective;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.properties.RetrievalMethod;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Handing an objective from one ship to another (G25.2), and what the rules say you may
 * NOT do with it.
 *
 * <p>Three findings from (G25.0) shape all of this, and none of them were guessed:
 * <ul>
 *   <li>(G25.211) a transporter moves ten cargo spaces and "two transporters cannot ever be
 *       used to move a single object of any size" — so a big enough item can never be
 *       beamed, no matter how much energy or how many transporters a ship has;</li>
 *   <li>(G25.214) cargo cannot be dropped into space and still exist, except where "a
 *       special scenario rule might provide a specific type of item that could be left in
 *       space for a time" — so jettison defaults to REFUSAL and is a per-item grant;</li>
 *   <li>a transfer is one transporter operation read from the other end, so it is the
 *       existing retrieval path with a carrier as the source, not a parallel mechanism.</li>
 * </ul>
 *
 * <p>The shield rule is the interesting half. The receiving ship auto-lowers its own facing
 * shield (G8) because it owns it, but it cannot lower the carrier's — so the carrier's must
 * already be down. That is the consent handshake, expressed with a move the carrier's player
 * already has, and it falls out of the same rule that a prize can be stolen off an enemy who
 * has dropped a shield.
 */
public class ObjectiveTransferTest {

    private Game game;
    private Ship carrier;
    private Ship receiver;
    private Ship raider;

    @Before
    public void setUp() {
        game = new Game();
        carrier  = fed("USS Carrier",  10, 10);
        receiver = fed("USS Receiver", 11, 10);
        raider   = new Ship();
        raider.init(KlingonShips.getD7());
        raider.setName("IKV Raider");
        raider.setLocation(new Location(12, 10));
        raider.setFacing(1);
        raider.setSpeedPreviousTurn(31);
        raider.setSpeedTwoTurnsAgo(31);
        game.getShips().add(raider);
    }

    private Ship fed(String name, int col, int row) {
        Ship s = new Ship();
        s.init(FederationShips.getFedCa());
        s.setName(name);
        s.setLocation(new Location(col, row));
        s.setFacing(1);
        s.setSpeedPreviousTurn(31);
        s.setSpeedTwoTurnsAgo(31);
        game.getShips().add(s);
        return s;
    }

    /** An objective already aboard {@code holder}, with the given permitted methods. */
    private Objective aboard(String name, Ship holder, RetrievalMethod... methods) {
        Objective o = new Objective(name, 0, 0);
        for (RetrievalMethod m : methods)
            o.getAllowedRetrieval().add(m);
        o.setCarrier(holder);
        o.setLocation(null);
        game.addObjective(o);
        return o;
    }

    private void toActivityPhase() {
        game.startTurn();
        for (Ship s : game.getShips()) {
            Energy e = new Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            e.setActivateShields(s.getActiveShieldCost());
            e.setWarpMovement(0.0);
            game.submitAllocation(s, e);
        }
        for (int guard = 0; guard < 20 && game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY; guard++)
            game.advancePhase();
        assertEquals(Game.ImpulsePhase.ACTIVITY, game.getCurrentPhase());
        for (Ship s : game.getShips()) {
            s.setActiveFireControl(true);
            s.getTransporters().bankEnergy(2.0);
        }
    }

    /** Drop the shield {@code holder} presents to {@code toward} — the carrier's half of a handoff. */
    private void openShieldToward(Ship holder, Ship toward) {
        int shield = game.getShieldNumber(toward, holder);
        assertTrue("fixture: " + holder.getName() + " should be able to lower shield #" + shield,
                holder.getShields().lowerShield(shield));
    }

    // -------------------------------------------------------------------------
    // The handoff
    // -------------------------------------------------------------------------

    @Test
    public void transfer_movesTheObjectiveToTheReceiver() {
        Objective kaleen = aboard("Vak Kaleen", carrier, RetrievalMethod.TRANSPORTER);
        toActivityPhase();
        openShieldToward(carrier, receiver);

        ActionResult r = game.pickUpObjective(receiver, "Vak Kaleen", RetrievalMethod.TRANSPORTER);

        assertTrue(r.getMessage(), r.isSuccess());
        assertSame(receiver, kaleen.getCarrier());
        assertTrue("the log should name where it came from", r.getMessage().contains("USS Carrier"));
    }

    /**
     * The transferred objective must not keep a hex of its own. Before this change the
     * transporter path set the carrier and left the old free location in place, which
     * {@code getEffectiveLocation} hid — right up until something asked for the raw one.
     */
    @Test
    public void transfer_leavesNoStaleHexBehind() {
        Objective kaleen = aboard("Vak Kaleen", carrier, RetrievalMethod.TRANSPORTER);
        kaleen.setLocation(new Location(3, 3));   // as a free pickup would have left it
        toActivityPhase();
        openShieldToward(carrier, receiver);

        assertTrue(game.pickUpObjective(receiver, "Vak Kaleen", RetrievalMethod.TRANSPORTER).isSuccess());

        assertNull("a carried objective has no hex of its own", kaleen.getLocation());
        assertEquals(receiver.getLocation(), kaleen.getEffectiveLocation());
    }

    /** No consent, no transfer: the carrier's own shield is not the receiver's to lower. */
    @Test
    public void transfer_refusedWhileTheCarrierKeepsItsShieldUp() {
        Objective kaleen = aboard("Vak Kaleen", carrier, RetrievalMethod.TRANSPORTER);
        toActivityPhase();

        ActionResult r = game.pickUpObjective(receiver, "Vak Kaleen", RetrievalMethod.TRANSPORTER);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("must lower that shield"));
        assertSame("still aboard the carrier", carrier, kaleen.getCarrier());
    }

    /** The same rule, used offensively: an enemy with a shield down can be robbed. */
    @Test
    public void transfer_canStealFromAnEnemyWhoseShieldIsDown() {
        Objective prize = aboard("Stasis Box", raider, RetrievalMethod.TRANSPORTER);
        toActivityPhase();
        openShieldToward(raider, receiver);

        ActionResult r = game.pickUpObjective(receiver, "Stasis Box", RetrievalMethod.TRANSPORTER);

        assertTrue(r.getMessage(), r.isSuccess());
        assertSame(receiver, prize.getCarrier());
    }

    @Test
    public void transfer_refusedWhenTheReceiverAlreadyHasIt() {
        aboard("Vak Kaleen", receiver, RetrievalMethod.TRANSPORTER);
        toActivityPhase();

        ActionResult r = game.pickUpObjective(receiver, "Vak Kaleen", RetrievalMethod.TRANSPORTER);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("already aboard"));
    }

    @Test
    public void transfer_refusedBeyondTransporterRange() {
        aboard("Vak Kaleen", carrier, RetrievalMethod.TRANSPORTER);
        carrier.setLocation(new Location(20, 10));   // 9 hexes off
        toActivityPhase();
        openShieldToward(carrier, receiver);

        ActionResult r = game.pickUpObjective(receiver, "Vak Kaleen", RetrievalMethod.TRANSPORTER);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("out of transporter range"));
    }

    // -------------------------------------------------------------------------
    // (G25.211) size
    // -------------------------------------------------------------------------

    /**
     * A stored shuttle is 25 cargo spaces (Annex #7K) and a transporter moves ten, so no
     * amount of energy or spare transporters gets it across — (G25.211) forbids combining
     * them. This is the rule that makes "some objectives MUST go by shuttle" true rather
     * than a scenario preference.
     */
    @Test
    public void oversizeCargoCannotBeBeamedAtAll() {
        Objective pod = aboard("Cargo Pod", carrier, RetrievalMethod.TRANSPORTER);
        pod.setCargoSpaces(25.0);
        toActivityPhase();
        openShieldToward(carrier, receiver);

        ActionResult r = game.pickUpObjective(receiver, "Cargo Pod", RetrievalMethod.TRANSPORTER);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("G25.211"));
        assertTrue("the size should read as the annex writes it, not as 25.0",
                r.getMessage().contains("25 cargo spaces"));
    }

    /** Exactly ten is within a single transporter — the cap is inclusive (G25.21). */
    @Test
    public void tenSpacesIsStillOneTransporterOperation() {
        Objective sat = aboard("Defense Satellite", carrier, RetrievalMethod.TRANSPORTER);
        sat.setCargoSpaces(10.0);
        toActivityPhase();
        openShieldToward(carrier, receiver);

        assertTrue(game.pickUpObjective(receiver, "Defense Satellite", RetrievalMethod.TRANSPORTER)
                .isSuccess());
    }

    /**
     * An unstated size is a gap, not a weight of zero — the gate is skipped. Boxed
     * {@code Double} is what lets the two be told apart; a primitive would read every
     * silent scenario as weightless and quietly pass.
     */
    @Test
    public void anUnstatedSizeIsNotGated() {
        Objective o = aboard("Probe Canister", carrier, RetrievalMethod.TRANSPORTER);
        assertNull("fixture: the scenario never said how big it is", o.getCargoSpaces());
        toActivityPhase();
        openShieldToward(carrier, receiver);

        assertTrue(game.pickUpObjective(receiver, "Probe Canister", RetrievalMethod.TRANSPORTER)
                .isSuccess());
    }

    // -------------------------------------------------------------------------
    // (G25.214) leaving a thing in space
    // -------------------------------------------------------------------------

    @Test
    public void jettison_isRefusedByDefault() {
        Objective kaleen = aboard("Vak Kaleen", carrier, RetrievalMethod.TRANSPORTER);
        toActivityPhase();

        ActionResult r = game.jettisonObjective(carrier, "Vak Kaleen");

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("G25.214"));
        assertSame("nothing moved", carrier, kaleen.getCarrier());
    }

    @Test
    public void jettison_worksWhenTheScenarioGrantsIt() {
        Objective mine = aboard("Captured T-Bomb", carrier, RetrievalMethod.TRANSPORTER);
        mine.setCanBeLeftInSpace(true);
        toActivityPhase();

        ActionResult r = game.jettisonObjective(carrier, "Captured T-Bomb");

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue("free again", mine.isFree());
        assertEquals("in the hex the ship was in", carrier.getLocation(), mine.getLocation());
    }

    /** Dropped, it can be collected by anyone — which is the point of the grant. */
    @Test
    public void jettison_leavesSomethingTheOtherShipCanPickUp() {
        Objective mine = aboard("Captured T-Bomb", carrier, RetrievalMethod.TRANSPORTER);
        mine.setCanBeLeftInSpace(true);
        toActivityPhase();
        assertTrue(game.jettisonObjective(carrier, "Captured T-Bomb").isSuccess());

        ActionResult r = game.pickUpObjective(receiver, "Captured T-Bomb", RetrievalMethod.TRANSPORTER);

        assertTrue(r.getMessage(), r.isSuccess());
        assertSame(receiver, mine.getCarrier());
    }

    @Test
    public void jettison_refusedFromAShipThatIsNotCarryingIt() {
        Objective mine = aboard("Captured T-Bomb", carrier, RetrievalMethod.TRANSPORTER);
        mine.setCanBeLeftInSpace(true);
        toActivityPhase();

        ActionResult r = game.jettisonObjective(receiver, "Captured T-Bomb");

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("not aboard"));
    }
}
