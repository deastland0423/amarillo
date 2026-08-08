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
    private boolean      directFire;   // true = Hellbore fires in direct-fire mode (E10.7)

    // ALLOCATE fields
    private int                 speed;                  // warp speed requested (31 = warp 30 + impulse)
    private boolean             topOffCap;              // legacy: true = charge phaser capacitor to full
    private double              capacitorCharge = -1;   // energy to add to the phaser capacitor; <0 = use topOffCap
    private String              shieldMode;             // "ACTIVE", "MINIMUM", or "OFF"
    private Map<String, String> weaponArming;           // weapon name → "STANDARD", "OVERLOAD", "SKIP", "ROLL", "FINISH", "EPT"
    private boolean             cloakPaid;              // true if the player paid the cloak cost this turn
    private boolean             energizeCaps;           // true if player paid 1 pt to energize capacitors (WS-0)
    private Map<String, Integer> esgEnergy;             // ESG designator → energy allocated this turn (G23.21)
    private String              esgDesignator;          // ANNOUNCE_ESG/CANCEL_ESG/DEACTIVATE_ESG: which ESG
    private int                 esgRadius;              // ANNOUNCE_ESG: field radius 0–3 (G23.41)
    private int                 esgReleaseAmount;       // ANNOUNCE_ESG: capacitor release 1–5 (G23.242); 0 = all
    private int                 batteryDraw;            // energy drawn from batteries this turn
    private int                 batteryRecharge;        // energy put into batteries this turn
    private boolean             doubleLwarp;            // Orion engine doubling (G15.2) — left warp
    private boolean             doubleRwarp;            // right warp
    private boolean             doubleCwarp;            // center warp
    private boolean             doubleImpulse;          // impulse
    private int                 generalReinforcement;   // general shield reinforcement points (2 energy each)
    private int[]               specificReinforcement;  // specific reinforcement per shield 1-6
    private Map<String, Map<String, Integer>> droneReloadSelections;   // rack name → {droneType → count}
    private Map<String, Map<String, Integer>> scatterPackLoading;     // shuttle name → {droneType → count}
    private Map<String, Integer>             suicideShuttleArming;   // shuttle name → energy (1–3)
    private java.util.Set<String>            suicideShuttleHold;     // shuttle names paying hold this turn
    private Map<String, Integer> shuttleSpeeds;                       // shuttle name → requested speed (active shuttles only)
    private Map<String, String>  shotModes;                           // weapon name → "SINGLE" or "DOUBLE" (FighterFusion)

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

    // PICKUP_OBJECTIVE
    private String objectiveName;
    private String retrievalMethod; // "TRACTOR" | "TRANSPORTER" | "SHUTTLE_PICKUP"
    public String getObjectiveName()                    { return objectiveName; }
    public void   setObjectiveName(String n)            { this.objectiveName = n; }
    public String getRetrievalMethod()                  { return retrievalMethod; }
    public void   setRetrievalMethod(String m)          { this.retrievalMethod = m; }

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

    public boolean isDirectFire()                      { return directFire; }
    public void    setDirectFire(boolean directFire)   { this.directFire = directFire; }

    public int  getSpeed()                         { return speed; }
    public void setSpeed(int speed)                { this.speed = speed; }

    public boolean isTopOffCap()                   { return topOffCap; }
    public void    setTopOffCap(boolean topOffCap) { this.topOffCap = topOffCap; }
    public double  getCapacitorCharge()                 { return capacitorCharge; }
    public void    setCapacitorCharge(double charge)    { this.capacitorCharge = charge; }

    public String getShieldMode()                        { return shieldMode; }
    public void   setShieldMode(String shieldMode)       { this.shieldMode = shieldMode; }

    public Map<String, String> getWeaponArming()                           { return weaponArming; }
    public void                setWeaponArming(Map<String, String> arming) { this.weaponArming = arming; }

    public boolean isCloakPaid()                   { return cloakPaid; }
    public void    setCloakPaid(boolean cloakPaid) { this.cloakPaid = cloakPaid; }

    public boolean isEnergizeCaps()                        { return energizeCaps; }
    public void    setEnergizeCaps(boolean energizeCaps)   { this.energizeCaps = energizeCaps; }
    public Map<String, Integer> getEsgEnergy()             { return esgEnergy; }
    public void    setEsgEnergy(Map<String, Integer> m)    { this.esgEnergy = m; }
    public String  getEsgDesignator()                      { return esgDesignator; }
    public void    setEsgDesignator(String d)              { this.esgDesignator = d; }
    public int     getEsgRadius()                          { return esgRadius; }
    public void    setEsgRadius(int r)                     { this.esgRadius = r; }
    public int     getEsgReleaseAmount()                   { return esgReleaseAmount; }
    public void    setEsgReleaseAmount(int a)              { this.esgReleaseAmount = a; }

    public int  getBatteryDraw()                       { return batteryDraw; }
    public void setBatteryDraw(int batteryDraw)        { this.batteryDraw = batteryDraw; }

    public boolean isDoubleLwarp()                     { return doubleLwarp; }
    public void    setDoubleLwarp(boolean v)           { this.doubleLwarp = v; }
    public boolean isDoubleRwarp()                     { return doubleRwarp; }
    public void    setDoubleRwarp(boolean v)           { this.doubleRwarp = v; }
    public boolean isDoubleCwarp()                     { return doubleCwarp; }
    public void    setDoubleCwarp(boolean v)           { this.doubleCwarp = v; }
    public boolean isDoubleImpulse()                   { return doubleImpulse; }
    public void    setDoubleImpulse(boolean v)         { this.doubleImpulse = v; }

    public int  getBatteryRecharge()                   { return batteryRecharge; }
    public void setBatteryRecharge(int batteryRecharge){ this.batteryRecharge = batteryRecharge; }

    public int  getGeneralReinforcement()                          { return generalReinforcement; }
    public void setGeneralReinforcement(int generalReinforcement)  { this.generalReinforcement = generalReinforcement; }

    public int[]  getSpecificReinforcement()                         { return specificReinforcement; }
    public void   setSpecificReinforcement(int[] specificReinforcement) { this.specificReinforcement = specificReinforcement; }

    public Map<String, Map<String, Integer>> getDroneReloadSelections()                                              { return droneReloadSelections; }
    public void                              setDroneReloadSelections(Map<String, Map<String, Integer>> selections) { this.droneReloadSelections = selections; }

    public Map<String, Map<String, Integer>> getScatterPackLoading()                                              { return scatterPackLoading; }
    public void                              setScatterPackLoading(Map<String, Map<String, Integer>> selections)  { this.scatterPackLoading = selections; }

    public Map<String, Integer>  getSuicideShuttleArming()                             { return suicideShuttleArming; }
    public void                  setSuicideShuttleArming(Map<String, Integer> m)       { this.suicideShuttleArming = m; }

    public java.util.Set<String> getSuicideShuttleHold()                               { return suicideShuttleHold; }
    public void                  setSuicideShuttleHold(java.util.Set<String> s)        { this.suicideShuttleHold = s; }

    public Map<String, Integer> getShuttleSpeeds()                              { return shuttleSpeeds; }
    public void                 setShuttleSpeeds(Map<String, Integer> speeds)   { this.shuttleSpeeds = speeds; }

    public Map<String, String>  getShotModes()                                  { return shotModes; }
    public void                 setShotModes(Map<String, String> shotModes)     { this.shotModes = shotModes; }

    // DISENGAGE_ACCEL fields
    private boolean declare;

    public boolean isDeclare()              { return declare; }
    public void    setDeclare(boolean d)    { this.declare = d; }

    // ASSIGN_GUARD field — post a commando squad instead of a normal BP (D7.83)
    private boolean commando;

    public boolean isCommando()               { return commando; }
    public void    setCommando(boolean c)     { this.commando = c; }

    // LAUNCH_PLASMA fields
    private boolean pseudo;
    private boolean fastLoad;

    public boolean isPseudo()                { return pseudo; }
    public void    setPseudo(boolean pseudo) { this.pseudo = pseudo; }
    public boolean isFastLoad()                  { return fastLoad; }
    public void    setFastLoad(boolean fastLoad)  { this.fastLoad = fastLoad; }

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

    // TRANSPORT_CREW fields  (shipName = source, targetName = destination)
    private int crewAmount;

    public int  getCrewAmount()                { return crewAmount; }
    public void setCrewAmount(int crewAmount)  { this.crewAmount = crewAmount; }

    // IDENTIFY_SEEKERS fields
    private List<String> seekerNames;

    public List<String> getSeekerNames()                       { return seekerNames; }
    public void         setSeekerNames(List<String> seekerNames) { this.seekerNames = seekerNames; }

    // ALLOCATE — Wild Weasel charging: shuttle names being charged this turn (J3.12)
    private java.util.Set<String> wwCharge;

    public java.util.Set<String> getWwCharge()              { return wwCharge; }
    public void                  setWwCharge(java.util.Set<String> s) { this.wwCharge = s; }

    // ALLOCATE — ECM/ECCM fields
    private int ecm;
    private int eccm;

    public int  getEcm()          { return ecm;  }
    public void setEcm(int ecm)   { this.ecm  = ecm;  }
    public int  getEccm()         { return eccm; }
    public void setEccm(int eccm) { this.eccm = eccm; }

    // ALLOCATE — Tractor energy pool
    private int tractorEnergy;

    public int  getTractorEnergy()                { return tractorEnergy; }
    public void setTractorEnergy(int tractorEnergy) { this.tractorEnergy = tractorEnergy; }

    // ESTABLISH_TRACTOR / NEGATIVE_TRACTOR_BID — auction bid
    private int tractorBid;

    public int  getTractorBid()             { return tractorBid; }
    public void setTractorBid(int tractorBid) { this.tractorBid = tractorBid; }

    // Destination hex for hex-targeted actions (ROTATE_TRACTORED, PLACE_TBOMB).
    // 1-based map coordinates; 0 means "not provided" (handlers must validate).
    private int hexCol;
    private int hexRow;

    public int  getHexCol()            { return hexCol; }
    public void setHexCol(int hexCol)  { this.hexCol = hexCol; }

    public int  getHexRow()            { return hexRow; }
    public void setHexRow(int hexRow)  { this.hexRow = hexRow; }

    // PERFORM_HET fields
    private int facing;
    private int hetEnergy;

    public int  getFacing()              { return facing; }
    public void setFacing(int facing)    { this.facing = facing; }

    public int  getHetEnergy()           { return hetEnergy; }
    public void setHetEnergy(int e)      { this.hetEnergy = e; }

    // ALLOCATE — Tactical Maneuver fields (C5.0)
    private int     warpTacticalTurns;       // 0–4 warp TACs to pre-pay (C5.22)
    private boolean sublightTacticalTurn;    // true = pay 1 impulse point for sublight TAC (C5.12)

    public int     getWarpTacticalTurns()                    { return warpTacticalTurns; }
    public void    setWarpTacticalTurns(int n)               { this.warpTacticalTurns = n; }
    public boolean isSublightTacticalTurn()                  { return sublightTacticalTurn; }
    public void    setSublightTacticalTurn(boolean b)        { this.sublightTacticalTurn = b; }

    // SUBMIT_REINFORCEMENT fields
    public static class ReinforcementEntry {
        private String shipName;
        private int    shieldNumber;
        private int    power;

        public String getShipName()              { return shipName; }
        public void   setShipName(String s)      { this.shipName = s; }
        public int    getShieldNumber()           { return shieldNumber; }
        public void   setShieldNumber(int n)      { this.shieldNumber = n; }
        public int    getPower()                  { return power; }
        public void   setPower(int p)             { this.power = p; }
    }

    private List<ReinforcementEntry> reinforcements;

    public List<ReinforcementEntry> getReinforcements()                        { return reinforcements; }
    public void                     setReinforcements(List<ReinforcementEntry> r) { this.reinforcements = r; }

    // COMMIT_FIRE_DECLARATION fields — a sealed fire plan (D6.315 written orders).
    // One FireOrder mirrors the FIRE action's payload; EwAdjustment carries a
    // ship's new EW totals for Game.adjustEw.
    public static class FireOrder {
        private String shipName;
        private String targetName;
        private List<String> weaponNames;
        private java.util.Map<String, String> shotModes;
        private int range;
        private int adjustedRange;
        private int shieldNumber;
        private boolean useUim;
        private boolean directFire = true;

        public String getShipName()                          { return shipName; }
        public void   setShipName(String s)                  { this.shipName = s; }
        public String getTargetName()                        { return targetName; }
        public void   setTargetName(String t)                { this.targetName = t; }
        public List<String> getWeaponNames()                 { return weaponNames; }
        public void   setWeaponNames(List<String> w)         { this.weaponNames = w; }
        public java.util.Map<String, String> getShotModes()  { return shotModes; }
        public void   setShotModes(java.util.Map<String, String> m) { this.shotModes = m; }
        public int     getRange()                            { return range; }
        public void    setRange(int r)                       { this.range = r; }
        public int     getAdjustedRange()                    { return adjustedRange; }
        public void    setAdjustedRange(int r)               { this.adjustedRange = r; }
        public int     getShieldNumber()                     { return shieldNumber; }
        public void    setShieldNumber(int n)                { this.shieldNumber = n; }
        public boolean isUseUim()                            { return useUim; }
        public void    setUseUim(boolean b)                  { this.useUim = b; }
        public boolean isDirectFire()                        { return directFire; }
        public void    setDirectFire(boolean b)              { this.directFire = b; }
    }

    public static class EwAdjustment {
        private String shipName;
        private int ecm;
        private int eccm;

        public String getShipName()         { return shipName; }
        public void   setShipName(String s) { this.shipName = s; }
        public int    getEcm()              { return ecm; }
        public void   setEcm(int e)         { this.ecm = e; }
        public int    getEccm()             { return eccm; }
        public void   setEccm(int e)        { this.eccm = e; }
    }

    private List<FireOrder> fireOrders;
    private List<EwAdjustment> ewAdjustments;

    public List<FireOrder> getFireOrders()                     { return fireOrders; }
    public void            setFireOrders(List<FireOrder> f)    { this.fireOrders = f; }
    public List<EwAdjustment> getEwAdjustments()               { return ewAdjustments; }
    public void               setEwAdjustments(List<EwAdjustment> e) { this.ewAdjustments = e; }
}
