package com.fleetops.fleetapi.rollout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The thresholds behind issue #6, at and around their boundaries. */
class AutoPausePolicyTest {

    private final AutoPausePolicy policy = new AutoPausePolicy(0.20, 5);

    @Test
    void neverPausesBeforeTheMinimumHaveFinished() {
        assertThat(policy.shouldPause(4, 4)).isFalse();
    }

    @Test
    void exactlyTheThresholdDoesNotPause() {
        assertThat(policy.shouldPause(1, 5)).isFalse();
        assertThat(policy.shouldPause(2, 10)).isFalse();
    }

    @Test
    void moreThanTheThresholdPauses() {
        assertThat(policy.shouldPause(2, 5)).isTrue();
        assertThat(policy.shouldPause(3, 10)).isTrue();
    }

    @Test
    void noFailuresNeverPause() {
        assertThat(policy.shouldPause(0, 100)).isFalse();
    }

    @Test
    void theThresholdsAreConfigurable() {
        AutoPausePolicy lenient = new AutoPausePolicy(0.50, 2);

        assertThat(lenient.shouldPause(1, 2)).isFalse();
        assertThat(lenient.shouldPause(2, 3)).isTrue();
    }

    @Test
    void rejectsAThresholdOutsideZeroToOne() {
        assertThatThrownBy(() -> new AutoPausePolicy(0, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AutoPausePolicy(1, 5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAMinimumBelowOne() {
        assertThatThrownBy(() -> new AutoPausePolicy(0.20, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
