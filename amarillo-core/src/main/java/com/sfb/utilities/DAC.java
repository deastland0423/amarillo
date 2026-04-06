package com.sfb.utilities;

// This will represent the DAC
// There are lines for rolls of 2-12
// Each line has 13 entries.
// Some entries are 'special', where they can only be
// used once per volley.
public class DAC {

	DACItem[][] dacTable = new DACItem[][] {};

	// Constructor: sets up a fresh DAC
	public DAC() {
		init();
	}

	public void reset() {
		init();
	}

	// reset the DAC so that all special items are refreshed.
	public void init() {

		// DAC line2
		DACItem line2Item1 = new DACItem("bridge", true);
		DACItem line2Item2 = new DACItem("flag", true);
		DACItem line2Item3 = new DACItem("sensor", true);
		DACItem line2Item4 = new DACItem("damcon", true);
		DACItem line2Item5 = new DACItem("afthull", true);
		DACItem line2Item6 = new DACItem("lwarp", false);
		DACItem line2Item7 = new DACItem("trans", false);
		DACItem line2Item8 = new DACItem("tractor", false);
		DACItem line2Item9 = new DACItem("shuttle", false);
		DACItem line2Item10 = new DACItem("lab", false);
		DACItem line2Item11 = new DACItem("fhull", false);
		DACItem line2Item12 = new DACItem("rwarp", false);
		DACItem line2Item13 = new DACItem("excess", false);

		// DAC Line3
		DACItem line3Item1 = new DACItem("drone", true);
		DACItem line3Item2 = new DACItem("phaser", true);
		DACItem line3Item3 = new DACItem("impulse", false);
		DACItem line3Item4 = new DACItem("lwarp", false);
		DACItem line3Item5 = new DACItem("rwarp", false);
		DACItem line3Item6 = new DACItem("afthull", false);
		DACItem line3Item7 = new DACItem("shuttle", false);
		DACItem line3Item8 = new DACItem("damcon", true);
		DACItem line3Item9 = new DACItem("cwarp", false);
		DACItem line3Item10 = new DACItem("lab", false);
		DACItem line3Item11 = new DACItem("battery", false);
		DACItem line3Item12 = new DACItem("phaser", false);
		DACItem line3Item13 = new DACItem("excess", false);

		// DAC line4
		DACItem line4Item1 = new DACItem("phaser", true);
		DACItem line4Item2 = new DACItem("trans", true);
		DACItem line4Item3 = new DACItem("rwarp", false);
		DACItem line4Item4 = new DACItem("impulse", false);
		DACItem line4Item5 = new DACItem("fhull", false);
		DACItem line4Item6 = new DACItem("ahull", false);
		DACItem line4Item7 = new DACItem("lwarp", false);
		DACItem line4Item8 = new DACItem("apr", false);
		DACItem line4Item9 = new DACItem("lab", false);
		DACItem line4Item10 = new DACItem("trans", false);
		DACItem line4Item11 = new DACItem("probe", false);
		DACItem line4Item12 = new DACItem("cwarp", false);
		DACItem line4Item13 = new DACItem("excess", false);

		// DAC line5
		DACItem line5Item1 = new DACItem("rwarp", true);
		DACItem line5Item2 = new DACItem("ahull", false);
		DACItem line5Item3 = new DACItem("cargo", false);
		DACItem line5Item4 = new DACItem("battery", false);
		DACItem line5Item5 = new DACItem("shuttle", false);
		DACItem line5Item6 = new DACItem("torp", true);
		DACItem line5Item7 = new DACItem("lwarp", false);
		DACItem line5Item8 = new DACItem("impulse", false);
		DACItem line5Item9 = new DACItem("rwarp", false);
		DACItem line5Item10 = new DACItem("tractor", false);
		DACItem line5Item11 = new DACItem("probe", false);
		DACItem line5Item12 = new DACItem("weapon", false);
		DACItem line5Item13 = new DACItem("excess", false);

		// DAC line6
		DACItem line6Item1 = new DACItem("fhull", false);
		DACItem line6Item2 = new DACItem("impulse", false);
		DACItem line6Item3 = new DACItem("lab", false);
		DACItem line6Item4 = new DACItem("lwarp", false);
		DACItem line6Item5 = new DACItem("sensor", true);
		DACItem line6Item6 = new DACItem("tractor", false);
		DACItem line6Item7 = new DACItem("shuttle", false);
		DACItem line6Item8 = new DACItem("rwarp", false);
		DACItem line6Item9 = new DACItem("phaser", false);
		DACItem line6Item10 = new DACItem("trans", false);
		DACItem line6Item11 = new DACItem("battery", false);
		DACItem line6Item12 = new DACItem("weapon", false);
		DACItem line6Item13 = new DACItem("excess", false);

		// DAC line7
		DACItem line7Item1 = new DACItem("cargo", false);
		DACItem line7Item2 = new DACItem("fhull", false);
		DACItem line7Item3 = new DACItem("battery", false);
		DACItem line7Item4 = new DACItem("cwarp", false);
		DACItem line7Item5 = new DACItem("shuttle", false);
		DACItem line7Item6 = new DACItem("apr", false);
		DACItem line7Item7 = new DACItem("lab", false);
		DACItem line7Item8 = new DACItem("phaser", false);
		DACItem line7Item9 = new DACItem("warp", false);
		DACItem line7Item10 = new DACItem("probe", false);
		DACItem line7Item11 = new DACItem("ahull", false);
		DACItem line7Item12 = new DACItem("weapon", false);
		DACItem line7Item13 = new DACItem("excess", false);

		// DAC line8
		DACItem line8Item1 = new DACItem("ahull", false);
		DACItem line8Item2 = new DACItem("apr", false);
		DACItem line8Item3 = new DACItem("shuttle", false);
		DACItem line8Item4 = new DACItem("rwarp", false);
		DACItem line8Item5 = new DACItem("scanner", true);
		DACItem line8Item6 = new DACItem("tractor", false);
		DACItem line8Item7 = new DACItem("lab", false);
		DACItem line8Item8 = new DACItem("lwarp", false);
		DACItem line8Item9 = new DACItem("phaser", false);
		DACItem line8Item10 = new DACItem("trans", false);
		DACItem line8Item11 = new DACItem("battery", false);
		DACItem line8Item12 = new DACItem("weapon", false);
		DACItem line8Item13 = new DACItem("excess", false);

		// DAC line9
		DACItem line9Item1 = new DACItem("lwarp", true);
		DACItem line9Item2 = new DACItem("fhull", false);
		DACItem line9Item3 = new DACItem("cargo", false);
		DACItem line9Item4 = new DACItem("battery", false);
		DACItem line9Item5 = new DACItem("lab", false);
		DACItem line9Item6 = new DACItem("drone", true);
		DACItem line9Item7 = new DACItem("rwarp", false);
		DACItem line9Item8 = new DACItem("impulse", false);
		DACItem line9Item9 = new DACItem("lwarp", false);
		DACItem line9Item10 = new DACItem("tractor", false);
		DACItem line9Item11 = new DACItem("probe", false);
		DACItem line9Item12 = new DACItem("weapon", false);
		DACItem line9Item13 = new DACItem("excess", false);

		// DAC line10
		DACItem line10Item1 = new DACItem("phaser", true);
		DACItem line10Item2 = new DACItem("tractor", true);
		DACItem line10Item3 = new DACItem("lwarp", false);
		DACItem line10Item4 = new DACItem("impulse", false);
		DACItem line10Item5 = new DACItem("ahull", false);
		DACItem line10Item6 = new DACItem("fhull", false);
		DACItem line10Item7 = new DACItem("rwarp", false);
		DACItem line10Item8 = new DACItem("apr", false);
		DACItem line10Item9 = new DACItem("lab", false);
		DACItem line10Item10 = new DACItem("trans", false);
		DACItem line10Item11 = new DACItem("probe", false);
		DACItem line10Item12 = new DACItem("cwarp", false);
		DACItem line10Item13 = new DACItem("excess", false);

		// DAC line11
		DACItem line11Item1 = new DACItem("torp", true);
		DACItem line11Item2 = new DACItem("phaser", true);
		DACItem line11Item3 = new DACItem("impulse", false);
		DACItem line11Item4 = new DACItem("rwarp", false);
		DACItem line11Item5 = new DACItem("lwarp", false);
		DACItem line11Item6 = new DACItem("fhull", false);
		DACItem line11Item7 = new DACItem("tractor", false);
		DACItem line11Item8 = new DACItem("damcon", true);
		DACItem line11Item9 = new DACItem("cwarp", false);
		DACItem line11Item10 = new DACItem("lab", false);
		DACItem line11Item11 = new DACItem("battery", false);
		DACItem line11Item12 = new DACItem("phaser", false);
		DACItem line11Item13 = new DACItem("excess", false);

		// DAC line12
		DACItem line12Item1 = new DACItem("auxcon", true);
		DACItem line12Item2 = new DACItem("emer", true);
		DACItem line12Item3 = new DACItem("scanner", true);
		DACItem line12Item4 = new DACItem("probe", true);
		DACItem line12Item5 = new DACItem("fhull", true);
		DACItem line12Item6 = new DACItem("rwarp", false);
		DACItem line12Item7 = new DACItem("trans", false);
		DACItem line12Item8 = new DACItem("shuttle", false);
		DACItem line12Item9 = new DACItem("tractor", false);
		DACItem line12Item10 = new DACItem("lab", false);
		DACItem line12Item11 = new DACItem("ahull", false);
		DACItem line12Item12 = new DACItem("lwarp", false);
		DACItem line12Item13 = new DACItem("excess", false);

		this.dacTable = new DACItem[][] {
				{ line2Item1, line2Item2, line2Item3, line2Item4, line2Item5, line2Item6, line2Item7, line2Item8, line2Item9,
						line2Item10, line2Item11, line2Item12, line2Item13 },
				{ line3Item1, line3Item2, line3Item3, line3Item4, line3Item5, line3Item6, line3Item7, line3Item8, line3Item9,
						line3Item10, line3Item11, line3Item12, line3Item13 },
				{ line4Item1, line4Item2, line4Item3, line4Item4, line4Item5, line4Item6, line4Item7, line4Item8, line4Item9,
						line4Item10, line4Item11, line4Item12, line4Item13 },
				{ line5Item1, line5Item2, line5Item3, line5Item4, line5Item5, line5Item6, line5Item7, line5Item8, line5Item9,
						line5Item10, line5Item11, line5Item12, line5Item13 },
				{ line6Item1, line6Item2, line6Item3, line6Item4, line6Item5, line6Item6, line6Item7, line6Item8, line6Item9,
						line6Item10, line6Item11, line6Item12, line6Item13 },
				{ line7Item1, line7Item2, line7Item3, line7Item4, line7Item5, line7Item6, line7Item7, line7Item8, line7Item9,
						line7Item10, line7Item11, line7Item12, line7Item13 },
				{ line8Item1, line8Item2, line8Item3, line8Item4, line8Item5, line8Item6, line8Item7, line8Item8, line8Item9,
						line8Item10, line8Item11, line8Item12, line8Item13 },
				{ line9Item1, line9Item2, line9Item3, line9Item4, line9Item5, line9Item6, line9Item7, line9Item8, line9Item9,
						line9Item10, line9Item11, line9Item12, line9Item13 },
				{ line10Item1, line10Item2, line10Item3, line10Item4, line10Item5, line10Item6, line10Item7, line10Item8,
						line10Item9, line10Item10, line10Item11, line10Item12, line10Item13 },
				{ line11Item1, line11Item2, line11Item3, line11Item4, line11Item5, line11Item6, line11Item7, line11Item8,
						line11Item9, line11Item10, line11Item11, line11Item12, line11Item13 },
				{ line12Item1, line12Item2, line12Item3, line12Item4, line12Item5, line12Item6, line12Item7, line12Item8,
						line12Item9, line12Item10, line12Item11, line12Item12, line12Item13 }
		};
	}

	public String fetchNextHit(int roll) {
		String result = null;
		// Fetch the line of the DAC indicated by this roll.
		DACItem[] dacLine = this.dacTable[roll - 2];

		// Return the next damage result
		for (int i = 0; i < dacLine.length; i++) {
			// Check each item in the line. Get the first 'available' item in the line.
			// If the available item is 'special' then set it to unavailable so it won't
			// be hit again until the DAC is reset.
			DACItem item = dacLine[i];
			if (item.isAvailable()) {
				result = item.getSystem();
				if (item.isSpecial()) {
					item.setUnavailable();
				}
				break;
			}
		}

		return result;
	}

	private class DACItem {

		private String system; // Name of the system
		private boolean special; // True if this is an underlined item.
		private boolean available; // Default true, set to false when it is a 'special' and has been already hit.

		public DACItem(String system, boolean special) {
			this.system = system;
			this.special = special;
			this.available = true;
		}

		public String getSystem() {
			return this.system;
		}

		public void setSystem(String system) {
			this.system = system;
		}

		public void setAvailable() {
			this.available = true;
		}

		public void setUnavailable() {
			this.available = false;
		}

		public boolean isAvailable() {
			return this.available;
		}

		public boolean isSpecial() {
			return this.special;
		}
	}
}
