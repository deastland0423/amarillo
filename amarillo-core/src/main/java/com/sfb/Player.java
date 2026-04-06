package com.sfb;

import java.util.ArrayList;
import java.util.List;

import com.sfb.objects.Unit;
import com.sfb.properties.Faction;

public class Player {

	private String name = null;
	private Faction faction = null;
	private List<Unit> playerUnits = new ArrayList<>();
	
	public Player() {
		
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Faction getFaction() {
		return faction;
	}

	public void setFaction(Faction faction) {
		this.faction = faction;
	}

	public List<Unit> getPlayerUnits() {
		return playerUnits;
	}

	public void setPlayerUnits(List<Unit> playerUnits) {
		this.playerUnits = playerUnits;
	}

	
}
