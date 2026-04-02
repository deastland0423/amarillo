package com.sfb;

import java.io.IOException;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;

import com.sfb.objects.Ship;
import com.sfb.samples.FederationShips;
import com.sfb.utilities.DAC;

public class Application {

	private static DAC myDac = new DAC();
	public static void main(String[] args) {

//		Marker thing1 = new Marker(14,10);
//		Marker thing2 = new Marker(14,9);
//		
//		int facing = 1;
//		
//		int range = MapUtils.getRange(thing1, thing2);
//		
//		FiringArc firingArc = FiringArc.values()[MapUtils.getAbsoluteArc(thing1, thing2)];
//		
//		ShieldFacing shieldFacing = ShieldFacing.values()[MapUtils.getAbsoluteShieldFacing(thing1, thing2)];
//
//		int trueBearing = MapUtils.getBearing(thing1, thing2);
//		
//		System.out.println("Source: " + thing1.getLocation());
//		System.out.println("Target: " + thing2.getLocation());
//		System.out.println("----------------------------");
//		
//		System.out.println("RelativeBearing: " + MapUtils.getRelativeBearing(trueBearing, facing));
//
//		System.out.println("Distance: " + range);
//		System.out.println("ARC: " + firingArc);
//		System.out.println("SHLD: " + shieldFacing);
//		System.out.println("Bearing: " + trueBearing);
//		
//		System.out.println("---------------------------");
		
//		DAC dac = new DAC();
//		int roll = 2;
//		System.out.println("Roll: " + roll + " |Result: " + dac.fetchNextHit(roll));
//		System.out.println("Roll: " + roll + " |Result: " + dac.fetchNextHit(roll));
//		System.out.println("Roll: " + roll + " |Result: " + dac.fetchNextHit(roll));
//		System.out.println("Roll: " + roll + " |Result: " + dac.fetchNextHit(roll));
//		System.out.println("Roll: " + roll + " |Result: " + dac.fetchNextHit(roll));
//		System.out.println("Roll: " + roll + " |Result: " + dac.fetchNextHit(roll));
//		System.out.println("Roll: " + roll + " |Result: " + dac.fetchNextHit(roll));
//		System.out.println("Roll: " + roll + " |Result: " + dac.fetchNextHit(roll));
//
//		roll = 7;
//		System.out.println("Roll: " + roll + " |Result: " + dac.fetchNextHit(roll));
//		System.out.println("Roll: " + roll + " |Result: " + dac.fetchNextHit(roll));
//		System.out.println("Roll: " + roll + " |Result: " + dac.fetchNextHit(roll));
//		System.out.println("Roll: " + roll + " |Result: " + dac.fetchNextHit(roll));
//		System.out.println("Roll: " + roll + " |Result: " + dac.fetchNextHit(roll));
//		System.out.println("Roll: " + roll + " |Result: " + dac.fetchNextHit(roll));
//		System.out.println("Roll: " + roll + " |Result: " + dac.fetchNextHit(roll));
//		System.out.println("Roll: " + roll + " |Result: " + dac.fetchNextHit(roll));
//		System.out.println("Roll: " + roll + " |Result: " + dac.fetchNextHit(roll));
//		
//		roll = 9;
//		System.out.println("Roll: " + roll + " |Result: " + dac.fetchNextHit(roll));
//		System.out.println("Roll: " + roll + " |Result: " + dac.fetchNextHit(roll));
//		System.out.println("Roll: " + roll + " |Result: " + dac.fetchNextHit(roll));
//		System.out.println("Roll: " + roll + " |Result: " + dac.fetchNextHit(roll));
		
		Ship newShip = new Ship();
		newShip.init(FederationShips.getFedCa());
		
		ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
		try {
			String myShip = ow.writeValueAsString(newShip);
			
			System.out.println(myShip);
		} catch (JsonGenerationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		
		
		//		try {
//			System.out.println(new Ship());
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
	}
	
}
