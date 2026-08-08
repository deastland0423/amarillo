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
		// equals(null) and cross-type must return false, not throw (equals contract)
		if (!(o instanceof Location))
			return false;
		Location otherLocation = (Location) o;
		return otherLocation.getX() == this.x && otherLocation.getY() == this.y;
	}
	
	@Override
	public int hashCode() {
		return 31 * x + y;
	}

	@Override
	public String toString() {
		return "<" + this.x + "|" + this.y + ">";
	}

}
