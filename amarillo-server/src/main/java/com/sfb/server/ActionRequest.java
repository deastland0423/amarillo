package com.sfb.server;

import java.util.List;
import java.util.Map;

/**
 * Inbound action from a client.
 *
 * The "type" field is a string discriminator. Additional fields are populated
 * depending on the type:
 *
 *   ADVANCE_PHASE  — no extra fields
 *   MOVE           — shipName, action (FORWARD | TURN_LEFT | TURN_RIGHT | SIDESLIP_LEFT | SIDESLIP_RIGHT)
 *   FIRE           — shipName (attacker), targetName, weaponNames, range, adjustedRange, shieldNumber
 */
public class ActionRequest {

    private String       type;
    private String       playerToken; // set by controller from X-Player-Token header
    private String       shipName;
    private String       action;

    // FIRE fields
    private String       targetName;
    private List<String> weaponNames;
    private int          range;
    private int          adjustedRange;
    private int          shieldNumber;
    private boolean      useUim;

    // ALLOCATE fields
    private int                 speed;        // warp speed requested
    private boolean             topOffCap;    // true = charge phaser capacitor to full
    private String              shieldMode;   // "ACTIVE", "MINIMUM", or "OFF"
    private Map<String, String> weaponArming; // weapon name → "STANDARD", "OVERLOAD", "SKIP", "ROLL", "FINISH", "DISCHARGE"
    private boolean             cloakPaid;    // true if the player paid the cloak cost this turn
    private boolean             energizeCaps; // true if player paid 1 pt to energize capacitors (WS-0)

    public String getType()                           { return type; }
    public void   setType(String type)                { this.type = type; }

    public String getPlayerToken()                    { return playerToken; }
    public void   setPlayerToken(String playerToken)  { this.playerToken = playerToken; }

    public String getShipName()                    { return shipName; }
    public void   setShipName(String n)            { this.shipName = n; }

    public String getAction()                      { return action; }
    public void   setAction(String action)         { this.action = action; }

    public String getTargetName()                  { return targetName; }
    public void   setTargetName(String targetName) { this.targetName = targetName; }

    public List<String> getWeaponNames()                       { return weaponNames; }
    public void         setWeaponNames(List<String> weaponNames) { this.weaponNames = weaponNames; }

    public int  getRange()                         { return range; }
    public void setRange(int range)                { this.range = range; }

    public int  getAdjustedRange()                 { return adjustedRange; }
    public void setAdjustedRange(int adjustedRange){ this.adjustedRange = adjustedRange; }

    public int  getShieldNumber()                  { return shieldNumber; }
    public void setShieldNumber(int shieldNumber)  { this.shieldNumber = shieldNumber; }

    public boolean isUseUim()                      { return useUim; }
    public void    setUseUim(boolean useUim)        { this.useUim = useUim; }

    public int  getSpeed()                         { return speed; }
    public void setSpeed(int speed)                { this.speed = speed; }

    public boolean isTopOffCap()                   { return topOffCap; }
    public void    setTopOffCap(boolean topOffCap) { this.topOffCap = topOffCap; }

    public String getShieldMode()                        { return shieldMode; }
    public void   setShieldMode(String shieldMode)       { this.shieldMode = shieldMode; }

    public Map<String, String> getWeaponArming()                           { return weaponArming; }
    public void                setWeaponArming(Map<String, String> arming) { this.weaponArming = arming; }

    public boolean isCloakPaid()                   { return cloakPaid; }
    public void    setCloakPaid(boolean cloakPaid) { this.cloakPaid = cloakPaid; }

    public boolean isEnergizeCaps()                        { return energizeCaps; }
    public void    setEnergizeCaps(boolean energizeCaps)   { this.energizeCaps = energizeCaps; }

    // LAUNCH_PLASMA fields
    private boolean pseudo;

    public boolean isPseudo()                { return pseudo; }
    public void    setPseudo(boolean pseudo) { this.pseudo = pseudo; }

    // ALLOCATE — transporter field
    private int transUses;

    public int  getTransUses()               { return transUses; }
    public void setTransUses(int transUses)  { this.transUses = transUses; }

    // BOARDING_ACTION fields
    private int normalParties;
    private int commandoParties;

    public int  getNormalParties()                       { return normalParties; }
    public void setNormalParties(int normalParties)      { this.normalParties = normalParties; }

    public int  getCommandoParties()                     { return commandoParties; }
    public void setCommandoParties(int commandoParties)  { this.commandoParties = commandoParties; }
}
