package com.sfb.utilities;

import java.util.ArrayList;
import java.util.List;

import com.sfb.objects.Marker;
import com.sfb.properties.Location;

/**
 * Utility object used to calculate the true bearing and range between two
 * objects on a hex map.
 * The map utilizes an x:y coordinate system.
 * 
 * @author Daniel Eastland
 *
 */
public class MapUtils {

	// Shield facings from a position
	// within the hex map.
	// 1
	//
	// 11 3
	// 12 2
	//
	// 10 X 4
	//
	// 8 6
	// 9 5
	//
	// 7

	// If on the 1/4 line, just get the yDiff.
	// If inside of 3/5 or 9/11, just get the xDiff
	// Else, find the location along the 3/5 or 9/11 spine.
	// Calculate that range, and add the yDiff.
	public static int getRange(Location sourceLocation, Location targetLocation) {
		if (sourceLocation == null || targetLocation == null) return Integer.MAX_VALUE;
		int xDiff = Math.abs(targetLocation.getX() - sourceLocation.getX());
		if (sourceLocation.getX() == targetLocation.getX())
			return Math.abs(sourceLocation.getY() - targetLocation.getY());
		int topY  = getTopArcHex(sourceLocation, targetLocation).getY();
		int bottomY = getBottomArcHex(sourceLocation, targetLocation).getY();
		if (targetLocation.getY() >= topY && targetLocation.getY() <= bottomY)
			return xDiff;
		return targetLocation.getY() < topY ? xDiff + (topY - targetLocation.getY())
		                                     : xDiff + (targetLocation.getY() - bottomY);
	}

	/**
	 * Range between two units. A unit with no hex — a ship that has disengaged keeps its
	 * entry but loses its location (C7.1) — is unreachable rather than an error, so every
	 * range test against it simply fails.
	 */
	public static int getRange(Marker source, Marker target) {
		return getRange(source.getLocation(), target.getLocation());
	}

	// Return 1 of 12 numbers. These numbers are the 6 arcs AND
	// the 6 spaces between said arcs.

	public static int getAbsoluteArc(Marker source, Marker target) {

		Location sourceLocation = source.getLocation();
		Location targetLocation = target.getLocation();
		if (sourceLocation == null || targetLocation == null) return 0; // off the map (C7.1)

		// If target directly 'above' the source, arc is 1
		// If directly 'below' the source, arc is 7
		if (sourceLocation.getX() == targetLocation.getX()) {
			// Target and source in same hex.
			if (targetLocation.equals(sourceLocation)) {
				return 0;
				// Target above source
			} else if (targetLocation.getY() < sourceLocation.getY()) {
				return 1;
				// Target below source
			} else {
				return 7;
			}
		}

		// Get the spine hex that lines up vertically with the target.
		Location topSpine = getTopArcHex(sourceLocation, targetLocation);
		Location bottomSpine = getBottomArcHex(sourceLocation, targetLocation);

		// If target is to the left, get the 2 left spines (9 and 11)
		// for comparison.
		if (targetLocation.getX() < sourceLocation.getX()) {
			// Exactly left top spine is direction 11
			if (targetLocation.equals(topSpine)) {
				return 11;
			}
			// Exactly left bottom spine is direction 9
			if (targetLocation.equals(bottomSpine)) {
				return 9;
			}

			// Between 2 left spines is 10
			if (targetLocation.getY() > topSpine.getY() && targetLocation.getY() < bottomSpine.getY()) {
				return 10;
			} else {
				// Above top spine is 12
				if (targetLocation.getY() < topSpine.getY()) {
					return 12;
				} else {
					return 8;
				}
			}

			// Target is to the right (2,3,4,5,6)
		} else {
			// Exactly left top spine is direction 3
			if (targetLocation.equals(topSpine)) {
				return 3;
			}
			// Exactly left bottom spine is direction 5
			if (targetLocation.equals(bottomSpine)) {
				return 5;
			}

			// Between 2 left spines is 4
			if (targetLocation.getY() > topSpine.getY() && targetLocation.getY() < bottomSpine.getY()) {
				return 4;
			} else {
				// Above top spine is 2
				if (targetLocation.getY() < topSpine.getY()) {
					return 2;
				} else {
					return 6;
				}
			}
		}
	}

	// Shield facings are along the vertices of the hex, rather than the flat sides.
	// 1 (shield 1)
	// 2 (shield 1/2 border)
	// 3 (shield 2)
	// 4 (shield 2/3 border)
	// 5 (shield 3)
	// 6 (shield 3/4 border)
	// 7 (shield 4)
	// 8 (shield 4/5 border)
	// 9 (shield 5)
	// 10 (shield 5/6 border)
	// 11 (shield 6)
	// 12 (shield 6/1 border)
	public static int getAbsoluteShieldFacing(Marker source, Marker target) {
		return getAbsoluteShieldFacing(source.getLocation(), target.getLocation());
	}

	/**
	 * Location form: which of {@code source}'s six sides — in the 12-point vertex
	 * scheme (odd = a side dead-on, even = a vertex between two sides) — faces
	 * {@code target}. Same geometry as the Marker overload, exposed on raw hexes
	 * so planet-face logic can use it.
	 */
	public static int getAbsoluteShieldFacing(Location sourceLocation, Location targetLocation) {
		if (sourceLocation == null || targetLocation == null) return 0; // off the map (C7.1)
		// If in the same hex, special conditions exist.
		if (targetLocation.equals(sourceLocation)) {
			return 0;
		}

		// If the target is directly to the left or right (and an even number of x
		// offsets)
		// Then it is either 10 or 4.
		// Get the border hex that lines up with the target's X coord.
		// If yes and to the left, arc is 10
		// If yes and to the right, arc is 4
		boolean evenOffset = (Math.abs(sourceLocation.getX() - targetLocation.getX()) % 2 == 0);

		// Determine if the target is EXACTLY ON the 10/4 line
		if (evenOffset && sourceLocation.getY() == targetLocation.getY()) {
			if (targetLocation.getX() < sourceLocation.getX()) {
				return 10;
			} else {
				return 4;
			}
		}

		// Calculate if the target is above or below the 10/4 line
		boolean aboveTheLine = isAboveTheLine(sourceLocation, targetLocation);

		// Fetch the spine hex that is closest to the target hex
		Location spineHex = getShieldSpineHex(sourceLocation, targetLocation);
		// System.out.println("Spine Hex for this offset is: " + spineHex.getX() + "|" +
		// spineHex.getY());

		if (aboveTheLine) {

			// target is above the top spine hex, regardless of left or right
			// it is in shield 1.
			if (targetLocation.getY() < spineHex.getY()) {
				return 1;

				// target IS the spine hex.
			} else if (targetLocation.equals(spineHex)) {

				// target is left spine hex, therefore 12
				if (targetLocation.getX() < sourceLocation.getX()) {
					return 12;

					// target is right spine hex, therefore 2
				} else {
					return 2;
				}

				// target is below the top spine hex, regardless of left or right
			} else {

				// to the left, return 11
				if (targetLocation.getX() < sourceLocation.getX()) {
					return 11;

					// to the right, return 3
				} else {
					return 3;
				}
			}

		} else {
			// target is BELOW the bottom spine hex, regardless of left or right
			// it is in shield 7.
			if (targetLocation.getY() > spineHex.getY()) {
				return 7;

				// target IS the spine hex.
			} else if (targetLocation.equals(spineHex)) {

				// target is left spine hex, therefore 8
				if (targetLocation.getX() < sourceLocation.getX()) {
					return 8;

					// target is right spine hex, therefore 6
				} else {
					return 6;
				}

				// target is ABOVE the top spine hex, regardless of left or right
			} else {

				// to the left, return 9
				if (targetLocation.getX() < sourceLocation.getX()) {
					return 9;

					// to the right, return 5
				} else {
					return 5;
				}
			}
		}
	}

	/**
	 * The hex sides (1..6, A..F) of a planet hex that are visible from an
	 * observing hex — the near hemisphere of the convex hex, and the bulk hides
	 * the rest. Looking straight at a side (odd absolute facing) you see that
	 * side and its two neighbours (3); looking straight at a vertex (even facing)
	 * you see only the two sides that meet there (2); grazing sides are not seen.
	 * Empty when the observer shares the planet's hex or either is null.
	 *
	 * <p>General geometry for any interaction with a particular planet face —
	 * transporter/shuttle recovery of a surface party (SH50.46), face-specific
	 * fire, etc.
	 */
	public static java.util.Set<Integer> visiblePlanetSides(Location planet, Location observer) {
		java.util.Set<Integer> sides = new java.util.HashSet<>();
		if (planet == null || observer == null || planet.equals(observer)) {
			return sides;
		}
		int facing = getAbsoluteShieldFacing(planet, observer); // planet's side toward the observer
		if (facing == 0) {
			return sides;
		}
		if (facing % 2 == 1) {
			// Odd → a side dead-on: that side and its two neighbours.
			int side = (facing + 1) / 2;
			sides.add(wrapSide(side - 1));
			sides.add(side);
			sides.add(wrapSide(side + 1));
		} else {
			// Even → a vertex: only the two sides that meet at it.
			int lower = facing / 2;
			sides.add(lower);
			sides.add(wrapSide(lower + 1));
		}
		return sides;
	}

	/** Whether hex side {@code side} (1..6) of a planet is visible from {@code observer}. */
	public static boolean isPlanetSideVisible(Location planet, Location observer, int side) {
		return visiblePlanetSides(planet, observer).contains(side);
	}

	/** Keep a hex side value in the 1..6 range (wraps A..F). */
	private static int wrapSide(int side) {
		return ((side - 1) % 6 + 6) % 6 + 1;
	}

	// Get the relative shield facing (the actual shield number from the ship's
	// point of view)
	/**
	 * Given an absolute (map-based) shield facing and the heading of the unit,
	 * determine the shield facing value relative to the ship.
	 * 
	 * @param absoluteShieldfacing Map-based absolute shield facing
	 * @param unitHeading          The heading of the unit (map direction in which
	 *                             it is pointed)
	 * @return
	 */
	public static int getRelativeShieldFacing(int absoluteShieldfacing, int unitHeading) {
		int returnValue = 0;

		switch (unitHeading) {
			case 1:
				returnValue = absoluteShieldfacing;
				break;
			case 5:
				switch (absoluteShieldfacing) {
					case 1:  returnValue = 11; break;
					case 2:  returnValue = 12; break;
					case 3:  returnValue = 1;  break;
					case 4:  returnValue = 2;  break;
					case 5:  returnValue = 3;  break;
					case 6:  returnValue = 4;  break;
					case 7:  returnValue = 5;  break;
					case 8:  returnValue = 6;  break;
					case 9:  returnValue = 7;  break;
					case 10: returnValue = 8;  break;
					case 11: returnValue = 9;  break;
					case 12: returnValue = 10; break;
					default: break;
				}
				break;
			case 9:
				switch (absoluteShieldfacing) {
					case 1:  returnValue = 9;  break;
					case 2:  returnValue = 10; break;
					case 3:  returnValue = 11; break;
					case 4:  returnValue = 12; break;
					case 5:  returnValue = 1;  break;
					case 6:  returnValue = 2;  break;
					case 7:  returnValue = 3;  break;
					case 8:  returnValue = 4;  break;
					case 9:  returnValue = 5;  break;
					case 10: returnValue = 6;  break;
					case 11: returnValue = 7;  break;
					case 12: returnValue = 8;  break;
					default: break;
				}
				break;
			case 13:
				switch (absoluteShieldfacing) {
					case 1:  returnValue = 7;  break;
					case 2:  returnValue = 8;  break;
					case 3:  returnValue = 9;  break;
					case 4:  returnValue = 10; break;
					case 5:  returnValue = 11; break;
					case 6:  returnValue = 12; break;
					case 7:  returnValue = 1;  break;
					case 8:  returnValue = 2;  break;
					case 9:  returnValue = 3;  break;
					case 10: returnValue = 4;  break;
					case 11: returnValue = 5;  break;
					case 12: returnValue = 6;  break;
					default: break;
				}
				break;
			case 17:
				switch (absoluteShieldfacing) {
					case 1:  returnValue = 5;  break;
					case 2:  returnValue = 6;  break;
					case 3:  returnValue = 7;  break;
					case 4:  returnValue = 8;  break;
					case 5:  returnValue = 9;  break;
					case 6:  returnValue = 10; break;
					case 7:  returnValue = 11; break;
					case 8:  returnValue = 12; break;
					case 9:  returnValue = 1;  break;
					case 10: returnValue = 2;  break;
					case 11: returnValue = 3;  break;
					case 12: returnValue = 4;  break;
					default: break;
				}
				break;
			case 21:
				switch (absoluteShieldfacing) {
					case 1:  returnValue = 3;  break;
					case 2:  returnValue = 4;  break;
					case 3:  returnValue = 5;  break;
					case 4:  returnValue = 6;  break;
					case 5:  returnValue = 7;  break;
					case 6:  returnValue = 8;  break;
					case 7:  returnValue = 9;  break;
					case 8:  returnValue = 10; break;
					case 9:  returnValue = 11; break;
					case 10: returnValue = 12; break;
					case 11: returnValue = 1;  break;
					case 12: returnValue = 2;  break;
					default: break;
				}
				break;
			default:
				break;
		}

		return returnValue;
	}

	// Return the top (3 or 11) arc hex with the same x-coord as the target.
	private static Location getTopArcHex(Location sourceLocation, Location targetLocation) {
		// If in the 3/5 or 9/11 zone then the range
		// is simply the xDiff

		// Odd and even X coords will have different calculations
		boolean even = (sourceLocation.getX() % 2 == 0);
		// Horizontal offsets
		int xDiff = Math.abs(targetLocation.getX() - sourceLocation.getX());

		int topY = 0;

		if (even) {
			topY = sourceLocation.getY() - xDiff / 2;
		} else {
			topY = sourceLocation.getY() - (xDiff + 1) / 2;
		}

		return new Location(targetLocation.getX(), topY);
	}

	// Return the bottom (5 or 9) arc hex with the same x-coord as the target.
	private static Location getBottomArcHex(Location sourceLocation, Location targetLocation) {
		// If in the 3/5 or 9/11 zone then the range
		// is simply the xDiff

		// Odd and even X coords will have different calculations
		boolean even = (sourceLocation.getX() % 2 == 0);
		// Horizontal offsets
		int xDiff = Math.abs(targetLocation.getX() - sourceLocation.getX());

		int bottomY = 0;

		if (even) {
			bottomY = sourceLocation.getY() + (xDiff + 1) / 2;

		} else {
			bottomY = sourceLocation.getY() + xDiff / 2;
		}

		return new Location(targetLocation.getX(), bottomY);
	}

	private static Location getShieldSpineHex(Location sourceLocation, Location targetLocation) {

		// Calculate if the target is above or below the 10/4 line
		boolean aboveTheLine = false;
		boolean xEven = sourceLocation.getX() % 2 == 0;
		int xOffset = Math.abs(sourceLocation.getX() - targetLocation.getX());
		int yOffset = 0;

		if (xEven) {
			aboveTheLine = (targetLocation.getY() <= sourceLocation.getY());
		} else {
			aboveTheLine = (targetLocation.getY() < sourceLocation.getY());
		}

		if (aboveTheLine) {
			// Target is above the 10/4 line.

			// Calculate the shield-spine Y-value for the given x-offset
			if (xOffset % 2 == 0) {
				yOffset = xOffset / 2 + xOffset;
			} else {
				if (xEven) {
					yOffset = getSmallOddOffset(xOffset);
				} else {
					yOffset = getLargeOddOffset(xOffset);
				}
			}

			// Above the line means the y offset should be negative (up)
			yOffset = -yOffset;

		} else {
			// Target is below the 10/4 line.

			// Calculate the shield-spine Y-value for the given x-offset.
			if (xOffset % 2 == 0) {
				yOffset = xOffset / 2 + xOffset;
			} else {
				if (xEven) {
					yOffset = getLargeOddOffset(xOffset);
				} else {
					yOffset = getSmallOddOffset(xOffset);
				}
			}

			// System.out.println("BELOW OFFSET: " + yOffset);
		}

		return new Location(targetLocation.getX(), (sourceLocation.getY() + yOffset));

	}

	// Return the top () spine hex with the same x-coord as the target.
	private static Location getTopSpineHex(Location sourceLocation, Location targetLocation) {

		// For EVEN values of X, we have this pattern for the
		// "spinal" hexes.
		// Direction | X-Offset | Y-Offset
		// Up | 1 | 1
		// Up | 2 | 3
		// Up | 3 | 4
		// Up | 4 | 6
		// Up | 5 | 7
		// Up | 6 | 9
		// Down | 1 | 2
		// Down | 2 | 3
		// Down | 3 | 5
		// Down | 4 | 6
		// Down | 5 | 8
		// Down | 6 | 9

		// For ODD values of X, we have this pattern for the
		// "spinal" hexes.
		// Direction | X-Offset | Y-Offset
		// Up | 1 | 2
		// Up | 2 | 3
		// Up | 3 | 5
		// Up | 4 | 6
		// Up | 5 | 8
		// Up | 6 | 9
		// Down | 1 | 1
		// Down | 2 | 3
		// Down | 3 | 4
		// Down | 4 | 6
		// Down | 5 | 7
		// Down | 6 | 9

		// Thus the EVEN offsets for Up are the same as the ODD offsets for Down, and
		// vice versa.
		boolean sourceEvenX = (sourceLocation.getX() % 2 == 0);
		boolean targetEvenX = (targetLocation.getX() % 2 == 0);

		// Check to see if the target is along the L/R spines
		// If they have the same Y coord and are both even or both odd, then it is on
		// the
		// LR spine. Thus return the target location.
		if (sourceLocation.getY() == targetLocation.getY() && sourceEvenX == targetEvenX) {
			return targetLocation;
		}

		return null;
	}

	// Returns the yOffset ofr a shield spine
	// using the large values (even/down, odd/up)
	private static int getLargeOddOffset(int xOffset) {
		int yOffset = 2;
		for (int i = 1; i < xOffset; i = i + 2) {
			yOffset = yOffset + 3;
		}

		return yOffset;
	}

	// Returns the yOffset for a shield spine
	// using the small values (even/up, odd/down)
	private static int getSmallOddOffset(int xOffset) {
		int yOffset = 1;
		for (int i = 1; i < xOffset; i = i + 2) {
			yOffset = yOffset + 3;
		}

		return yOffset;

	}

	// Return the direction value of 1..24, or 0 for same hex.
	public static int getBearing(Location sourceLocation, Location targetLocation) {
		if (sourceLocation == null || targetLocation == null) return 0;
		if (sourceLocation.equals(targetLocation)) return 0;
		int xOffset = targetLocation.getX() - sourceLocation.getX();
		if (xOffset == 0)
			return targetLocation.getY() < sourceLocation.getY() ? 1 : 13;
		boolean evenOffset = (Math.abs(xOffset) % 2 == 0);
		if (evenOffset && sourceLocation.getY() == targetLocation.getY())
			return xOffset < 0 ? 10 : 4;
		Location topSpine    = getTopArcHex(sourceLocation, targetLocation);
		Location bottomSpine = getBottomArcHex(sourceLocation, targetLocation);
		int topY    = topSpine.getY();
		int bottomY = bottomSpine.getY();
		if (xOffset > 0) {
			if (targetLocation.getY() < topY)    return 2;
			if (targetLocation.getY() == topY)   return 3;
			if (targetLocation.getY() <= bottomY) return 4;
			if (targetLocation.getY() == bottomY + 1) return 5;
			return 6;
		} else {
			if (targetLocation.getY() < topY)    return 24;
			if (targetLocation.getY() == topY)   return 23;
			if (targetLocation.getY() <= bottomY) return 22;
			if (targetLocation.getY() == bottomY + 1) return 21;
			return 20;
		}
	}

	/**
	 * Pixel-geometry-accurate bearing using flat-top hex centers.
	 * Returns 1-24 (SFB directions), 0 if same hex.
	 * Use this for seeker tracking to avoid zone-boundary oscillation.
	 */
	public static int getGeometricBearing(Marker source, Marker target) {
		Location src = source.getLocation();
		Location tgt = target.getLocation();
		if (src == null || tgt == null) return 0;  // off the map (C7.1)
		if (src.equals(tgt)) return 0;

		double sqrt3 = Math.sqrt(3);
		double sx = (src.getX() - 1) * 1.5;
		double sy = (src.getY() - 1) * sqrt3 + (src.getX() % 2 == 0 ? sqrt3 / 2.0 : 0.0);
		double tx = (tgt.getX() - 1) * 1.5;
		double ty = (tgt.getY() - 1) * sqrt3 + (tgt.getX() % 2 == 0 ? sqrt3 / 2.0 : 0.0);

		double dx = tx - sx;
		double dy = sy - ty; // flip y: positive = north
		double deg = (Math.toDegrees(Math.atan2(dx, dy)) + 360) % 360;
		return ((int) Math.round(deg / 15) % 24) + 1;
	}

	public static int getBearing(Marker source, Marker target) {

		Location sourceLocation = source.getLocation();
		Location targetLocation = target.getLocation();
		if (sourceLocation == null || targetLocation == null) return 0; // off the map (C7.1)

		// If the two locations are exactly the same, we can't determint the direction.
		if (sourceLocation.equals(targetLocation)) {
			return 0;
		}

		// The real offset of the two X coords.
		// Positive means to the right.
		// Negative means to the left.
		int xOffset = targetLocation.getX() - sourceLocation.getX();

		// If the target is directly above or below the source, we have a fixed
		// direction
		if (xOffset == 0) {

			// Directly above, return 1
			if (targetLocation.getY() < sourceLocation.getY()) {
				return 1;
				// Directly below, return 13
			} else {
				return 13;
			}
		}

		// Due east or due west: an even column offset on the same row lands exactly on the
		// vertex between two hex directions. In the 24-point bearing scheme the six directions
		// are 1, 5, 9, 13, 17 and 21, so due east is the vertex between 5 and 9 (= 7) and due
		// west the vertex between 17 and 21 (= 19). (4 and 10 are the equivalents in the
		// twelve-point SHIELD scheme — a different space; see getAbsoluteShieldFacing.)
		boolean evenOffset = (Math.abs(sourceLocation.getX() - targetLocation.getX()) % 2 == 0);
		if (evenOffset && sourceLocation.getY() == targetLocation.getY()) {
			if (targetLocation.getX() < sourceLocation.getX()) {
				return 19;
			} else {
				return 7;
			}
		}

		// We know it isn't on the North/South or Left/Right spine. So let's get to
		// work!

		// Is the target 'north' of the source?
		boolean aboveTheLine = isAboveTheLine(sourceLocation, targetLocation);

		Location shieldSpineLocation = getShieldSpineHex(sourceLocation, targetLocation);

		// System.out.println("ShieldSpineLocation: " + shieldSpineLocation);

		Location upperArcLocation = getTopArcHex(sourceLocation, targetLocation);
		Location lowerArcLocation = getBottomArcHex(sourceLocation, targetLocation);

		// System.out.println("Top Arc Hex: " + upperArcLocation);
		// System.out.println("Bottom Arc Hex: " + lowerArcLocation);

		// Upper left quadrant
		if (xOffset < 0 && aboveTheLine) {
			// On the shield line
			if (targetLocation.equals(shieldSpineLocation)) {
				return 23;
				// On the arc line
			} else if (targetLocation.equals(upperArcLocation)) {
				return 21;
				// Above the shield line
			} else if (targetLocation.getY() < shieldSpineLocation.getY()) {
				return 24;
				// Below the arc line
			} else if (targetLocation.getY() > upperArcLocation.getY()) {
				return 20;
				// Between the shield line and the arc line
			} else {
				return 22;
			}
		}

		// Upper right quadrant
		if (xOffset > 0 && aboveTheLine) {
			// On the shield line
			if (targetLocation.equals(shieldSpineLocation)) {
				return 3;
				// On the arc line
			} else if (targetLocation.equals(upperArcLocation)) {
				return 5;
				// Above the shield line
			} else if (targetLocation.getY() < shieldSpineLocation.getY()) {
				return 2;
				// Below the arc line
			} else if (targetLocation.getY() > upperArcLocation.getY()) {
				return 6;
				// Between the shield line and the arc line
			} else {
				return 4;
			}
		}

		// Lower left quadrant
		if (xOffset < 0 && !aboveTheLine) {
			// On the shield line
			if (targetLocation.equals(shieldSpineLocation)) {
				return 15;
				// On the arc line
			} else if (targetLocation.equals(lowerArcLocation)) {
				return 17;
				// Below the shield line
			} else if (targetLocation.getY() > shieldSpineLocation.getY()) {
				return 14;
				// Above the arc line
			} else if (targetLocation.getY() < lowerArcLocation.getY()) {
				return 18;
				// Between arc and shield lines
			} else {
				return 16;
			}
		}

		// Lower right quadrant
		if (xOffset > 0 && !aboveTheLine) {
			// On the shield line
			if (targetLocation.equals(shieldSpineLocation)) {
				return 11;
				// On the arc line
			} else if (targetLocation.equals(lowerArcLocation)) {
				return 9;
				// Below the shield line
			} else if (targetLocation.getY() > shieldSpineLocation.getY()) {
				return 12;
				// Above the arc line
			} else if (targetLocation.getY() < lowerArcLocation.getY()) {
				return 8;
				// Between arc and shield lines
			} else {
				return 10;
			}
		}

		return 99;
	}

	// Assuming a ship is pointing due north, this returns true if the target
	// is in the FH (to the North of the source).
	private static boolean isAboveTheLine(Location sourceLocation, Location targetLocation) {
		boolean aboveTheLine = false;
		boolean xEven = sourceLocation.getX() % 2 == 0;

		if (xEven) {
			aboveTheLine = (targetLocation.getY() <= sourceLocation.getY());
		} else {
			aboveTheLine = (targetLocation.getY() < sourceLocation.getY());
		}

		return aboveTheLine;

	}

	// Given the true (map-oriented) bearing and the facing of the source
	// Give the relative bearing, with the front of the source as the "1" bearing.
	public static int getRelativeBearing(int trueBearing, int facing) {
		if (facing == 1) {
			return trueBearing;
		}

		int adjustDown = facing - 1;
		int adjustUp = 24 - adjustDown;

		if (trueBearing >= facing) {
			return trueBearing - adjustDown;
		} else {
			return trueBearing + adjustUp;
		}

	}

	/**
	 * Given the relative (ship-centric) bearing and the ship facing, find
	 * out the true (map-centric) bearing.
	 * 
	 * @param relativeBearing The bearing relative to the front of the ship
	 * @param facing          The map-relative facing of the ship.
	 * @return The true bearing of the direction desired.
	 */
	public static int getTrueBearing(int relativeBearing, int facing) {

		// If the ship is facing "true north" then the relative and
		// true bearings are the same.
		if (facing == 1) {
			return relativeBearing;
		}

		// Ship 5
		// 1->5, 5->9, 9->13, 13->17, 17->21, 21->1

		// Ship 9
		// 1->9, 5->13, 9->17, 13->21, 17->1, 21->5

		// Ship 13
		// 1->13, 5->17, 9->21, 13->1, 17->5, 21->9

		// So, take the facing and subtract 1.
		// Then add this to the facing to get the true bearing.
		int adjustmentValue = facing - 1;
		int trueBearing = relativeBearing + adjustmentValue;
		// If we've spilled past 24, adjust
		if (trueBearing > 24) {
			trueBearing = trueBearing % 24;
		}

		return trueBearing;
	}

	/**
	 * Get the coordinates of an adjacent hex in a particular direction.
	 * 
	 * @param sourceLocation The hex from which we are measuring.
	 * @param trueBearing    The map direction of the adjacent hex
	 * @return The coordinates of the desired adjacent hex.
	 */
	public static Location getAdjacentHex(Location sourceLocation, int trueBearing, int maxCols, int maxRows) {
		Location loc = getAdjacentHex(sourceLocation, trueBearing);
		if (loc == null) return null;
		if (loc.getX() < 1 || loc.getX() > maxCols || loc.getY() < 1 || loc.getY() > maxRows) return null;
		return loc;
	}

	public static Location getAdjacentHex(Location sourceLocation, int trueBearing) {
		Location newLocation = new Location();

		// Odd and even X coords will have different calculations
		boolean even = (sourceLocation.getX() % 2 == 0);

		switch (trueBearing) {
			case 1:
				// Hex in direction 1 is simply subtract 1 from Y coord.
				newLocation.setX(sourceLocation.getX());
				newLocation.setY(sourceLocation.getY() - 1);
				break;
			case 5:
				newLocation.setX(sourceLocation.getX() + 1);
				// If even X coord (x+1, y)
				if (even) {
					newLocation.setY(sourceLocation.getY());
					// If odd X coord (x+1, y - 1)
				} else {
					newLocation.setY(sourceLocation.getY() - 1);
				}
				break;
			case 9:
				newLocation.setX(sourceLocation.getX() + 1);
				// if even coord (x+1, y + 1)
				if (even) {
					newLocation.setY(sourceLocation.getY() + 1);
				} else {
					newLocation.setY(sourceLocation.getY());
				}
				break;
			case 13:
				// Hex in direction 13 is simply add 1 to Y coord.
				newLocation.setX(sourceLocation.getX());
				newLocation.setY(sourceLocation.getY() + 1);
				break;
			case 17:
				newLocation.setX(sourceLocation.getX() - 1);
				// if even coord (x-1, y+1)
				if (even) {
					newLocation.setY(sourceLocation.getY() + 1);
				} else {
					newLocation.setY(sourceLocation.getY());
				}
				break;
			case 21:
				newLocation.setX(sourceLocation.getX() - 1);
				// If even X coord (x-1, y)
				if (even) {
					newLocation.setY(sourceLocation.getY());
					// If odd X coord (x-1, y - 1)
				} else {
					newLocation.setY(sourceLocation.getY() - 1);
				}
				break;
			default:
				// Something went wrong and a bad bearing was sent.
				newLocation = null;
				break;
		}

		return newLocation;
	}

	/**
	 * In SFB, a facing is represented by a letter A-F.
	 * 
	 * @param facing The map-relative facing.
	 * @return The letter representing the facing.
	 */
	public static String getFacingLetter(int facing) {
		switch (facing) {
			case 1:
				return "A";
			case 5:
				return "B";
			case 9:
				return "C";
			case 13:
				return "D";
			case 17:
				return "E";
			case 21:
				return "F";
			default:
				return "?";
		}
	}

	// -------------------------------------------------------------------------
	// Hex-line enumeration (for terrain ECM counting along a line of fire)
	// -------------------------------------------------------------------------

	/**
	 * The hexes a center-to-center line from {@code a} to {@code b} passes
	 * through, inclusive of both endpoints — the standard cube hex-line draw
	 * (one hex per step, so a line grazing a shared edge yields a single hex,
	 * matching P3.33's "counts only one"). Used to COUNT terrain hexes for the
	 * ECM magnitude (P3.33/P2.223); this is not a legality test, so unlike the
	 * exact-integer LosUtils geometry it may use interpolation.
	 */
	public static List<Location> hexLine(Location a, Location b) {
		List<Location> line = new ArrayList<>();
		if (a == null || b == null)
			return line;
		int n = getRange(a, b);
		int[] ac = toCube(a);
		int[] bc = toCube(b);
		Location prev = null;
		for (int i = 0; i <= n; i++) {
			double t = n == 0 ? 0.0 : (double) i / n;
			Location hex = cubeRound(
					ac[0] + (bc[0] - ac[0]) * t,
					ac[1] + (bc[1] - ac[1]) * t,
					ac[2] + (bc[2] - ac[2]) * t);
			if (prev == null || !hex.equals(prev)) { // guard against duplicate steps
				line.add(hex);
				prev = hex;
			}
		}
		return line;
	}

	// Offset (even-q: even columns shifted down) → cube {x, y, z}.
	private static int[] toCube(Location h) {
		int x = h.getX();
		int z = h.getY() - (h.getX() + (h.getX() & 1)) / 2;
		return new int[] { x, -x - z, z };
	}

	private static Location fromCube(int x, int z) {
		return new Location(x, z + (x + (x & 1)) / 2);
	}

	private static Location cubeRound(double x, double y, double z) {
		int rx = (int) Math.round(x);
		int ry = (int) Math.round(y);
		int rz = (int) Math.round(z);
		double dx = Math.abs(rx - x);
		double dy = Math.abs(ry - y);
		double dz = Math.abs(rz - z);
		if (dx > dy && dx > dz)
			rx = -ry - rz;
		else if (dy > dz)
			ry = -rx - rz;
		else
			rz = -rx - ry;
		return fromCube(rx, rz);
	}
}
