package com.sfb.properties;

/**
 * Quality rating of a boarding party for Hit-and-Run raid resolution.
 *
 * <p>Determines which column of the D7.81 and D7.831 tables is used when
 * resolving a raid or guard encounter.
 */
public enum BoardingPartyQuality {
    POOR,
    NORMAL,
    COMMANDO,
    OUTSTANDING
}
