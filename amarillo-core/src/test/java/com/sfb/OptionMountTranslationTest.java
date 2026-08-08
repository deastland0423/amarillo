package com.sfb;

import com.sfb.objects.OptionMountCatalog;
import com.sfb.weapons.ADD;
import com.sfb.weapons.Disruptor;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Hellbore;
import com.sfb.weapons.Phaser2;
import com.sfb.weapons.PlasmaLauncher;
import com.sfb.weapons.Weapon;
import com.sfb.properties.PlasmaType;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Translating an Annex #8B catalog name into a live weapon instance (G15.4).
 * The recipe (weaponSpec) attached to each buildable row drives WeaponFactory,
 * so a picked option becomes a real weapon firing in the mount's arc — while
 * non-weapon systems and not-yet-implemented weapons report themselves
 * unbuildable rather than producing a bogus weapon.
 */
public class OptionMountTranslationTest {

    private OptionMountCatalog catalog() throws Exception {
        return OptionMountCatalog.fromJson(new File("../data/reference/orion_option_mounts.json"));
    }

    @Test
    public void phaser_translatesToItsClass_inTheMountArc() throws Exception {
        Weapon w = catalog().get("Phaser-2").buildWeapon(List.of("FA"));
        assertTrue(w instanceof Phaser2);
        assertEquals("fires in the mount's arc", "FA", w.getArcLabel());
    }

    @Test
    public void disruptorName_carriesItsRange() throws Exception {
        Weapon w = catalog().get("Disruptor-30").buildWeapon(List.of("FA"));
        assertTrue(w instanceof Disruptor);
        assertEquals("the '-30' in the name becomes the disruptor range",
                30, ((Disruptor) w).getDisruptorRange());
    }

    @Test
    public void plasmaName_decodesTypeAndSwivel() throws Exception {
        OptionMountCatalog c = catalog();

        Weapon noSwivel = c.get("Plasma-S Torp (No Swivel)").buildWeapon(List.of("FA"));
        assertTrue(noSwivel instanceof PlasmaLauncher);
        assertEquals(PlasmaType.S, ((PlasmaLauncher) noSwivel).getLauncherType());
        assertEquals("no-swivel launcher has no swivel arc", 0,
                ((PlasmaLauncher) noSwivel).getLaunchDirections());

        Weapon swivel = c.get("Plasma-S Torp (Swivel)").buildWeapon(List.of("FA"));
        assertTrue(((PlasmaLauncher) swivel).getLaunchDirections() != 0);
    }

    @Test
    public void droneRackName_decodesRackType() throws Exception {
        Weapon w = catalog().get("Drone Rack G").buildWeapon(List.of("FA"));
        assertTrue(w instanceof DroneRack);
        assertEquals(DroneRack.DroneRackType.TYPE_G, ((DroneRack) w).getRackType());
    }

    @Test
    public void addName_decodesRoundCount() throws Exception {
        Weapon w = catalog().get("ADD (6 round)").buildWeapon(List.of("FA"));
        assertTrue(w instanceof ADD);
        assertEquals(ADD.AddType.ADD_6, ((ADD) w).getAddType());
    }

    @Test
    public void hellbore_isBuildable_despiteBeingAHeavy() throws Exception {
        // Good coverage case: a ‡/Δ centerline heavy that DOES have a class.
        assertTrue(catalog().get("Hellbore").isBuildableWeapon());
        assertTrue(catalog().get("Hellbore").buildWeapon(List.of("FA")) instanceof Hellbore);
    }

    @Test
    public void nonWeaponSystem_isNotBuildable() throws Exception {
        // Cargo/Lab/Transporter are options but not weapons — no recipe, no weapon.
        assertFalse(catalog().get("Cargo").isBuildableWeapon());
        assertNull(catalog().get("Cargo").buildWeapon(List.of("FA")));
    }

    @Test
    public void unimplementedWeapon_reportsUnbuildable() throws Exception {
        // Ion Cannon / ESG are real weapons with no class yet — honestly null,
        // not a silently-wrong substitute.
        assertFalse(catalog().get("Ion Cannon").isBuildableWeapon());
        assertFalse(catalog().get("ESG").isBuildableWeapon());
    }
}
