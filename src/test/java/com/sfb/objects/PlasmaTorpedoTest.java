package com.sfb.objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static com.sfb.objects.Seeker.SeekerType.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.properties.PlasmaType;
import com.sfb.properties.TurnMode;
import com.sfb.properties.WeaponArmingType;

public class PlasmaTorpedoTest {
	
	private PlasmaTorpedo plasma;
	private PlasmaTorpedo target;
	
	@Before
	public void setUp() {
		plasma = new PlasmaTorpedo(PlasmaType.R, WeaponArmingType.STANDARD);
		target = new PlasmaTorpedo(PlasmaType.F, WeaponArmingType.STANDARD);
	}
	
	// --- Constructor and Initialization Tests ---
	
	@Test
	public void testConstructorWithPlasmaR() {
		PlasmaTorpedo r = new PlasmaTorpedo(PlasmaType.R, WeaponArmingType.STANDARD);
		assertEquals(PlasmaType.R, r.getPlasmaType());
		assertEquals(WeaponArmingType.STANDARD, r.getArmingType());
		assertEquals(TurnMode.Seeker, r.getTurnMode());
	}
	
	@Test
	public void testConstructorWithDifferentPlasmaTypes() {
		PlasmaTorpedo typeS = new PlasmaTorpedo(PlasmaType.S, WeaponArmingType.STANDARD);
		PlasmaTorpedo typeG = new PlasmaTorpedo(PlasmaType.G, WeaponArmingType.STANDARD);
		PlasmaTorpedo typeD = new PlasmaTorpedo(PlasmaType.D, WeaponArmingType.STANDARD);
		PlasmaTorpedo typeF = new PlasmaTorpedo(PlasmaType.F, WeaponArmingType.STANDARD);
		
		assertEquals(PlasmaType.S, typeS.getPlasmaType());
		assertEquals(PlasmaType.G, typeG.getPlasmaType());
		assertEquals(PlasmaType.D, typeD.getPlasmaType());
		assertEquals(PlasmaType.F, typeF.getPlasmaType());
	}
	
	@Test
	public void testConstructorWithDifferentArmingTypes() {
		PlasmaTorpedo normal = new PlasmaTorpedo(PlasmaType.R, WeaponArmingType.STANDARD);
		PlasmaTorpedo overload = new PlasmaTorpedo(PlasmaType.R, WeaponArmingType.OVERLOAD);
		
		assertEquals(WeaponArmingType.STANDARD, normal.getArmingType());
		assertEquals(WeaponArmingType.OVERLOAD, overload.getArmingType());
	}
	
	@Test
	public void testConstructorSetsSpeed() {
		assertEquals(32, plasma.getSpeed());  // SPEED constant from ctor
	}
	
	@Test
	public void testSetEnduranceNoOp() {
		plasma.setEndurance(100);
		assertEquals(50, plasma.getEndurance());  // Still table-driven
	}
	
	@Test
	public void testSetWarheadDamageNoOp() {
		plasma.setWarheadDamage(100);
		assertEquals(50, plasma.getWarheadDamage());  // Still current strength
	}
	
	// --- Damage Table Tests ---
	
	@Test
	public void testPlasmaTorpedoInitialStrength() {
		// Plasma-R should start with 50 damage at range 0
		assertEquals(50, plasma.getCurrentStrength());
	}
	
	@Test
	public void testDifferentPlasmaTypesHaveDifferentInitialStrength() {
		PlasmaTorpedo typeS = new PlasmaTorpedo(PlasmaType.S, WeaponArmingType.STANDARD);
		PlasmaTorpedo typeG = new PlasmaTorpedo(PlasmaType.G, WeaponArmingType.STANDARD);
		PlasmaTorpedo typeD = new PlasmaTorpedo(PlasmaType.D, WeaponArmingType.STANDARD);
		PlasmaTorpedo typeF = new PlasmaTorpedo(PlasmaType.F, WeaponArmingType.STANDARD);
		
		// From the damage tables
		assertEquals(30, typeS.getCurrentStrength());
		assertEquals(20, typeG.getCurrentStrength());
		assertEquals(10, typeD.getCurrentStrength());
		assertEquals(20, typeF.getCurrentStrength());
	}
	
	// --- Distance Traveled Tests ---
	
	@Test
	public void testIncrementDistance() {
		assertEquals(0, plasma.getDistanceTraveled());
		
		plasma.incrementDistance();
		assertEquals(1, plasma.getDistanceTraveled());
		
		plasma.incrementDistance();
		assertEquals(2, plasma.getDistanceTraveled());
	}
	
	@Test
	public void testStrengthDecreaseWithDistance() {
		// Plasma-R: [50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 35, ...]
		assertEquals(50, plasma.getCurrentStrength());
		
		// Travel 11 impulses (distance index 11)
		for (int i = 0; i < 11; i++) {
			plasma.incrementDistance();
		}
		assertEquals(11, plasma.getDistanceTraveled());
		assertEquals(35, plasma.getCurrentStrength());
	}
	
	@Test
	public void testStrengthBecomesZeroAfterTable() {
		PlasmaTorpedo typeF = new PlasmaTorpedo(PlasmaType.F, WeaponArmingType.STANDARD);
		// Plasma-F table has 16 entries (0-15), so at distance 16+ should be 0
		
		// Travel to the end of the table
		for (int i = 0; i < 16; i++) {
			typeF.incrementDistance();
		}
		assertEquals(0, typeF.getCurrentStrength());
	}
	
	// --- Phaser Damage Tests ---
	
	@Test
	public void testApplyPhaserDamage() {
		assertEquals(0.0, plasma.getDamageTaken(), 0.001);
		
		// Apply 2 points of phaser damage
		plasma.applyPhaserDamage(2);
		assertEquals(1.0, plasma.getDamageTaken(), 0.001);
	}
	
	@Test
	public void testPhaserDamageReducesStrength() {
		// Start with 50 damage
		assertEquals(50, plasma.getCurrentStrength());
		
		// Apply 4 phaser points (= 2 damage reduction)
		plasma.applyPhaserDamage(4);
		
		// Strength should be reduced by 2
		assertEquals(48, plasma.getCurrentStrength());
	}
	
	@Test
	public void testPhaserDamageCanReduceStrengthToZero() {
		// Plasma-R at range 0 has 50 strength
		assertEquals(50, plasma.getCurrentStrength());
		
		// Apply enough phaser damage to exceed the current strength
		plasma.applyPhaserDamage(200); // 100 damage reduction
		
		// Strength should be 0 (clamped by Math.max)
		assertEquals(0, plasma.getCurrentStrength());
	}
	
	@Test
	public void testPhaserDamageWithDistance() {
		// Start with 50 damage
		assertEquals(50, plasma.getCurrentStrength());
		
		// Travel 11 impulses to reduce base damage to 35
		for (int i = 0; i < 11; i++) {
			plasma.incrementDistance();
		}
		assertEquals(35, plasma.getCurrentStrength());
		
		// Apply 10 phaser points (= 5 damage reduction)
		plasma.applyPhaserDamage(10);
		
		// Strength should be 35 - 5 = 30
		assertEquals(30, plasma.getCurrentStrength());
	}
	
	// --- Seeker Interface Tests ---
	
	@Test
	public void testSetAndGetTarget() {
		assertNull(plasma.getTarget());
		
		plasma.setTarget(target);
		assertEquals(target, plasma.getTarget());
	}
	
	@Test
	public void testSelfGuiding() {
		assertFalse(plasma.isSelfGuiding());
		
		plasma.setSelfGuiding(true);
		assertTrue(plasma.isSelfGuiding());
		
		plasma.setSelfGuiding(false);
		assertFalse(plasma.isSelfGuiding());
	}
	
	@Test
	public void testLaunchImpulse() {
		assertEquals(0, plasma.getLaunchImpulse());
		
		plasma.setLaunchImpulse(5);
		assertEquals(5, plasma.getLaunchImpulse());
	}
	
	@Test
	public void testWarheadDamage() {
		// Warhead damage should match current strength
		assertEquals(50, plasma.getWarheadDamage());
		
		plasma.incrementDistance();
		plasma.incrementDistance();
		assertEquals(50, plasma.getWarheadDamage());
		
		// Apply phaser damage
		plasma.applyPhaserDamage(10);
		assertEquals(45, plasma.getWarheadDamage());
	}
	
	@Test
	public void testEndurance() {
		// Endurance should match current strength
		assertEquals(50, plasma.getEndurance());
		
		// Travel distance
		plasma.incrementDistance();
		assertEquals(50, plasma.getEndurance());
		
		// Apply phaser damage
		plasma.applyPhaserDamage(20);
		assertEquals(40, plasma.getEndurance());
	}
	
	@Test
	public void testController() {
		assertNull(plasma.getController());
		
		PlasmaTorpedo controller = new PlasmaTorpedo(PlasmaType.F, WeaponArmingType.STANDARD);
		plasma.setController(controller);
		assertEquals(controller, plasma.getController());
	}
	
	@Test
	public void testIdentified() {
		assertFalse(plasma.isIdentified());
		
		plasma.identify();
		assertTrue(plasma.isIdentified());
	}
	
	// --- Arming Type Tests ---
	
	@Test
	public void testIsEnveloping() {
		PlasmaTorpedo normal = new PlasmaTorpedo(PlasmaType.R, WeaponArmingType.STANDARD);
		PlasmaTorpedo overload = new PlasmaTorpedo(PlasmaType.R, WeaponArmingType.OVERLOAD);
		
		assertFalse(normal.isEnveloping());
		assertTrue(overload.isEnveloping());
	}
	
	@Test
	public void testIsEnvelopingSpecial() {
		PlasmaTorpedo special = new PlasmaTorpedo(PlasmaType.R, WeaponArmingType.SPECIAL);
		assertFalse(special.isEnveloping());  // Only OVERLOAD
	}
	
	// --- Impact Test ---
	
	@Test
	public void testImpact() {
		// Impact should return current strength
		assertEquals(50, plasma.impact());
		
		// After applying damage, impact returns reduced strength
		plasma.applyPhaserDamage(10);
		assertEquals(45, plasma.impact());
		
		// After traveling, impact returns reduced strength
		for (int i = 0; i < 11; i++) {
			plasma.incrementDistance();
		}
		// Strength at distance 11 is 35, minus 5 damage already applied = 30
		assertEquals(30, plasma.impact());
	}
	
	// --- Edge Cases and Integration Tests ---
	
	@Test
	public void testMultiplePhaserApplications() {
		assertEquals(50, plasma.getCurrentStrength());
		
		plasma.applyPhaserDamage(4); // 2 damage
		assertEquals(48, plasma.getCurrentStrength());
		
		plasma.applyPhaserDamage(6); // 3 more damage
		assertEquals(45, plasma.getCurrentStrength());
		
		plasma.applyPhaserDamage(90); // 45 more damage (exceeds remaining)
		assertEquals(0, plasma.getCurrentStrength());
	}
	
	@Test
	public void testCombinedDistanceAndPhaserDamage() {
		// Plasma-R damage table: [50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 35, 35, ...]
		assertEquals(50, plasma.getCurrentStrength());
		
		// Travel 5 impulses (still 50)
		for (int i = 0; i < 5; i++) {
			plasma.incrementDistance();
		}
		assertEquals(50, plasma.getCurrentStrength());
		
		// Apply 10 phaser points (5 damage)
		plasma.applyPhaserDamage(10);
		assertEquals(45, plasma.getCurrentStrength());
		
		// Travel 6 more impulses (to index 11, where base damage is 35)
		for (int i = 0; i < 6; i++) {
			plasma.incrementDistance();
		}
		// 35 - 5 = 30
		assertEquals(30, plasma.getCurrentStrength());
	}
	
	@Test
	public void testPlasmaDTypeWeakest() {
		PlasmaTorpedo typeD = new PlasmaTorpedo(PlasmaType.D, WeaponArmingType.STANDARD);
		
		// Plasma-D starts with only 10 damage
		assertEquals(10, typeD.getCurrentStrength());
		
		// And exhausts quickly
		for (int i = 0; i < 16; i++) {
			typeD.incrementDistance();
		}
		assertEquals(0, typeD.getCurrentStrength());
	}
	
	@Test
	public void testPlasmaRStrongest() {
		PlasmaTorpedo typeR = new PlasmaTorpedo(PlasmaType.R, WeaponArmingType.STANDARD);
		
		// Plasma-R can travel further with damage potential
		// Table has 32 entries, maintaining reasonable damage longer than others
		assertEquals(50, typeR.getCurrentStrength());
		
		// At distance 30, should have 1 damage remaining (table[30]=1)
		for (int i = 0; i < 30; i++) {
			typeR.incrementDistance();
		}
		assertEquals(1, typeR.getCurrentStrength());
		
		// At distance 31, exhausted (table[31]=0)
		typeR.incrementDistance();
		assertEquals(0, typeR.getCurrentStrength());
	}
}
