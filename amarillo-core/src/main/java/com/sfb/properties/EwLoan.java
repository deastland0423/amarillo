package com.sfb.properties;

/**
 * Electronic warfare points one unit is lending to another right now (D6.3144).
 * <p>
 * Two numbers rather than one, because ECM and ECCM are lent as a declared split and are
 * capped separately: G24.2174 holds a fighter to "four points of ECM or four points of
 * ECCM", not four points between them.
 *
 * @param ecm  points of ECM the recipient may add to its own (D6.394)
 * @param eccm points of ECCM the recipient may add to its own (D6.393)
 */
public record EwLoan(int ecm, int eccm) {

    public static final EwLoan NONE = new EwLoan(0, 0);

    public boolean isNothing() {
        return ecm == 0 && eccm == 0;
    }

    /** J4.941's figure: "up to eight EW points (combined total of ECM and ECCM)". */
    public int combined() {
        return ecm + eccm;
    }

    @Override
    public String toString() {
        return isNothing() ? "no lent EW" : ecm + " ECM / " + eccm + " ECCM";
    }
}
