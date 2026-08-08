package com.sfb.properties;

public enum SizeClass {

	// Size classes (number) match to these
	// 0 - Very Large (some monsters)
	// 1 - Starbases
	// 2 - Dreadnoughts and other Large Ships
	// 3 - Cruisers (Heavy, Light, etc.)
	// 4 - Destroyers, Frigates, Escorts, etc.
	// 5 - PFs, Interceptors, Small/Medium Ground Bases
	// 6 - Shuttlecraft, Fighters, Heavy Fighters
	// 7 - Drones, Plasma Torpedoes, Mines
	Monster(0),
	Starbase(1),
	Dreadnought(2),
	Cruiser(3),
	Destroyer(4),
	PF(5),
	Shuttle(6),
	Seeker(7);

	private int sizeClassNumber;

	SizeClass(int sizeClassNumber) {
		this.sizeClassNumber = sizeClassNumber;
	}

	public int getSizeClassNumber() {
		return sizeClassNumber;
	}
}
