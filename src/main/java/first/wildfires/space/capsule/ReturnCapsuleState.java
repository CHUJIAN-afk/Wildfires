/*
 * Adapted from NTM: Space EntityRideableRocket.RocketState.
 * Copyright NTM: Space contributors.
 * SPDX-License-Identifier: LGPL-3.0-only
 * Wildfires modifications: split launch/arrival into visually continuous first-release phases,
 * assigned stable persisted IDs and added an explicit recovery state.
 */
package first.wildfires.space.capsule;

import java.util.Arrays;
import java.util.Optional;

/** Stable persisted/network state IDs adapted from NTM's reusable-capsule phase boundaries. */
public enum ReturnCapsuleState {
    STATION_DOCKED(10, true),
    STATION_UNDOCKING(20, false),
    DEORBIT(30, false),
    REENTRY(40, false),
    SURFACE_LANDING(50, false),
    SURFACE_LANDED(60, true),
    SURFACE_LAUNCHING(70, false),
    ASCENT_TRANSITION(80, false),
    ORBIT_INSERTION(90, false),
    STATION_APPROACH(100, false),
    RECOVERY_REQUIRED(110, true);

    private final int stableId;
    private final boolean interactive;

    ReturnCapsuleState(int stableId, boolean interactive) {
        this.stableId = stableId;
        this.interactive = interactive;
    }

    public int stableId() {
        return stableId;
    }

    public boolean interactive() {
        return interactive;
    }

    public boolean docked() {
        return this == STATION_DOCKED;
    }

    public static Optional<ReturnCapsuleState> fromStableId(int id) {
        return Arrays.stream(values()).filter(value -> value.stableId == id).findFirst();
    }
}
