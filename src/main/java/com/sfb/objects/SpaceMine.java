package com.sfb.objects;

public class SpaceMine extends Marker {

  public enum MineType {
    T_BOMB("tBomb", 10),
    NSM("NSM", 25);

    final String label;
    final int damage;

    MineType(String label, int damage) {
      this.label = label;
      this.damage = damage;
    }
  }

  // These vary by mine type
  private final MineType mineType;
  private final int damage;
  private final String name;

  // These are the same for all mines
  private final Ship layingShip;
  private final int placedOnImpulse;
  private final boolean isReal; // Only TBombs have dummies, but this is simpler than a separate subclass

  private boolean active = false;
  private boolean revealed = false; // true once a dummy has been exposed

  // The Constructor now takes the "Stats" as arguments
  public SpaceMine(MineType type, Ship layingShip, int placedOnImpulse, boolean isReal) {
    this.mineType = type;
    this.damage = type.damage;
    this.layingShip = layingShip;
    this.placedOnImpulse = placedOnImpulse;
    this.isReal = (type == MineType.NSM || isReal); // NSMs are always real, TBombs can be real or dummy
    this.name = type.label;
  }

  // -------------------------------------------------------------------------
  // Activation
  // -------------------------------------------------------------------------

  /**
   * Returns true if this mine is currently armed and will check for
   * targets.
   */
  public boolean isActive() {
    return active;
  }

  /**
   * Attempt to activate this mine. Should be called each impulse
   * while the mine is still inactive. Activates (permanently) if both conditions
   * are met:
   * at least {@code impulsesElapsed} impulses have passed AND the laying ship
   * is more than 1 hex away.
   *
   * @param currentImpulse The current absolute impulse number.
   * @param layerRange     Hex distance from this mine to the laying
   *                       ship.
   */
  public void tryActivate(int currentImpulse, int layerRange) {
    if (active)
      return;
    boolean timeElapsed = (currentImpulse - placedOnImpulse) >= 2;
    boolean layerGone = layerRange > 1;
    if (timeElapsed && layerGone) {
      active = true;
    }
  }

  // -------------------------------------------------------------------------
  // Detection
  // -------------------------------------------------------------------------

  /**
   * Check whether this active mine detects a unit moving at {@code speed}.
   * Rolls 1d6 for slow units. Returns true if the unit is detected.
   *
   * <p>
   * The caller is responsible for confirming the unit is within range 1
   * before calling this method.
   *
   * @param speed    The speed of the unit being checked.
   * @param diceRoll A pre-rolled 1d6 value (1–6). Ignored when speed &ge; 6.
   * @return true if the mine detects the unit and should detonate.
   */
  public boolean detectsUnit(int speed, int diceRoll) {
    if (!active)
      return false;
    if (speed >= 6)
      return true;
    return diceRoll > speed;
  }

  // -------------------------------------------------------------------------
  // Dummy / reveal
  // -------------------------------------------------------------------------

  public boolean isReal() {
    return isReal;
  }

  public boolean isRevealed() {
    return revealed;
  }

  /**
   * Mark this dummy mine as revealed (a unit entered range without explosion).
   * Has no effect on real mines.
   */
  public void reveal() {
    if (!isReal)
      revealed = true;
  }

  // -------------------------------------------------------------------------
  // Accessors
  // -------------------------------------------------------------------------

  public Ship getLayingShip() {
    return layingShip;
  }

  public int getPlacedOnImpulse() {
    return placedOnImpulse;
  }

  public int getDamage() {
    return damage;
  }

  public String getTypeName() {
    return name;
  }

  public MineType getMineType() {
    return mineType;
  }

  // -------------------------------------------------------------------------
  // Factory methods for specific mine types
  // -------------------------------------------------------------------------
  public static SpaceMine createTBomb(Ship layingShip, int placedOnImpulse, boolean isReal) {
    return new SpaceMine(MineType.T_BOMB, layingShip, placedOnImpulse, isReal);
  }

  public static SpaceMine createNSMine(Ship layingShip, int placedOnImpulse) {
    return new SpaceMine(MineType.NSM, layingShip, placedOnImpulse, true);
  }

}
