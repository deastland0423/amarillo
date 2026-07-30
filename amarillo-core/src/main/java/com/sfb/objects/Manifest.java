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

    // --- Typed access + personnel-space accounting (J2.211) ------------------

    public int count(com.sfb.properties.PersonnelType type) {
        switch (type) {
            case CREW_UNIT:      return crew;
            case BOARDING_PARTY: return boardingParties;
            case COMMANDO:       return commandos;
            default:             return 0;
        }
    }

    public void add(com.sfb.properties.PersonnelType type, int n) {
        switch (type) {
            case CREW_UNIT:      crew += n; break;
            case BOARDING_PARTY: boardingParties += n; break;
            case COMMANDO:       commandos += n; break;
        }
    }

    /** Total personnel spaces occupied by everyone here (J2.211 sizing). */
    public int personnelSpaces() {
        int total = 0;
        for (com.sfb.properties.PersonnelType t : com.sfb.properties.PersonnelType.values())
            total += count(t) * t.spaces;
        return total;
    }

    /**
     * Move up to {@code amount} of {@code type} from this manifest into
     * {@code dest}, limited by what this manifest actually holds and by
     * {@code destFreeSpaces} (the destination holder's spare personnel capacity;
     * pass {@link Integer#MAX_VALUE} for an uncapped holder such as a planet
     * surface). Returns the number actually moved.
     */
    public int transferTo(Manifest dest, com.sfb.properties.PersonnelType type,
            int amount, int destFreeSpaces) {
        if (dest == null || amount <= 0) return 0;
        int available   = count(type);
        int fitCapacity = type.spaces > 0 ? destFreeSpaces / type.spaces : available;
        int moved = Math.min(amount, Math.min(available, Math.max(0, fitCapacity)));
        if (moved <= 0) return 0;
        add(type, -moved);
        dest.add(type, moved);
        return moved;
    }
}
