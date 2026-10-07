package com.sfb.weapons;

import com.sfb.properties.PlasmaType;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * What a weapon is CALLED, as against what it is keyed on.
 *
 * <h2>Why this is in core at all</h2>
 * {@code getType()} is a lookup key — it is half of {@code getName()}, which the client sends back to
 * address a weapon for reload selections, hex fire and boarding — so it is written for machines:
 * "Phaser1", "Disruptor30", "PlasmaDRack", and a bare "Plasma" for every launcher whatever torpedo it
 * throws.
 *
 * <p>FOUR places were dressing that up independently and had drifted apart. Two {@code weaponLabel}
 * copies in the web whose rewrite rules no longer matched anything — they rewrite {@code
 * /^Disruptor-/} and {@code /^DroneRack-/} while the real names are "Disruptor30-A" and
 * "Drone-Rack 1". A third in the SSD panel, which until yesterday showed "Plasma A" for a weapon the
 * map sidebar already named properly. And the COMBAT LOG, which shows players "Phaser1-1 destroyed —
 * cannot fire" and "Drone-Rack 1 cannot bear on the planet". The log is the reason this lives here
 * rather than in one shared web module: nothing in the client can reach it.
 */
public class WeaponDisplayNameTest {

    /** The default is the type itself, which is already right for these. */
    @Test
    public void aWeaponWhoseKeyIsReadableKeepsIt() {
        assertEquals("Photon", new Photon().getDisplayName());
        assertEquals("Fusion", new Fusion().getDisplayName());
        assertEquals("Hellbore", new Hellbore().getDisplayName());
        assertEquals("ESG", new ESG().getDisplayName());
        assertEquals("ADD", new ADD(ADD.AddType.ADD_6).getDisplayName());
    }

    /** Phasers gain the hyphen SFB writes them with. */
    @Test
    public void phasersAreHyphenated() {
        assertEquals("Phaser-1", new Phaser1().getDisplayName());
        assertEquals("Phaser-2", new Phaser2().getDisplayName());
        assertEquals("Phaser-3", new Phaser3().getDisplayName());
        assertEquals("Phaser-4", new Phaser4().getDisplayName());
        assertEquals("Phaser-G", new PhaserG().getDisplayName());
    }

    /**
     * A disruptor is identified by its range in play, and the range is NOT fixed for the life of the
     * weapon — D23.12 notes one may be repaired as a shorter-ranged version — so the name is computed
     * rather than set once.
     */
    @Test
    public void aDisruptorNamesItsRange() {
        Disruptor d30 = new Disruptor();
        d30.setDisruptorRange(30);
        assertEquals("Disruptor-30", d30.getDisplayName());

        Disruptor d40 = new Disruptor();
        d40.setDisruptorRange(40);
        assertEquals("Disruptor-40", d40.getDisplayName());
    }

    /**
     * The case that prompted the whole thing. Every launcher's type string is a bare "Plasma", so the
     * letter has to come from the launcher type or the player learns nothing — and an R, an S and an
     * F differ in warhead, arming cost and range.
     */
    @Test
    public void aPlasmaLauncherNamesItsTorpedo() {
        assertEquals("Plasma-R", new PlasmaLauncher(PlasmaType.R).getDisplayName());
        assertEquals("Plasma-S", new PlasmaLauncher(PlasmaType.S).getDisplayName());
        assertEquals("Plasma-F", new PlasmaLauncher(PlasmaType.F).getDisplayName());
        assertEquals("Plasma-G", new PlasmaLauncher(PlasmaType.G).getDisplayName());
    }

    /** And the plasma RACK is a different weapon that keeps its own name (FP10.0). */
    @Test
    public void aPlasmaRackIsNotALauncher() {
        assertEquals("Plasma-D Rack", new PlasmaRack().getDisplayName());
    }

    /**
     * A drone rack says which of the eight it is — and it must be computed, because the Y175 refit
     * upgrades a type-A into a type-G and a name fixed at construction would still say type-A.
     */
    @Test
    public void aDroneRackNamesItsTypeAndFollowsAnUpgrade() {
        DroneRack rack = new DroneRack(DroneRack.DroneRackType.TYPE_A);
        assertEquals("Type-A Drone", rack.getDisplayName());

        rack.upgradeRackType(DroneRack.DroneRackType.TYPE_G);
        assertEquals("the Y175 refit changes what it IS, so it changes what it is called",
                "Type-G Drone", rack.getDisplayName());
    }

    // ------------------------------------------------------------------ the label

    /**
     * The label pairs the name with the designator, each said once. The clause that earns its keep is
     * the one for a designator that already states its own weapon: 43 hulls designate their ADDs
     * "ADD 1" and 91 designate racks "Rack 1", so a blanket join gives "ADD ADD 1" — and only this
     * rule produces both "ADD 1" and "Type-A Drone Rack 1" correctly.
     */
    @Test
    public void theLabelSaysTheKindAndTheDesignatorOnceEach() {
        Phaser1 ph = new Phaser1();
        ph.setDesignator("1");
        assertEquals("Phaser-1 1", ph.getLabel());

        Photon photon = new Photon();
        photon.setDesignator("A");
        assertEquals("Photon A", photon.getLabel());

        ADD add = new ADD(ADD.AddType.ADD_6);
        add.setDesignator("ADD 1");
        assertEquals("the designator already names it", "ADD 1", add.getLabel());

        DroneRack rack = new DroneRack(DroneRack.DroneRackType.TYPE_A);
        rack.setDesignator("Rack 1");
        assertEquals("but a rack's designator does not say 'Drone'",
                "Type-A Drone Rack 1", rack.getLabel());
    }

    /** No designator is just the kind, not a trailing space. */
    @Test
    public void aWeaponWithNoDesignatorIsJustItsKind() {
        assertEquals("ESG", new ESG().getLabel());
    }

    /**
     * And the identity is untouched. {@code getName()} is the wire key for reload selections, hex
     * fire and boarding targets, so a change here must never reach it — that would be a protocol
     * change dressed as a caption fix.
     */
    @Test
    public void theIdentityIsUnchanged() {
        Phaser1 ph = new Phaser1();
        ph.setDesignator("1");
        assertEquals("Phaser1-1", ph.getName());
        assertEquals("Phaser1", ph.getType());

        PlasmaLauncher pl = new PlasmaLauncher(PlasmaType.R);
        pl.setDesignator("A");
        assertEquals("Plasma-A", pl.getName());
        assertEquals("Plasma", pl.getType());
    }
}
