package com.sfb;

import java.util.ArrayList;
import java.util.List;

import com.sfb.constants.Constants;
import com.sfb.exceptions.CapacitorException;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.properties.Faction;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.utilities.MapUtils;
import com.sfb.weapons.DirectFire;
import com.sfb.weapons.Weapon;

public class Main {

	/*
	 * The SEQUENCE OF PLAY
	 * 
	 * 1) Energy Allocation Phase
	 * 2) Speed Determination Phase
	 * 3) Self-Destruction Phase
	 * 4) Sensor Lock-On Phase
	 * 6) Impulse Procedure (repeat for each impulse)
	 * --- A) Movement Segment (units move)
	 * --- B) Impulse Activity Segment (transporters, mines, launching shuttles,
	 * launching seekers, etc.
	 * --- C) Dogfight Resolution Interface
	 * --- D) Direct-Fire Weapons Segment (fire weapons)
	 * --- E) Post-Combat Segment
	 * 7) Final Activity Phase
	 * 8) Record Keeping Phase
	 * 
	 * 
	 * 
	 * 
	 */

	private static List<Player> players = new ArrayList<>(); // The players
	private static List<Ship> ships = new ArrayList<>(); // The ships on the board.
	private static List<Seeker> seekers = new ArrayList<>(); // The seekers on the board.
	private static TurnTracker turnTracker = new TurnTracker(); // Time tracker for everything.
	private static boolean inProgress = true; // When true, the game continues to run.

	public Main() {
	}

	public static void main(String[] args) {

		System.out.println("Starting main...");

		Player player1 = new Player();
		player1.setName("Knosset");
		player1.setFaction(Faction.Federation);

		Player player2 = new Player();
		player2.setName("Kumerian");
		player2.setFaction(Faction.Klingon);

		// Place a Fed CA on the board.
		Ship fedCa = new Ship();
		fedCa.init(FederationShips.getFedCa());
		fedCa.setLocation(new Location(14, 01));
		fedCa.setFacing(13);
		fedCa.setOwner(player1);
		ships.add(fedCa);

		// Place a Klin D7 on the board.
		Ship klnD7 = new Ship();
		klnD7.init(KlingonShips.getD7());
		klnD7.setLocation(new Location(14, 30));
		klnD7.setFacing(1);
		klnD7.setOwner(player2);
		ships.add(klnD7);

		System.out.println("Range from CA to D7: " + MapUtils.getRange(klnD7, fedCa));
		// What weapons on the FedCA have the Klin in arc?
		List<Weapon> inArc = fedCa.fetchAllBearingWeapons(klnD7);
		for (Weapon weapon : inArc) {
			System.out.println(weapon.getName());

		}

		System.out.println("Range from D7 to CA: " + MapUtils.getRange(klnD7, fedCa));
		// What weapons on the FedCA have the Klin in arc?
		inArc = klnD7.fetchAllBearingWeapons(fedCa);
		for (Weapon weapon : inArc) {
			if (weapon instanceof DirectFire) {
				System.out.print("Firing " + weapon.getName() + ": ");
				try {
					int damage = ((DirectFire) weapon).fire(MapUtils.getRange(klnD7, fedCa));
					System.out.println("doing " + damage + " damage.");

				} catch (WeaponUnarmedException | TargetOutOfRangeException | CapacitorException e) {
					// TODO Auto-generated catch block
					// e.printStackTrace();
					System.out.println(e.getMessage());
				}
			}

		}

		// TODO: Set up players
		// TODO: Set up map
		// TODO: Set up units
		// TODO: Links between players and ships.

		// the main loop
		while (inProgress) {
			turnTracker.nextImpulse();

			// Create a string with a list of all speeds that move each impulse
			int[] moveThisImpulse = Constants.IMPULSE_CHART[turnTracker.getLocalImpulse()];
			StringBuilder moveNow = new StringBuilder();
			for (int speed : moveThisImpulse) {
				moveNow.append(speed).append(" - ");
			}
			System.out.println(turnTracker.getTurn() + "|" + turnTracker.getLocalImpulse() + ": " + moveNow.toString());

			// On the 3rd turn, exit the main loop
			if (turnTracker.getTurn() == 3) {
				setInProgress(false);
			}
		}

	}

	public List<Player> getPlayers() {
		return players;
	}

	public void setPlayers(List<Player> players) {
		this.players = players;
	}

	public static boolean isInProgress() {
		return inProgress;
	}

	public static void setInProgress(boolean value) {
		inProgress = value;
	}

	public static TurnTracker getTurnTracker() {
		return turnTracker;
	}

}
