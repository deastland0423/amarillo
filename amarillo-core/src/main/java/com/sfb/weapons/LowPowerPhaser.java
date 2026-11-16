package com.sfb.weapons;

/**
 * E2.25 LOW-POWER: a phaser bigger than a ph-3 may be fired AS a ph-3.
 *
 * <p>"Phasers can be fired at low power. This may be done to save power, or because there is
 * not enough power, or because of special scenario rules, or other tactical considerations,
 * such as to avoid blinding special sensors (G24.34)." E2.251 prices it: "Any ph-1, ph-2, or
 * ph-4 can be fired as a ph-3 by using only 1/2-unit of power."
 *
 * <h2>Why a scout cares, which is the reason this was built</h2>
 * G24.13 blinds one powered scout channel for every blinding weapon the scout fires, and
 * G24.1341 puts the phaser-3 on the list of weapons that do NOT blind. G24.1343 draws the
 * conclusion: "Phasers larger than phaser-3s can be fired as phaser-3s to avoid blinding
 * sensors." G24.34 states the trade plainly — a scout "can fire its phaser-1 or phaser-2
 * weapons as phaser-3s in order to defend itself without blinding its channels… Any ship can
 * use (E2.25), but scouts have an extra reason to."
 *
 * <h2>What low power costs, and what it does not</h2>
 * It is the ph-3 in every respect that matters: the ph-3 damage table, the ph-3 energy cost,
 * and the ph-3's reach. What it does NOT buy is a second shot — E2.25 says "a low-powered
 * firing counts as the single firing of that phaser for that turn" and then says again that
 * the rule "does not imply that ph-1s, ph-2s, or ph-4s can be fired more than once per turn
 * in this mode". The rulebook saying it twice is a good sign somebody tried it, so
 * {@code registerFire} is called exactly as a full-power shot calls it.
 *
 * <h2>The declaration is per SHOT, not per turn</h2>
 * The flag is cleared as the shot resolves, so a phaser that fired low this impulse is back to
 * full power for its next shot without anything having to remember to reset it. What persists
 * is {@link #firedLowPowerThisImpulse()}, because the blinding tally in {@code DamageResolver}
 * runs after the volley settles and has to know how the shot was taken.
 *
 * <p>Not applied to the ph-G: E2.251 lists "any ph-1, ph-2, or ph-4" and does not include it,
 * and the ph-G has its own blinding clause in G24.1342 keyed on firing more than once in an
 * impulse. E2.252 (a ph-4 firing as a ph-1 or ph-2) and E2.253 (a ph-1 as a ph-2, for
 * deception under D17.76) are further modes this class does not yet offer.
 */
public abstract class LowPowerPhaser extends VariableDamageWeapon {

	/** Declared for the NEXT shot; consumed as it is taken. */
	private boolean firingLowPower = false;

	/** The impulse a low-power shot was actually taken on, so blinding can ask afterwards. */
	private int lowPowerImpulse = Integer.MIN_VALUE;

	/** E2.251: half a point, whatever the full-power phaser would have cost. */
	public static final double LOW_POWER_ENERGY = 0.5;

	/** Declare that this phaser's next shot is taken at low power, on the ph-3 table. */
	public void setFiringLowPower(boolean lowPower) {
		this.firingLowPower = lowPower;
	}

	/** True while a low-power shot is declared but not yet taken. */
	public boolean isFiringLowPower() {
		return firingLowPower;
	}

	/**
	 * True if this phaser's shot THIS impulse was taken at low power.
	 *
	 * Asked by {@link #blindsScoutChannels()} after the volley resolves, which is why the
	 * answer cannot simply be the declaration — that is cleared when the shot is taken.
	 */
	public boolean firedLowPowerThisImpulse() {
		return clock != null && lowPowerImpulse == clock.getImpulse();
	}

	/**
	 * G24.1343: a phaser fired as a ph-3 does not blind a scout channel, because G24.1341
	 * exempts the ph-3 and the shot IS a ph-3 for every purpose the rules care about.
	 */
	@Override
	public boolean blindsScoutChannels() {
		return !firedLowPowerThisImpulse();
	}

	/** The energy this shot will actually draw: half a point at low power (E2.251). */
	public double energyToFire() {
		return firingLowPower ? LOW_POWER_ENERGY : fullPowerEnergy();
	}

	/** What this phaser costs to fire normally — one point for a ph-1 or ph-2, two for a ph-4. */
	protected abstract double fullPowerEnergy();

	/**
	 * Take the shot on the ph-3 table and stamp the impulse, or return -1 if no low-power shot
	 * was declared and the caller should resolve its own full-power table instead.
	 *
	 * <p>Range is the ph-3's: a shot taken as a ph-3 reaches as far as a ph-3 does and no
	 * further, so the caller's own maximum range check must not be the one that applies.
	 */
	protected int fireAsPhaser3(int range)
			throws com.sfb.exceptions.TargetOutOfRangeException {
		if (!firingLowPower)
			return -1;

		firingLowPower = false;            // the declaration is spent whether or not it hits
		if (range > Phaser3.LOW_POWER_MAX_RANGE)
			throw new com.sfb.exceptions.TargetOutOfRangeException(
					"Target not in range for a low-power (phaser-3) shot — "
					+ range + " hexes, max " + Phaser3.LOW_POWER_MAX_RANGE + " (E2.251).");

		int roll = rollAndRecord();
		registerFire();                    // E2.25: still the phaser's one firing for the turn
		lowPowerImpulse = clock == null ? Integer.MIN_VALUE : clock.getImpulse();
		return lookupWithShift(Phaser3.chart(), roll, Phaser3.rangeBand(range));
	}
}
