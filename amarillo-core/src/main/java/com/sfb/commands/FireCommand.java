package com.sfb.commands;

import java.util.List;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.weapons.Weapon;

/**
 * Command to fire a set of direct-fire weapons at a target.
 * Carries all information needed to resolve the attack: attacker, target,
 * selected weapons, range (true and scanner-adjusted), and the shield facing
 * the attacker.
 */
public class FireCommand implements Command {

    private final Ship         attacker;
    private final Unit         target;
    private final List<Weapon> selected;
    private final int          range;
    private final int          adjustedRange;
    private final int          shieldNumber;
    private final boolean      useUim;

    public FireCommand(Ship attacker, Unit target, List<Weapon> selected,
                       int range, int adjustedRange, int shieldNumber) {
        this(attacker, target, selected, range, adjustedRange, shieldNumber, false);
    }

    public FireCommand(Ship attacker, Unit target, List<Weapon> selected,
                       int range, int adjustedRange, int shieldNumber, boolean useUim) {
        this.attacker      = attacker;
        this.target        = target;
        this.selected      = selected;
        this.range         = range;
        this.adjustedRange = adjustedRange;
        this.shieldNumber  = shieldNumber;
        this.useUim        = useUim;
    }

    @Override
    public ActionResult execute(Game game) {
        String log = game.fireWeapons(attacker, target, selected, range, adjustedRange, shieldNumber, useUim);
        return ActionResult.ok(log);
    }
}
