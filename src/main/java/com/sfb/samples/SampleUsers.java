package com.sfb.samples;

import java.util.ArrayList;
import java.util.List;

import com.sfb.Player;
import com.sfb.objects.Ship;
import com.sfb.properties.Faction;
import com.sfb.properties.Location;

public class SampleUsers {

	public static List<Player> getClassicPlayers() {
		List<Player> players = new ArrayList<>();

		// Player 1
		Player fedPlayer = new Player();
		fedPlayer.setFaction(Faction.Federation);
		fedPlayer.setName("Knosset");

		// Create new ship (CA)
		Ship fedCa = new Ship();
		fedCa.init(SampleShips.getFedCa());
		fedCa.setName("USS Kongo");
		// Set location and facing.
		fedCa.setLocation(new Location(21, 01));
		fedCa.setFacing(13);

		// Assign the ship to the player.
		fedCa.setOwner(fedPlayer);

		fedPlayer.getPlayerUnits().add(fedCa);

		// Player 2
		Player klinPlayer = new Player();
		klinPlayer.setFaction(Faction.Klingon);
		klinPlayer.setName("Aardak");

		// Create new ship (D7)
		Ship klinD7 = new Ship();
		klinD7.init(SampleShips.getD7());
		klinD7.setName("IKV Antagonist");

		// Set location and facing.
		klinD7.setLocation(new Location(21, 30));
		klinD7.setFacing(1);

		// Assign the ship to the player.
		klinD7.setOwner(klinPlayer);

		klinPlayer.getPlayerUnits().add(klinD7);

		players.add(fedPlayer);
		players.add(klinPlayer);

		return players;
	}
}
