package main

import (
	"context"
	"errors"
	"log/slog"
	"math/rand"
	"time"
)

// simVehicle is one simulated vehicle. It owns its own random source, so the
// goroutines never share one (math/rand's global source would serialise them).
type simVehicle struct {
	id     string
	vin    string
	state  vehicleState
	rnd    *rand.Rand
	client *client
	cfg    config
	stats  *stats
	log    *slog.Logger
}

// run drives this vehicle until the context is cancelled: telemetry on one
// ticker, update polling on another.
func (v *simVehicle) run(ctx context.Context) {
	telemetry := time.NewTicker(v.cfg.telemetryInterval())
	defer telemetry.Stop()

	poll := time.NewTicker(v.cfg.PollInterval)
	defer poll.Stop()

	// Spread the vehicles out so 500 of them do not all fire on the same instant.
	jitter := time.Duration(v.rnd.Int63n(int64(v.cfg.telemetryInterval())))
	select {
	case <-ctx.Done():
		return
	case <-time.After(jitter):
	}

	for {
		select {
		case <-ctx.Done():
			return
		case <-telemetry.C:
			v.sendOneReading(ctx)
		case <-poll.C:
			v.checkForUpdate(ctx)
		}
	}
}

func (v *simVehicle) sendOneReading(ctx context.Context) {
	v.state = v.state.next(v.rnd)

	if err := v.client.sendTelemetry(ctx, v.id, v.state, time.Now()); err != nil {
		if isShuttingDown(ctx, err) {
			return
		}
		v.stats.telemetryFailed.Add(1)
		v.log.Debug("telemetry rejected", "vin", v.vin, "error", err)
		return
	}
	v.stats.telemetrySent.Add(1)
}

func (v *simVehicle) checkForUpdate(ctx context.Context) {
	update, assigned, err := v.client.assignedUpdate(ctx, v.id)
	if err != nil {
		if isShuttingDown(ctx, err) {
			return
		}
		v.stats.pollsFailed.Add(1)
		v.log.Debug("update poll failed", "vin", v.vin, "error", err)
		return
	}
	if !assigned {
		return
	}

	v.stats.updatesReceived.Add(1)
	v.log.Info("installing update", "vin", v.vin, "version", update.Version)

	if !v.sleepInstall(ctx) {
		return
	}

	// Some installations fail on purpose: that is what the auto-pause requirement
	// in Phase 3 and the chaos experiments in Phase 7 need in order to be testable.
	success := v.rnd.Float64() >= v.cfg.FailRate

	if err := v.client.reportResult(ctx, v.id, update.Version, success); err != nil {
		if isShuttingDown(ctx, err) {
			return
		}
		v.stats.reportsFailed.Add(1)
		v.log.Debug("reporting result failed", "vin", v.vin, "error", err)
		return
	}

	if success {
		v.stats.updatesSucceeded.Add(1)
		v.log.Info("update installed", "vin", v.vin, "version", update.Version)
	} else {
		v.stats.updatesFailed.Add(1)
		v.log.Info("update failed", "vin", v.vin, "version", update.Version)
	}
}

// sleepInstall waits for a plausible installation time and reports whether it
// finished rather than being cut short by shutdown.
func (v *simVehicle) sleepInstall(ctx context.Context) bool {
	spread := v.cfg.InstallMax - v.cfg.InstallMin
	wait := v.cfg.InstallMin
	if spread > 0 {
		wait += time.Duration(v.rnd.Int63n(int64(spread)))
	}

	select {
	case <-ctx.Done():
		return false
	case <-time.After(wait):
		return true
	}
}

// isShuttingDown tells a cancelled request apart from a real failure, so the
// summary is not polluted by errors caused by Ctrl-C.
func isShuttingDown(ctx context.Context, err error) bool {
	return ctx.Err() != nil || errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded)
}
