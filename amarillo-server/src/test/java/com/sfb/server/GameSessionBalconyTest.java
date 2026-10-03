package com.sfb.server;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import com.sfb.systemgroups.ShuttleBay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The balcony actions at the layer that gates them (J1.53).
 * <p>
 * Every rule in slices one to five is enforced in core and tested there. What those tests
 * cannot show is whether a player can REACH any of it: an action the request protocol does not
 * carry is a feature that exists only in the test suite. This project has shipped that twice.
 * <p>
 * So these go through {@code session.executeAction} with a real {@link ActionRequest}, the way
 * the client does, and they also pin the two things only this layer decides: that the shared
 * combat log says a craft moved without saying WHAT (J1.534 lets a scatter pack sit out there
 * looking like anything else), and that the detail stays in the actor's private response.
 * <p>
 * The balcony is given to a CA's bay here rather than using a CVA from the ship files. These
 * tests are about the protocol, and the hull that happens to own a balcony is
 * {@code BalconyPositionTest}'s subject - one that reads the data.
 */
class GameSessionBalconyTest {

    private static final String HOST = "token-host";
    private static final int POSITIONS = 3;

    private GameSession session;
    private Game game;
    private Ship fed;

    @BeforeEach
    void setUp() {
        session = new GameSession("game-1", HOST, "Alice");
        game = session.getGame();

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);
        fed.getShuttles().getBays().get(0).setBalconyPositions(POSITIONS);
        // An owner, which the landing needs: J1.531 bars ENEMY craft from a balcony, and an
        // unowned ship has no team to be friendly with - so without this the carrier refuses
        // its own fighter.
        com.sfb.Player fedPlayer = new com.sfb.Player();
        fedPlayer.setTeamName("Federation");
        fed.setOwner(fedPlayer);

        game.getShips().add(fed);
        game.startTurn();
        game.submitAllocation(fed, makeAllocation(fed));

        for (int guard = 0; guard < 20
                && game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY; guard++)
            game.advancePhase();
        session.drainCombatLog(); // discard phase noise from setup
    }

    private ShuttleBay bay() {
        return fed.getShuttles().getBays().get(0);
    }

    private ActionRequest request(String type, String shuttleName) {
        ActionRequest req = new ActionRequest();
        req.setType(type);
        req.setShipName("USS Enterprise");
        req.setPlayerToken(HOST);
        req.setAction(shuttleName); // the shuttle name rides the action field, as launches do
        return req;
    }

    /** Advance two impulses, which is what J1.50's one-per-two-impulse rate needs. */
    private void afterHatchCooldown() {
        for (int i = 0; i < 2; i++) {
            int was = game.getAbsoluteImpulse();
            for (int guard = 0; guard < 40 && game.getAbsoluteImpulse() == was; guard++)
                game.advancePhase();
            for (int guard = 0; guard < 20
                    && game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY; guard++)
                game.advancePhase();
        }
        session.drainCombatLog();
    }

    // ---------------------------------------------------------------- out and back

    @Test
    void moveToBalcony_isReachableAndTheSharedLineSaysNothingOfWhat() {
        String name = bay().getInventory().get(0).getName();

        ActionResult r = session.executeAction(request("MOVE_TO_BALCONY", name));

        assertTrue(r.isSuccess(), r.getMessage());
        assertEquals(1, bay().getBalcony().size());
        assertEquals(name, bay().getBalcony().get(0).getName());

        // The actor's own response may name it; the shared log may not.
        assertTrue(r.getMessage().contains(name), r.getMessage());
        List<String> log = session.drainCombatLog();
        assertEquals(1, log.size());
        assertEquals("USS Enterprise moved a shuttle onto its balcony", log.get(0));
        assertFalse(log.get(0).contains(name), "the shared line named the craft: " + log.get(0));
    }

    @Test
    void moveFromBalcony_isReachableAndBringsTheCraftBackToABox() {
        String name = bay().getInventory().get(0).getName();
        assertTrue(session.executeAction(request("MOVE_TO_BALCONY", name)).isSuccess());
        int insideBefore = bay().getInventory().size();
        afterHatchCooldown();

        ActionResult r = session.executeAction(request("MOVE_FROM_BALCONY", name));

        assertTrue(r.isSuccess(), r.getMessage());
        assertTrue(bay().getBalcony().isEmpty());
        assertEquals(insideBefore + 1, bay().getInventory().size());
        assertEquals("USS Enterprise brought a shuttle in from its balcony",
                session.drainCombatLog().get(0));
    }

    /**
     * J1.532 through the protocol: the second transfer in the same two-impulse cycle is
     * refused, and refused with the rule's own number so the client can show why.
     */
    @Test
    void aSecondTransferInTheSameCycleIsRefused() {
        List<com.sfb.objects.shuttles.Shuttle> inside = bay().getInventory();
        String first = inside.get(0).getName();
        String second = inside.get(1).getName();

        assertTrue(session.executeAction(request("MOVE_TO_BALCONY", first)).isSuccess());
        ActionResult r = session.executeAction(request("MOVE_TO_BALCONY", second));

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("J1.532"), r.getMessage());
        assertEquals(1, bay().getBalcony().size());
    }

    @Test
    void anUnknownCraftIsRefusedRatherThanIgnored() {
        ActionResult r = session.executeAction(request("MOVE_TO_BALCONY", "No-Such-Shuttle"));

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("No-Such-Shuttle"), r.getMessage());
    }

    @Test
    void anEmptyShuttleNameIsRefused() {
        ActionResult r = session.executeAction(request("MOVE_TO_BALCONY", ""));

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage().contains("No shuttle specified"), r.getMessage());
    }

    // ---------------------------------------------------------------- launching from out there

    /**
     * The protocol half of slice three. LAUNCH_SHUTTLE used to resolve the named craft out of
     * the bay INVENTORY, which a parked craft is deliberately absent from - so the free launch
     * J1.53 grants was unreachable by name. It resolves through launchableInventory() now, and
     * this is the test that would have caught the gap.
     */
    @Test
    void aParkedCraftCanBeLaunchedByName() {
        String name = bay().getInventory().get(0).getName();
        assertTrue(session.executeAction(request("MOVE_TO_BALCONY", name)).isSuccess());
        session.drainCombatLog();

        ActionRequest req = request("LAUNCH_SHUTTLE", name);
        req.setSpeed(4);
        req.setRange(1); // facing rides the range field
        ActionResult r = session.executeAction(req);

        assertTrue(r.isSuccess(), r.getMessage());
        assertTrue(bay().getBalcony().isEmpty(), "it left the balcony");
    }

    /** And it is free: a spent hatch does not stop it (J1.53). */
    @Test
    void aParkedCraftLaunchesEvenWithTheHatchSpent() {
        List<com.sfb.objects.shuttles.Shuttle> inside = bay().getInventory();
        String parked = inside.get(0).getName();
        String other = inside.get(1).getName();
        assertTrue(session.executeAction(request("MOVE_TO_BALCONY", parked)).isSuccess());
        afterHatchCooldown();
        // Spend this cycle's hatch on a second transfer.
        assertTrue(session.executeAction(request("MOVE_TO_BALCONY", other)).isSuccess());
        assertFalse(bay().canLaunch(game.getAbsoluteImpulse()), "the hatch is spent");

        ActionRequest req = request("LAUNCH_SHUTTLE", parked);
        req.setSpeed(4);
        req.setRange(1);

        assertTrue(session.executeAction(req).isSuccess());
    }

    // ---------------------------------------------------------------- landing back on

    @Test
    void landOnBalcony_isReachable() {
        String name = bay().getInventory().get(0).getName();
        assertTrue(session.executeAction(request("MOVE_TO_BALCONY", name)).isSuccess());
        ActionRequest launch = request("LAUNCH_SHUTTLE", name);
        launch.setSpeed(4);
        launch.setRange(1);
        assertTrue(session.executeAction(launch).isSuccess());
        com.sfb.objects.shuttles.Shuttle craft = game.getActiveShuttles().get(0);
        craft.setSpeed(0);
        session.drainCombatLog();

        ActionResult r = session.executeAction(request("LAND_ON_BALCONY", craft.getName()));

        assertTrue(r.isSuccess(), r.getMessage());
        assertEquals(1, bay().getBalcony().size());
        assertTrue(game.getActiveShuttles().isEmpty());
    }

    // ---------------------------------------------------------------- what the client is told

    /**
     * The owner's own view has to carry the balcony, or the hangar drawer cannot offer any of
     * the above. Parked craft are reported like the ones in boxes - same shape, because the
     * owner acts on them in the same two ways.
     */
    @Test
    void theOwnersDtoReportsThePositionsAndTheCraftParkedOnThem() {
        String name = bay().getInventory().get(0).getName();
        assertTrue(session.executeAction(request("MOVE_TO_BALCONY", name)).isSuccess());

        com.sfb.dto.GameStateDto.ShipDto ship = ownShipDto();
        com.sfb.dto.GameStateDto.ShuttleBayDto bayDto = ship.shuttleBays.get(0);

        assertEquals(POSITIONS, bayDto.balconyPositions);
        assertEquals(1, bayDto.balcony.size());
        assertEquals(name, bayDto.balcony.get(0).name);
        assertTrue(bayDto.balcony.get(0).canLaunch, "a parked craft can always launch (J1.53)");
        assertTrue(bayDto.shuttles.stream().noneMatch(sd -> sd.name.equals(name)),
                "a parked craft must not also appear among the craft in boxes");
    }

    /** A bay with no balcony reports zero positions and an empty list, never null. */
    @Test
    void aBayWithNoBalconyReportsZeroAndAnEmptyList() {
        fed.getShuttles().getBays().get(0).setBalconyPositions(0);

        com.sfb.dto.GameStateDto.ShuttleBayDto bayDto = ownShipDto().shuttleBays.get(0);

        assertEquals(0, bayDto.balconyPositions);
        assertNotNull(bayDto.balcony);
        assertTrue(bayDto.balcony.isEmpty());
    }

    /** The state as this ship's OWN side sees it - the view the hangar drawer renders. */
    private com.sfb.dto.GameStateDto.ShipDto ownShipDto() {
        String team = fed.getOwner() != null ? fed.getOwner().getTeamName() : null;
        com.sfb.dto.GameStateDto state = new com.sfb.dto.GameStateDto(game, team);
        for (com.sfb.dto.GameStateDto.MapObjectDto o : state.mapObjects)
            if (o instanceof com.sfb.dto.GameStateDto.ShipDto sd
                    && "USS Enterprise".equals(sd.name))
                return sd;
        throw new AssertionError("fixture: the owner's own ship should be in its own state");
    }

    private Energy makeAllocation(Ship ship) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(0.0);
        return e;
    }
}
