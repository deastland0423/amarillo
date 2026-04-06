package com.sfb.properties;

public class Location {
	private int x;
	private int y;

	public Location() {}
	
	public Location(int x, int y) {
		this.x = x;
		this.y = y;
	}
	public int getX() {
		return this.x;
	}
	
	public void setX(int x) {
		this.x = x;
	}
	
	public int getY() {
		return this.y;
	}
	
	public void setY(int y) {
		this.y = y;
	}
	
	@Override
	public boolean equals(Object o) {
		// Cast the object as a Locaiton object
		Location otherLocation = (Location)o;
		
		boolean result = false;
		
		if (otherLocation.getX() == this.x && otherLocation.getY() == this.y) {
			result = true;
		}
		
		return result;
	}
	
	@Override
	public String toString() {
		return "<" + this.x + "|" + this.y + ">";
	}

}
