package com.fleetops.fleetapi.rollout;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Decides when a rollout pauses itself (issue #6): when more than the failure
 * threshold of its finished installations failed, once enough of them have
 * finished for the share to mean something. Below that minimum, one early
 * failure would stop a rollout that is in fact fine.
 */
@Component
public class AutoPausePolicy {

    private final double failureThreshold;
    private final int minimumFinished;

    public AutoPausePolicy(
            @Value("${fleetops.rollout.auto-pause.failure-threshold:0.20}") double failureThreshold,
            @Value("${fleetops.rollout.auto-pause.minimum-finished:5}") int minimumFinished) {
        if (failureThreshold <= 0 || failureThreshold >= 1) {
            throw new IllegalArgumentException(
                    "failure threshold must be between 0 and 1, exclusive, but was " + failureThreshold);
        }
        if (minimumFinished < 1) {
            throw new IllegalArgumentException("minimum finished must be at least 1, but was " + minimumFinished);
        }
        this.failureThreshold = failureThreshold;
        this.minimumFinished = minimumFinished;
    }

    /**
     * @param failed   installations that failed
     * @param finished installations that failed or succeeded
     */
    public boolean shouldPause(long failed, long finished) {
        return finished >= minimumFinished && (double) failed / finished > failureThreshold;
    }
}
