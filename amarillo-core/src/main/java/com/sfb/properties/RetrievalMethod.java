package com.sfb.properties;

/**
 * How a free-floating {@link com.sfb.objects.Objective} may be brought aboard a
 * ship. An objective permits a set of these (from its type); whether a given
 * ship can actually use one right now is a separate check (lock-on, range,
 * shields, etc.).
 */
public enum RetrievalMethod {
    TRACTOR,       // tractor beam / shuttle tractor rotation (J1.621) — e.g. SH35 canisters
    TRANSPORTER,   // beam aboard through a dropped shield — e.g. SH47 stasis box
    SHUTTLE_PICKUP // a shuttle/fighter physically collects it
}
