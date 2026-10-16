package main

import (
	"fmt"
	"sync/atomic"
)

// stats is shared by every vehicle goroutine, so all counters are atomic.
type stats struct {
	telemetrySent      atomic.Int64
	telemetryFailed    atomic.Int64
	updatesReceived    atomic.Int64
	updatesSucceeded   atomic.Int64
	updatesFailed      atomic.Int64
	pollsFailed        atomic.Int64
	reportsFailed      atomic.Int64
	vehiclesRegistered atomic.Int64
}

func (s *stats) summary() string {
	return fmt.Sprintf(
		"vehicles=%d telemetry_sent=%d telemetry_failed=%d updates_received=%d "+
			"updates_succeeded=%d updates_failed=%d polls_failed=%d reports_failed=%d",
		s.vehiclesRegistered.Load(),
		s.telemetrySent.Load(),
		s.telemetryFailed.Load(),
		s.updatesReceived.Load(),
		s.updatesSucceeded.Load(),
		s.updatesFailed.Load(),
		s.pollsFailed.Load(),
		s.reportsFailed.Load(),
	)
}

// lossRate is the share of telemetry the server did not accept. Requirement 7
// asks for under 1% errors, so this is the number that matters in Phase 5.
func (s *stats) lossRate() float64 {
	sent := s.telemetrySent.Load()
	failed := s.telemetryFailed.Load()
	total := sent + failed
	if total == 0 {
		return 0
	}
	return float64(failed) / float64(total)
}
