package com.sfb.utilities;

public class ImpulseUtil {
  // Each index (0-31) represents Impulse 1-32
  // Each value is a bitmask of speeds that move on that impulse
  public static final int[] IMPULSE_MASKS = new int[32];

  static {
    // You only have to define this once
    IMPULSE_MASKS[0] = mask(32); // Impulse 1
    IMPULSE_MASKS[1] = mask(32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16); // Impulse 2
    IMPULSE_MASKS[2] = mask(32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 15, 14, 13, 12, 11); // Impulse 3
    IMPULSE_MASKS[3] = mask(32, 31, 30, 29, 28, 27, 26, 25, 24, 21, 20, 19, 18, 17, 16, 10, 9, 8); // Impulse 4
    IMPULSE_MASKS[4] = mask(32, 31, 30, 29, 28, 27, 26, 23, 22, 21, 20, 15, 14, 13, 7); // Impulse 5
    IMPULSE_MASKS[5] = mask(32, 31, 30, 29, 28, 27, 25, 24, 23, 22, 19, 18, 17, 16, 12, 11, 6); // Impulse 6
    IMPULSE_MASKS[6] = mask(32, 31, 30, 29, 28, 26, 25, 24, 23, 21, 20, 19, 15, 14, 10, 5); // Impulse 7
    IMPULSE_MASKS[7] = mask(32, 31, 30, 29, 28, 27, 26, 25, 24, 22, 21, 20, 18, 17, 16, 13, 12, 9, 8, 4); // Impulse 8
    IMPULSE_MASKS[8] = mask(32, 31, 30, 29, 27, 26, 25, 23, 22, 19, 18, 15, 11); // Impulse 9
    IMPULSE_MASKS[9] = mask(32, 31, 30, 29, 28, 27, 26, 24, 23, 21, 20, 17, 16, 14, 13, 10, 7); // Impulse 10
    IMPULSE_MASKS[10] = mask(32, 31, 30, 28, 27, 25, 24, 22, 21, 19, 18, 15, 12, 9, 6, 3); // Impulse 11
    IMPULSE_MASKS[11] = mask(32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 20, 19, 17, 16, 14, 11, 8); // Impulse 12
    IMPULSE_MASKS[12] = mask(32, 31, 30, 29, 28, 26, 25, 23, 21, 20, 18, 15, 13, 10, 5); // Impulse 13
    IMPULSE_MASKS[13] = mask(32, 31, 30, 29, 28, 27, 26, 24, 23, 22, 21, 19, 17, 16, 14, 12, 7); // Impulse 14
    IMPULSE_MASKS[14] = mask(32, 31, 30, 29, 28, 27, 26, 25, 24, 22, 20, 18, 15, 13, 11, 9); // Impulse 15
    IMPULSE_MASKS[15] = mask(32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16, 14, 12, 10, 8, 6, 4,
        2); // Impulse 16
    IMPULSE_MASKS[16] = mask(32, 31, 29, 27, 25, 23, 21, 19, 17); // Impulse 17
    IMPULSE_MASKS[17] = mask(32, 31, 30, 29, 28, 27, 26, 25, 24, 22, 20, 18, 16, 15, 13, 11, 9); // Impulse 18
    IMPULSE_MASKS[18] = mask(32, 31, 30, 29, 28, 27, 26, 24, 23, 22, 21, 19, 17, 14, 12, 7); // Impulse 19
    IMPULSE_MASKS[19] = mask(32, 31, 30, 29, 28, 26, 25, 24, 23, 21, 20, 18, 16, 15, 13, 10, 8, 5); // Impulse 20
    IMPULSE_MASKS[20] = mask(32, 31, 30, 29, 28, 27, 26, 25, 23, 22, 20, 19, 17, 14, 11); // Impulse 21
    IMPULSE_MASKS[21] = mask(32, 31, 30, 28, 27, 25, 24, 22, 21, 19, 18, 16, 15, 12, 9, 6, 3); // Impulse 22
    IMPULSE_MASKS[22] = mask(32, 31, 30, 29, 28, 27, 26, 24, 23, 21, 20, 17, 14, 13, 10, 7); // Impulse 23
    IMPULSE_MASKS[23] = mask(32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 20, 19, 18, 16, 15, 12, 11, 8, 4); // Impulse
                                                                                                            // 24
    IMPULSE_MASKS[24] = mask(32, 31, 30, 29, 27, 26, 25, 22, 21, 18, 17, 13, 9); // Impulse 25
    IMPULSE_MASKS[25] = mask(32, 31, 30, 29, 28, 26, 25, 24, 23, 21, 20, 19, 16, 15, 14, 10, 5); // Impulse 26
    IMPULSE_MASKS[26] = mask(32, 31, 30, 29, 28, 27, 25, 24, 23, 22, 19, 18, 17, 12, 11, 6); // Impulse 27
    IMPULSE_MASKS[27] = mask(32, 31, 30, 29, 28, 27, 26, 24, 23, 22, 21, 20, 16, 15, 14, 13, 8, 7); // Impulse 28
    IMPULSE_MASKS[28] = mask(32, 31, 30, 29, 28, 27, 26, 25, 21, 20, 19, 18, 17, 10, 9); // Impulse 29
    IMPULSE_MASKS[29] = mask(32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 16, 15, 14, 13, 12, 11); // Impulse 30
    IMPULSE_MASKS[30] = mask(32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17); // Impulse 31
    IMPULSE_MASKS[31] = 0xFFFFFFFF; // Impulse 32: Every speed moves (all bits 1)
  }

  private static int mask(int... speeds) {
    int m = 0;
    for (int s : speeds) {
      // Speed 1 maps to bit 0, Speed 32 maps to bit 31
      m |= (1 << (s - 1));
    }
    return m;
  }

  /**
   * The next impulse AFTER {@code currentImpulse} on which a unit of this speed moves, as a
   * number from 1 to 32, or 0 if it never moves (speed zero).
   * <p>
   * Strictly after, because "next" has to mean something unambiguous while an impulse is being
   * played: a unit that moves on the current impulse has either already done so or is about to,
   * and neither is what a player asking "when do I move next?" wants to know.
   * <p>
   * Wraps into the following turn, which needs no special case: impulse 32 moves every speed, so
   * the search always terminates within 32 steps for any speed of one or more.
   */
  public static int nextMovingImpulse(int currentImpulse, int speed) {
    if (speed <= 0)
      return 0;
    for (int ahead = 1; ahead <= 32; ahead++) {
      int impulse = ((currentImpulse - 1 + ahead) % 32) + 1;
      if (doesMove(impulse, speed))
        return impulse;
    }
    return 0;
  }

  /**
   * How many impulses until that next move, or 0 if the unit never moves.
   * <p>
   * Zero cannot be confused with "moves now" because {@link #nextMovingImpulse} looks strictly
   * ahead — one is the soonest answer it can give. That distinction is the whole point for
   * P3.25, where fire counts only on the impulse IMMEDIATELY before entry: a player needs to
   * know they are one impulse out, not merely that a move is coming.
   */
  public static int impulsesUntilNextMove(int currentImpulse, int speed) {
    if (speed <= 0)
      return 0;
    for (int ahead = 1; ahead <= 32; ahead++)
      if (doesMove(((currentImpulse - 1 + ahead) % 32) + 1, speed))
        return ahead;
    return 0;
  }

  // Return TRUE if the given speed moves on the given impulse, FALSE otherwise.
  public static boolean doesMove(int impulse, int speed) {
    if (speed <= 0 || impulse < 1 || impulse > 32)
      return false;

    // Map 1-32 to 0-31for the array and bit-shift
    int impulseIdx = impulse - 1;
    int speedBit = speed - 1;

    // The core logic: check if the bit for 'speed' is set in the 'impulse' mask
    return (IMPULSE_MASKS[impulseIdx] & (1 << speedBit)) != 0;
  }
}
