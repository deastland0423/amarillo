package com.sfb;

import java.util.Scanner;

import com.sfb.properties.Location;
import com.sfb.utilities.ArcUtils;
import com.sfb.utilities.MapUtils;

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

	private static TurnTracker turnTracker = new TurnTracker();

	public Main() {
	}

	public static TurnTracker getTurnTracker() {
		return turnTracker;
	}

	public static void main(String[] args) {

		int Ship1X = 21;
		int Ship1Y = 9;
		int Ship1Facing = 21; // A=1, B=5, C
		int Ship2X = 12;
		int Ship2Y = 5;

		int absoluteBearing = MapUtils.getBearing(new Location(Ship1X, Ship1Y), new Location(Ship2X, Ship2Y));
		int relativeBearing = MapUtils.getRelativeBearing(absoluteBearing, Ship1Facing);

		System.out.println("-----------------------------------------------");
		System.out.println("Absolute Bearing: " + absoluteBearing);
		System.out.println("Relative Bearing: " + relativeBearing);
		System.out.println("-----------------------------------------------");

		// --- Interactive bearing calculator ---
		// Scanner scanner = new Scanner(System.in);
		// System.out.println("=== Bearing Calculator (enter blank line to exit) ===");
		// System.out.println("Format: shipX shipY facing targetX targetY");
		// System.out.println("Facings: A=1 B=5 C=9 D=13 E=17 F=21\n");
		// while (true) {
		// System.out.print("> ");
		// String line = scanner.nextLine().trim();
		// if (line.isEmpty())
		// break;
		// String[] parts = line.split("\\s+");
		// if (parts.length < 5) {
		// System.out.println(" Need 5 values: shipX shipY facing targetX targetY");
		// continue;
		// }
		// try {
		// int sx = Integer.parseInt(parts[0]);
		// int sy = Integer.parseInt(parts[1]);
		// int facing = Integer.parseInt(parts[2]);
		// int tx = Integer.parseInt(parts[3]);
		// int ty = Integer.parseInt(parts[4]);
		// Location shipLoc = new Location(sx, sy);
		// Location targetLoc = new Location(tx, ty);
		// int absBearing = MapUtils.getBearing(shipLoc, targetLoc);
		// int relBearing = MapUtils.getRelativeBearing(absBearing, facing);
		// int range = MapUtils.getRange(shipLoc, targetLoc);
		// System.out.printf(" abs bearing: %-3d rel bearing: %-3d range: %d%n",
		// absBearing, relBearing, range);
		// } catch (NumberFormatException e) {
		// System.out.println(" Bad input — all values must be integers.");
		// }
		// }
		// scanner.close();
		// System.out.println();

		// // --- Static arc diagnostics ---
		// System.out.println("=== Plasma Arc Diagnostics ===\n");

		// // Test each of the 6 ship facings
		// int[] facings = { 1, 5, 9, 13, 17, 21 };
		// String[] labels = { "A", "B", "C", "D", "E", "F" };

		// // WB+ plasma-R: launchDirections = ArcUtils.of(1) = forward only, arc = FA
		// int launchDirsMask = ArcUtils.of(1);
		// int arcMask = ArcUtils.FA;

		// System.out.printf("%-10s %-12s %-12s %-16s %-16s%n",
		// "Facing", "launchMask", "rotated", "validFacings(launch)",
		// "validFacings(arc)");
		// System.out.println("-".repeat(70));

		// for (int i = 0; i < facings.length; i++) {
		// int facing = facings[i];
		// int rotated = rotateMask(launchDirsMask, facing);
		// int rotatedArc = rotateMask(arcMask, facing);

		// // Which of the 6 picker facings fall inside each rotated mask?
		// StringBuilder launchValid = new StringBuilder();
		// StringBuilder arcValid = new StringBuilder();
		// for (int j = 0; j < facings.length; j++) {
		// int f = facings[j];
		// if (ArcUtils.inArc(f, rotated))
		// launchValid.append(labels[j]);
		// if (ArcUtils.inArc(f, rotatedArc))
		// arcValid.append(labels[j]);
		// }

		// System.out.printf("%-10s %-12s %-12s %-16s %-16s%n",
		// labels[i] + " (" + facing + ")",
		// Integer.toBinaryString(launchDirsMask),
		// Integer.toBinaryString(rotated),
		// launchValid.toString(),
		// arcValid.toString());
		// }

		// // Verify server-side relative bearing check matches client rotation
		// System.out.println("\n=== Server-side relFacing check
		// (launchDirections=forward, ArcUtils.of(1)) ===");
		// System.out.printf("%-12s %-18s %-10s %-10s%n", "ShipFacing",
		// "AbsLaunchFacing", "relFacing", "inArc?");
		// System.out.println("-".repeat(55));
		// for (int i = 0; i < facings.length; i++) {
		// int shipFacing = facings[i];
		// // Test launching in each of the 6 absolute directions
		// for (int j = 0; j < facings.length; j++) {
		// int absLaunch = facings[j];
		// int relFacing = MapUtils.getRelativeBearing(absLaunch, shipFacing);
		// boolean ok = ArcUtils.inArc(relFacing, launchDirsMask);
		// if (ok) { // only print valid combos to keep output manageable
		// System.out.printf("%-12s %-18s %-10d %-10s%n",
		// labels[i] + " (" + shipFacing + ")",
		// labels[j] + " (" + absLaunch + ")",
		// relFacing, ok ? "YES" : "no");
		// }
		// }
		// }
	}

	/**
	 * Mirrors the client-side rotateArcMask — rotate a ship-relative 24-bit arc
	 * mask by ship facing.
	 */
	private static int rotateMask(int mask, int facing) {
		if (mask == 0 || facing <= 1)
			return mask;
		int shift = facing - 1;
		return ((mask << shift) | (mask >>> (24 - shift))) & 0xFFFFFF;
	}

}
