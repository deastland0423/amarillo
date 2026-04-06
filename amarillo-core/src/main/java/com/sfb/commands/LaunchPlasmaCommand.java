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

    public LaunchPlasmaCommand(Ship launcher, Unit target, PlasmaLauncher weapon, boolean pseudo) {
        this.launcher = launcher;
        this.target   = target;
        this.weapon   = weapon;
        this.pseudo   = pseudo;
    }

    @Override
    public ActionResult execute(Game game) {
        return pseudo
                ? game.launchPseudoPlasma(launcher, target, weapon)
                : game.launchPlasma(launcher, target, weapon);
    }
}
