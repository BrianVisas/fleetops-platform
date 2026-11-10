package com.fleetops.fleetapi.rollout;

/** Why a rollout is paused. */
public enum PauseReason {
    /** The fleet operator paused it. */
    MANUAL,
    /** It paused itself because too many installations failed (issue #6). */
    FAILURE_THRESHOLD
}
