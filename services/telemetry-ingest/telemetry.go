package main

import (
	"fmt"
	"time"
)

// maxClockSkew is how far into the future a reading may be timestamped before we
// reject it. Vehicle clocks drift; a reading from next week is a bug, not drift.
const maxClockSkew = 5 * time.Minute

// reading is a validated telemetry sample, ready to store.
type reading struct {
	VehicleID   string
	RecordedAt  time.Time
	SpeedKmh    float64
	BatteryTemp float64
	Voltage     float64
}

// telemetryRequest is what arrives on the wire. Every field is a pointer so that
// "missing" and "zero" can be told apart: 0.0 km/h is a valid speed.
type telemetryRequest struct {
	VehicleID   *string    `json:"vehicleId"`
	RecordedAt  *time.Time `json:"recordedAt"`
	SpeedKmh    *float64   `json:"speedKmh"`
	BatteryTemp *float64   `json:"batteryTemp"`
	Voltage     *float64   `json:"voltage"`
}

func (t telemetryRequest) toReading(now time.Time) (reading, error) {
	if t.VehicleID == nil {
		return reading{}, fmt.Errorf("vehicleId: must be present")
	}
	if !isUUID(*t.VehicleID) {
		return reading{}, fmt.Errorf("vehicleId: must be a UUID")
	}
	if t.RecordedAt == nil {
		return reading{}, fmt.Errorf("recordedAt: must be present")
	}
	if t.RecordedAt.After(now.Add(maxClockSkew)) {
		return reading{}, fmt.Errorf("recordedAt: more than %s in the future", maxClockSkew)
	}
	if t.SpeedKmh == nil {
		return reading{}, fmt.Errorf("speedKmh: must be present")
	}
	if *t.SpeedKmh < 0 || *t.SpeedKmh > 400 {
		return reading{}, fmt.Errorf("speedKmh: must be between 0 and 400")
	}
	if t.BatteryTemp == nil {
		return reading{}, fmt.Errorf("batteryTemp: must be present")
	}
	if *t.BatteryTemp < -50 || *t.BatteryTemp > 150 {
		return reading{}, fmt.Errorf("batteryTemp: must be between -50 and 150")
	}
	if t.Voltage == nil {
		return reading{}, fmt.Errorf("voltage: must be present")
	}
	if *t.Voltage <= 0 || *t.Voltage > 1000 {
		return reading{}, fmt.Errorf("voltage: must be between 0 and 1000")
	}

	return reading{
		VehicleID:   *t.VehicleID,
		RecordedAt:  t.RecordedAt.UTC(),
		SpeedKmh:    *t.SpeedKmh,
		BatteryTemp: *t.BatteryTemp,
		Voltage:     *t.Voltage,
	}, nil
}

// isUUID checks the 8-4-4-4-12 hex layout. Postgres would reject a bad id anyway,
// but catching it here keeps a malformed request from reaching the database at all.
func isUUID(s string) bool {
	if len(s) != 36 {
		return false
	}
	for i, c := range s {
		switch i {
		case 8, 13, 18, 23:
			if c != '-' {
				return false
			}
		default:
			isHex := (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
			if !isHex {
				return false
			}
		}
	}
	return true
}
