package com.sfb.properties;

import java.util.Set;


public class Arc {

	Set<Integer> directions;
	
	public Arc() {}
	
	public void setDirections(Set<Integer> dirs) {
		this.directions = dirs;
	}
	
	public Set<Integer> getDirections() {
		return this.directions;
	}
	
	public void addDirection(Integer direction) {
		if (!this.directions.contains(direction)) {
			directions.add(direction);
		}
	}
}
