package com.sfb.commands;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.systems.Energy;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.weapons.DroneRack;
import com.sfb.objects.Drone;
import com.sfb.weapons.PlasmaLauncher;
import com.sfb.weapons.Weapon;
import com.sfb.properties.PlasmaType;

/**
 * Verifies that each Command subclass routes to the correct Game method
 * with the correct arguments.  Uses Mockito to avoid standing up a full
 * game session.
 */
public class CommandTest {

    private Game     game;
    private Ship     attacker;
    private Unit     target;

    @Before
    public void setUp() {
        game     = mock(Game.class);
        attacker = mock(Ship.class);
        target   = mock(Unit.class);

        // Default stub so any ActionResult-returning call doesn't NPE
        ActionResult ok = ActionResult.ok("ok");
        when(game.moveForward(any())).thenReturn(ok);
        when(game.turnLeft(any())).thenReturn(ok);
        when(game.turnRight(any())).thenReturn(ok);
        when(game.sideslipLeft(any())).thenReturn(ok);
        when(game.sideslipRight(any())).thenReturn(ok);
        when(game.launchDrone(any(), any(), any(), any())).thenReturn(ok);
        when(game.launchPlasma(any(), any(), any())).thenReturn(ok);
        when(game.launchPseudoPlasma(any(), any(), any())).thenReturn(ok);
        when(game.advancePhase()).thenReturn(ok);
    }

    // -------------------------------------------------------------------------
    // MoveCommand
    // -------------------------------------------------------------------------

    @Test
    public void moveForwardDelegatesToGame() {
        new MoveCommand(attacker, MoveCommand.Action.FORWARD).execute(game);
        verify(game).moveForward(attacker);
    }

    @Test
    public void turnLeftDelegatesToGame() {
        new MoveCommand(attacker, MoveCommand.Action.TURN_LEFT).execute(game);
        verify(game).turnLeft(attacker);
    }

    @Test
    public void turnRightDelegatesToGame() {
        new MoveCommand(attacker, MoveCommand.Action.TURN_RIGHT).execute(game);
        verify(game).turnRight(attacker);
    }

    @Test
    public void sideslipLeftDelegatesToGame() {
        new MoveCommand(attacker, MoveCommand.Action.SIDESLIP_LEFT).execute(game);
        verify(game).sideslipLeft(attacker);
    }

    @Test
    public void sideslipRightDelegatesToGame() {
        new MoveCommand(attacker, MoveCommand.Action.SIDESLIP_RIGHT).execute(game);
        verify(game).sideslipRight(attacker);
    }

    @Test
    public void moveCommandReturnsGameResult() {
        ActionResult expected = ActionResult.ok("moved");
        when(game.moveForward(attacker)).thenReturn(expected);
        ActionResult result = new MoveCommand(attacker, MoveCommand.Action.FORWARD).execute(game);
        assertSame(expected, result);
    }

    // -------------------------------------------------------------------------
    // LaunchDroneCommand
    // -------------------------------------------------------------------------

    @Test
    public void launchDroneDelegatesToGame() {
        DroneRack rack  = mock(DroneRack.class);
        Drone     drone = mock(Drone.class);
        new LaunchDroneCommand(attacker, target, rack, drone).execute(game);
        verify(game).launchDrone(attacker, target, rack, drone);
    }

    @Test
    public void launchDroneCommandReturnsGameResult() {
        DroneRack    rack     = mock(DroneRack.class);
        Drone        drone    = mock(Drone.class);
        ActionResult expected = ActionResult.ok("drone launched");
        when(game.launchDrone(attacker, target, rack, drone)).thenReturn(expected);
        ActionResult result = new LaunchDroneCommand(attacker, target, rack, drone).execute(game);
        assertSame(expected, result);
    }

    // -------------------------------------------------------------------------
    // LaunchPlasmaCommand — the routing logic is the key thing to pin down
    // -------------------------------------------------------------------------

    @Test
    public void realPlasmaRoutesToLaunchPlasma() {
        PlasmaLauncher launcher = new PlasmaLauncher(PlasmaType.F);
        new LaunchPlasmaCommand(attacker, target, launcher, false).execute(game);
        verify(game).launchPlasma(attacker, target, launcher);
        verify(game, never()).launchPseudoPlasma(any(), any(), any());
    }

    @Test
    public void pseudoPlasmaRoutesToLaunchPseudoPlasma() {
        PlasmaLauncher launcher = new PlasmaLauncher(PlasmaType.F);
        new LaunchPlasmaCommand(attacker, target, launcher, true).execute(game);
        verify(game).launchPseudoPlasma(attacker, target, launcher);
        verify(game, never()).launchPlasma(any(), any(), any());
    }

    @Test
    public void launchPlasmaCommandReturnsGameResult() {
        PlasmaLauncher launcher  = new PlasmaLauncher(PlasmaType.F);
        ActionResult   expected  = ActionResult.ok("plasma away");
        when(game.launchPlasma(attacker, target, launcher)).thenReturn(expected);
        ActionResult result = new LaunchPlasmaCommand(attacker, target, launcher, false).execute(game);
        assertSame(expected, result);
    }

    // -------------------------------------------------------------------------
    // FireCommand
    // -------------------------------------------------------------------------

    @Test
    public void fireCommandDelegatesToFireWeapons() {
        List<Weapon> selected = Arrays.asList(mock(Weapon.class));
        when(game.fireWeapons(attacker, target, selected, 5, 6, 2)).thenReturn("hit for 8");
        new FireCommand(attacker, target, selected, 5, 6, 2).execute(game);
        verify(game).fireWeapons(attacker, target, selected, 5, 6, 2);
    }

    @Test
    public void fireCommandWrapsLogInActionResult() {
        List<Weapon> selected = Arrays.asList(mock(Weapon.class));
        when(game.fireWeapons(any(), any(), any(), anyInt(), anyInt(), anyInt()))
                .thenReturn("hit for 8");
        ActionResult result = new FireCommand(attacker, target, selected, 5, 6, 2).execute(game);
        assertTrue(result.isSuccess());
        assertEquals("hit for 8", result.getMessage());
    }

    @Test
    public void fireCommandPassesRangeAndShieldCorrectly() {
        List<Weapon> selected = Arrays.asList(mock(Weapon.class));
        when(game.fireWeapons(any(), any(), any(), anyInt(), anyInt(), anyInt())).thenReturn("ok");
        new FireCommand(attacker, target, selected, 10, 12, 3).execute(game);
        verify(game).fireWeapons(attacker, target, selected, 10, 12, 3);
    }

    // -------------------------------------------------------------------------
    // AdvancePhaseCommand
    // -------------------------------------------------------------------------

    @Test
    public void advancePhaseDelegatesToGame() {
        new AdvancePhaseCommand().execute(game);
        verify(game).advancePhase();
    }

    @Test
    public void advancePhaseCommandReturnsGameResult() {
        ActionResult expected = ActionResult.ok("seeker moved\ninternal damage resolved");
        when(game.advancePhase()).thenReturn(expected);
        ActionResult result = new AdvancePhaseCommand().execute(game);
        assertSame(expected, result);
    }

    @Test
    public void advancePhaseCommandReturnsEmptyMessageWhenNoLog() {
        when(game.advancePhase()).thenReturn(ActionResult.ok(""));
        ActionResult result = new AdvancePhaseCommand().execute(game);
        assertTrue(result.isSuccess());
        assertEquals("", result.getMessage());
    }

    // -------------------------------------------------------------------------
    // AllocateEnergyCommand
    // -------------------------------------------------------------------------

    @Test
    public void allocateEnergyDelegatesToGame() {
        Energy allocation = mock(Energy.class);
        when(game.submitAllocation(attacker, allocation)).thenReturn(ActionResult.ok("ok"));
        new AllocateEnergyCommand(attacker, allocation).execute(game);
        verify(game).submitAllocation(attacker, allocation);
    }

    @Test
    public void allocateEnergyCommandReturnsGameResult() {
        Energy allocation = mock(Energy.class);
        ActionResult expected = ActionResult.ok("F5 energy allocated");
        when(game.submitAllocation(attacker, allocation)).thenReturn(expected);
        ActionResult result = new AllocateEnergyCommand(attacker, allocation).execute(game);
        assertSame(expected, result);
    }

    @Test
    public void allocateEnergyCommandPassesCorrectShipAndAllocation() {
        Energy allocationA = mock(Energy.class);
        Energy allocationB = mock(Energy.class);
        Ship   shipB       = mock(Ship.class);
        when(game.submitAllocation(any(), any())).thenReturn(ActionResult.ok("ok"));

        new AllocateEnergyCommand(attacker, allocationA).execute(game);
        new AllocateEnergyCommand(shipB,    allocationB).execute(game);

        verify(game).submitAllocation(attacker, allocationA);
        verify(game).submitAllocation(shipB,    allocationB);
        verify(game, never()).submitAllocation(attacker, allocationB);
        verify(game, never()).submitAllocation(shipB,    allocationA);
    }
}
