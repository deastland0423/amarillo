package com.sfb;

import com.sfb.objects.OptionCatalogEntry;
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

    /**
     * The swivel is the ARC, and the launch directions follow from it (D2.34).
     *
     * <p>This test used to assert that a no-swivel launcher had launch directions of ZERO, and
     * that only the swivel entry had any. Both halves were artefacts. Zero meant "not derived":
     * WeaponFactory derived them from {@code ws.arcs}, which is null on a catalogue option
     * because an option's arcs come from the MOUNT — so every option-mount plasma got none, and
     * the swivel row looked different only because it stated a {@code launchDirections}
     * override. That override pinned the swivel torpedo to the FIXED one's directions,
     * cancelling what it cost +1 for.
     *
     * <p>R8.R3 says what actually differs: a fixed torpedo in a centerline mount bears FA, a
     * swivel one bears FP. One launch direction against D2.34's three.
     */
    @Test
    public void plasmaSwivelIsAnArc_andLaunchDirectionsFollowFromIt() throws Exception {
        OptionMountCatalog c = catalog();

        OptionCatalogEntry fixed = c.get("Plasma-S Torp (No Swivel)");
        OptionCatalogEntry swivel = c.get("Plasma-S Torp (Swivel)");
        assertFalse("the fixed entry is not a swivel", fixed.swivel);
        assertTrue("and the swivel entry says so in the catalogue", swivel.swivel);

        PlasmaLauncher onFa = (PlasmaLauncher) fixed.buildWeapon(List.of("FA"));
        PlasmaLauncher onFp = (PlasmaLauncher) swivel.buildWeapon(List.of("FP"));
        assertEquals(PlasmaType.S, onFa.getLauncherType());

        assertEquals("a fixed launcher fires straight ahead and nowhere else",
                1, Integer.bitCount(onFa.getLaunchDirections()));
        assertEquals("a swivel launcher gets D2.34's three",
                3, Integer.bitCount(onFp.getLaunchDirections()));
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
        // A real weapon with no class yet is honestly null, not a silently-wrong substitute.
        // The PPD is the current example; the ESG used to be one and is not any more, which is
        // what this test is for — it fails the day a recipe arrives, and that is the day someone
        // should be looking at whether the option is now buyable.
        assertFalse(catalog().get("Ion Cannon").isBuildableWeapon());
        assertFalse(catalog().get("PPD").isBuildableWeapon());
    }

    /**
     * The ESG left that list on 2026-10-21. The weapon class had been complete for weeks (G23.0,
     * 55 tests); only the Annex #8B rows lacked a recipe, so no Orion could buy one. Annex #8B
     * sells two of them and they are two different generators (G23.24).
     */
    @Test
    public void bothEsgRowsBuildTheGeneratorTheyName() throws Exception {
        com.sfb.weapons.ESG withCapacitor =
                (com.sfb.weapons.ESG) catalog().get("ESG").buildWeapon(List.of("FA"));
        com.sfb.weapons.ESG without = (com.sfb.weapons.ESG)
                catalog().get("ESG without capacitor").buildWeapon(List.of("FA"));

        assertTrue(withCapacitor.hasCapacitor());
        assertFalse(without.hasCapacitor());
    }
}
