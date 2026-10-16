package main

import (
	"strings"
	"testing"
	"time"
)

func ptr[T any](v T) *T {
	return &v
}

const testVehicleID = "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90"

func validRequest(now time.Time) telemetryRequest {
	return telemetryRequest{
		VehicleID:   ptr(testVehicleID),
		RecordedAt:  ptr(now),
		SpeedKmh:    ptr(82.5),
		BatteryTemp: ptr(31.0),
		Voltage:     ptr(398.2),
	}
}

func TestToReading(t *testing.T) {
	now := time.Date(2026, 10, 15, 12, 0, 0, 0, time.UTC)

	cases := []struct {
		name      string
		mutate    func(*telemetryRequest)
		wantField string // empty means the request should be accepted
	}{
		{name: "valid reading", mutate: func(*telemetryRequest) {}},
		{name: "speed may be zero", mutate: func(r *telemetryRequest) { r.SpeedKmh = ptr(0.0) }},
		{name: "missing vehicle id", mutate: func(r *telemetryRequest) { r.VehicleID = nil }, wantField: "vehicleId"},
		{name: "vehicle id not a uuid", mutate: func(r *telemetryRequest) { r.VehicleID = ptr("ABC-123") }, wantField: "vehicleId"},
		{name: "missing timestamp", mutate: func(r *telemetryRequest) { r.RecordedAt = nil }, wantField: "recordedAt"},
		{
			name:      "timestamp too far in the future",
			mutate:    func(r *telemetryRequest) { r.RecordedAt = ptr(now.Add(6 * time.Minute)) },
			wantField: "recordedAt",
		},
		{
			name:   "small clock drift is accepted",
			mutate: func(r *telemetryRequest) { r.RecordedAt = ptr(now.Add(2 * time.Minute)) },
		},
		{name: "missing speed", mutate: func(r *telemetryRequest) { r.SpeedKmh = nil }, wantField: "speedKmh"},
		{name: "negative speed", mutate: func(r *telemetryRequest) { r.SpeedKmh = ptr(-1.0) }, wantField: "speedKmh"},
		{name: "missing battery temp", mutate: func(r *telemetryRequest) { r.BatteryTemp = nil }, wantField: "batteryTemp"},
		{name: "implausible battery temp", mutate: func(r *telemetryRequest) { r.BatteryTemp = ptr(900.0) }, wantField: "batteryTemp"},
		{name: "missing voltage", mutate: func(r *telemetryRequest) { r.Voltage = nil }, wantField: "voltage"},
		{name: "zero voltage", mutate: func(r *telemetryRequest) { r.Voltage = ptr(0.0) }, wantField: "voltage"},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			request := validRequest(now)
			tc.mutate(&request)

			sample, err := request.toReading(now)

			if tc.wantField == "" {
				if err != nil {
					t.Fatalf("expected the reading to be accepted, got %v", err)
				}
				if sample.VehicleID != testVehicleID {
					t.Errorf("vehicle id = %q, want %q", sample.VehicleID, testVehicleID)
				}
				if sample.RecordedAt.Location() != time.UTC {
					t.Errorf("recordedAt should be stored in UTC, got %v", sample.RecordedAt.Location())
				}
				return
			}

			if err == nil {
				t.Fatalf("expected an error naming %q", tc.wantField)
			}
			if !strings.Contains(err.Error(), tc.wantField) {
				t.Errorf("error %q should name the field %q", err.Error(), tc.wantField)
			}
		})
	}
}

func TestIsUUID(t *testing.T) {
	valid := []string{
		"3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90",
		"3F2A9C1E-5B7D-4E8A-9C21-7D4E5F6A8B90",
	}
	for _, s := range valid {
		if !isUUID(s) {
			t.Errorf("%q should be a valid uuid", s)
		}
	}

	invalid := []string{
		"",
		"3f2a9c1e5b7d4e8a9c217d4e5f6a8b90",
		"3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b9",
		"3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b9z",
		"3f2a9c1e_5b7d_4e8a_9c21_7d4e5f6a8b90",
	}
	for _, s := range invalid {
		if isUUID(s) {
			t.Errorf("%q should not be a valid uuid", s)
		}
	}
}
