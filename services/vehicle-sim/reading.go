package main

import (
	"fmt"
	"math/rand"
)

// vehicleState is what a vehicle currently reports. Values move by small steps
// between readings rather than jumping, so the stored series looks like a vehicle
// driving and not like noise.
type vehicleState struct {
	SpeedKmh    float64
	BatteryTemp float64
	Voltage     float64
}

// Plausible operating ranges. These sit inside what telemetry-ingest accepts, so
// a valid simulation never produces a 400.
const (
	minSpeed   = 0.0
	maxSpeed   = 180.0
	minTemp    = 18.0
	maxTemp    = 48.0
	minVoltage = 360.0
	maxVoltage = 420.0
)

func newVehicleState(rnd *rand.Rand) vehicleState {
	return vehicleState{
		SpeedKmh:    rnd.Float64() * 120,
		BatteryTemp: 25 + rnd.Float64()*10,
		Voltage:     380 + rnd.Float64()*20,
	}
}

// next walks each value a little and clamps it back into range.
func (s vehicleState) next(rnd *rand.Rand) vehicleState {
	return vehicleState{
		SpeedKmh:    clamp(s.SpeedKmh+step(rnd, 8), minSpeed, maxSpeed),
		BatteryTemp: clamp(s.BatteryTemp+step(rnd, 0.4), minTemp, maxTemp),
		Voltage:     clamp(s.Voltage+step(rnd, 1.5), minVoltage, maxVoltage),
	}
}

// step returns a random change in [-size, +size].
func step(rnd *rand.Rand, size float64) float64 {
	return (rnd.Float64()*2 - 1) * size
}

func clamp(value, low, high float64) float64 {
	if value < low {
		return low
	}
	if value > high {
		return high
	}
	return value
}

// vin builds a 17-character VIN from a 3-character prefix, a 6-character run tag
// and the vehicle's index. The run tag keeps two runs of the simulator from
// colliding on the unique VIN constraint in fleet-api.
func vin(prefix, runTag string, index int) string {
	return fmt.Sprintf("%s%s%08d", prefix, runTag, index)
}

// runTag is six hex characters - all valid VIN characters, since hex has no I, O or Q.
func newRunTag(rnd *rand.Rand) string {
	const hex = "0123456789ABCDEF"
	tag := make([]byte, 6)
	for i := range tag {
		tag[i] = hex[rnd.Intn(len(hex))]
	}
	return string(tag)
}
