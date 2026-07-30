package com.sfb.objects;

/**
 * Lightweight container for the fungible contents a map object can hold —
 * personnel (crew units, boarding parties, commandos, prisoners) and (future)
 * typed cargo. Held by ships, markers, planet sides, shuttle holds, etc.
 *
 * <p>This is the FUNGIBLE tier: countable, interchangeable contents. Identity-
 * bearing things (a specific canister, a named diplomat) are the separate
 * {@link Objective} tier, not stored here.
 *
 * <p>Ships use {@link com.sfb.systemgroups.Crew} for their full crew system;
 * this manifest is the stub for simpler holders (markers, terrain, shuttles).
 *
 * <p>Future: add typed cargo (supplies, dilithium, VIP passengers, scenario
 * victory items) without changing the transfer interface.
 */
public class Manifest {

    private int crew          = 0; // operational crew units
    private int capturedCrew  = 0; // enemy crew taken prisoner
    private int boardingParties = 0;
    private int commandos     = 0;
    // TODO: Map<String,Integer> cargo for typed cargo/victory items

    public int getCrew()             { return crew; }
    public void setCrew(int v)       { this.crew = v; }
    public void addCrew(int v)       { this.crew += v; }

    public int getCapturedCrew()             { return capturedCrew; }
    public void setCapturedCrew(int v)       { this.capturedCrew = v; }
    public void addCapturedCrew(int v)       { this.capturedCrew += v; }

    public int getBoardingParties()          { return boardingParties; }
    public void setBoardingParties(int v)    { this.boardingParties = v; }
    public void addBoardingParties(int v)    { this.boardingParties += v; }

    public int getCommandos()                { return commandos; }
    public void setCommandos(int v)          { this.commandos = v; }
    public void addCommandos(int v)          { this.commandos += v; }

    public boolean isEmpty() {
        return crew == 0 && capturedCrew == 0 && boardingParties == 0 && commandos == 0;
    }
}
