package com.sfb.server;

/**
 * Inbound action from a client.
 *
 * The "type" field is a string discriminator. Additional fields are populated
 * depending on the type:
 *
 *   ADVANCE_PHASE  — no extra fields
 *   MOVE           — shipName, action (FORWARD | TURN_LEFT | TURN_RIGHT | SIDESLIP_LEFT | SIDESLIP_RIGHT)
 */
public class ActionRequest {

    private String type;
    private String shipName;
    private String action;

    public String getType()                 { return type; }
    public void   setType(String type)      { this.type = type; }

    public String getShipName()             { return shipName; }
    public void   setShipName(String n)     { this.shipName = n; }

    public String getAction()               { return action; }
    public void   setAction(String action)  { this.action = action; }
}
