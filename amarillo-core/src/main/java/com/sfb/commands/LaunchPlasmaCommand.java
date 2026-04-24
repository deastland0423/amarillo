package com.sfb.commands;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.weapons.PlasmaLauncher;

/**
 * Command to launch a plasma torpedo (real or pseudo) at a target.
 * The pseudo flag distinguishes a decoy launch from an armed torpedo launch.
 */
public class LaunchPlasmaCommand implements Command {

    private final Ship          launcher;
    private final Unit          target;
    private final PlasmaLauncher weapon;
    private final boolean       pseudo;
    private final int           facing; // 0 = auto-compute from target

    public LaunchPlasmaCommand(Ship launcher, Unit target, PlasmaLauncher weapon, boolean pseudo, int facing) {
        this.launcher = launcher;
        this.target   = target;
        this.weapon   = weapon;
        this.pseudo   = pseudo;
        this.facing   = facing;
    }

    @Override
    public ActionResult execute(Game game) {
        return pseudo
                ? game.launchPseudoPlasma(launcher, target, weapon, facing)
                : game.launchPlasma(launcher, target, weapon, facing);
    }
}
