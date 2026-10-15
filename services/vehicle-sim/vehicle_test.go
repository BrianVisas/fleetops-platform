package main

import (
	"math/rand"
	"testing"
	"time"
)

func TestVehicleStateStaysInRange(t *testing.T) {
	rnd := rand.New(rand.NewSource(42))
	state := newVehicleState(rnd)

	for i := 0; i < 10_000; i++ {
		state = state.next(rnd)

		if state.SpeedKmh < minSpeed || state.SpeedKmh > maxSpeed {
			t.Fatalf("step %d: speed %v out of range", i, state.SpeedKmh)
		}
		if state.BatteryTemp < minTemp || state.BatteryTemp > maxTemp {
			t.Fatalf("step %d: battery temp %v out of range", i, state.BatteryTemp)
		}
		if state.Voltage < minVoltage || state.Voltage > maxVoltage {
			t.Fatalf("step %d: voltage %v out of range", i, state.Voltage)
		}
	}
}

func TestVehicleStateChangesGradually(t *testing.T) {
	rnd := rand.New(rand.NewSource(7))
	state := newVehicleState(rnd)

	for i := 0; i < 1000; i++ {
		next := state.next(rnd)
		if diff := abs(next.SpeedKmh - state.SpeedKmh); diff > 8.0001 {
			t.Fatalf("speed jumped by %v in one step", diff)
		}
		state = next
	}
}

func TestVinIsSeventeenValidCharacters(t *testing.T) {
	rnd := rand.New(rand.NewSource(1))
	tag := newRunTag(rnd)

	for _, index := range []int{0, 1, 42, 499, 99_999_999} {
		plate := vin("SMX", tag, index)
		if len(plate) != 17 {
			t.Errorf("vin %q has %d characters, want 17", plate, len(plate))
		}
		for _, c := range plate {
			if c == 'I' || c == 'O' || c == 'Q' {
				t.Errorf("vin %q contains a character real VINs do not use", plate)
			}
		}
	}
}

func TestRunTagMakesVinsUniqueAcrossRuns(t *testing.T) {
	first := newRunTag(rand.New(rand.NewSource(1)))
	second := newRunTag(rand.New(rand.NewSource(2)))

	if vin("SMX", first, 0) == vin("SMX", second, 0) {
		t.Error("two runs produced the same VIN for vehicle 0")
	}
}

func TestTelemetryInterval(t *testing.T) {
	cases := []struct {
		rate float64
		want time.Duration
	}{
		{rate: 1, want: time.Second},
		{rate: 2, want: 500 * time.Millisecond},
		{rate: 0.5, want: 2 * time.Second},
	}
	for _, tc := range cases {
		got := config{Rate: tc.rate}.telemetryInterval()
		if got != tc.want {
			t.Errorf("rate %v: interval = %v, want %v", tc.rate, got, tc.want)
		}
	}
}

func TestConfigValidation(t *testing.T) {
	valid := config{Vehicles: 10, Rate: 1, VinPrefix: "SMX", FailRate: 0.1,
		InstallMin: time.Second, InstallMax: 2 * time.Second}

	if err := valid.validate(); err != nil {
		t.Fatalf("expected the config to be valid: %v", err)
	}

	cases := []struct {
		name   string
		mutate func(*config)
	}{
		{"no vehicles", func(c *config) { c.Vehicles = 0 }},
		{"rate zero", func(c *config) { c.Rate = 0 }},
		{"fail rate above one", func(c *config) { c.FailRate = 1.5 }},
		{"short vin prefix", func(c *config) { c.VinPrefix = "SM" }},
		{"vin prefix with I", func(c *config) { c.VinPrefix = "SIM" }},
		{"install max below min", func(c *config) { c.InstallMax = 0 }},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			cfg := valid
			tc.mutate(&cfg)
			if err := cfg.validate(); err == nil {
				t.Error("expected validation to fail")
			}
		})
	}
}

func TestLossRate(t *testing.T) {
	s := &stats{}
	if s.lossRate() != 0 {
		t.Error("loss rate with no traffic should be 0")
	}

	s.telemetrySent.Store(99)
	s.telemetryFailed.Store(1)
	if got := s.lossRate(); got < 0.0099 || got > 0.0101 {
		t.Errorf("loss rate = %v, want about 0.01", got)
	}
}

func abs(v float64) float64 {
	if v < 0 {
		return -v
	}
	return v
}
