package main

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

// client talks to fleet-api and telemetry-ingest. Every call checks the status
// code: a simulator that ignores errors produces load but no information.
type client struct {
	apiURL    string
	ingestURL string
	http      *http.Client
}

func newClient(apiURL, ingestURL string, timeout time.Duration) *client {
	return &client{
		apiURL:    apiURL,
		ingestURL: ingestURL,
		http: &http.Client{
			Timeout: timeout,
			Transport: &http.Transport{
				MaxIdleConns:        200,
				MaxIdleConnsPerHost: 200,
				IdleConnTimeout:     90 * time.Second,
			},
		},
	}
}

type registerRequest struct {
	Vin       string `json:"vin"`
	Model     string `json:"model"`
	SwVersion string `json:"swVersion"`
}

type registerResponse struct {
	ID string `json:"id"`
}

func (c *client) register(ctx context.Context, vin, model, swVersion string) (string, error) {
	body := registerRequest{Vin: vin, Model: model, SwVersion: swVersion}

	var result registerResponse
	err := c.do(ctx, http.MethodPost, c.apiURL+"/api/v1/vehicles", body, &result, http.StatusCreated)
	if err != nil {
		return "", err
	}
	if result.ID == "" {
		return "", fmt.Errorf("register %s: response contained no id", vin)
	}
	return result.ID, nil
}

type telemetryPayload struct {
	VehicleID   string    `json:"vehicleId"`
	RecordedAt  time.Time `json:"recordedAt"`
	SpeedKmh    float64   `json:"speedKmh"`
	BatteryTemp float64   `json:"batteryTemp"`
	Voltage     float64   `json:"voltage"`
}

func (c *client) sendTelemetry(ctx context.Context, vehicleID string, state vehicleState, at time.Time) error {
	payload := telemetryPayload{
		VehicleID:   vehicleID,
		RecordedAt:  at.UTC(),
		SpeedKmh:    round1(state.SpeedKmh),
		BatteryTemp: round1(state.BatteryTemp),
		Voltage:     round1(state.Voltage),
	}
	return c.do(ctx, http.MethodPost, c.ingestURL+"/telemetry", payload, nil, http.StatusAccepted)
}

type assignedUpdate struct {
	Version   string `json:"version"`
	Checksum  string `json:"checksum"`
	SizeBytes int64  `json:"sizeBytes"`
}

// assignedUpdate reports whether this vehicle has an update waiting. A 204 means
// "nothing for you", which is the normal case and not an error.
func (c *client) assignedUpdate(ctx context.Context, vehicleID string) (assignedUpdate, bool, error) {
	url := fmt.Sprintf("%s/api/v1/vehicles/%s/update", c.apiURL, vehicleID)

	request, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return assignedUpdate{}, false, err
	}
	response, err := c.http.Do(request)
	if err != nil {
		return assignedUpdate{}, false, err
	}
	defer drainAndClose(response)

	switch response.StatusCode {
	case http.StatusNoContent:
		return assignedUpdate{}, false, nil
	case http.StatusOK:
		var update assignedUpdate
		if err := json.NewDecoder(response.Body).Decode(&update); err != nil {
			return assignedUpdate{}, false, fmt.Errorf("decoding update: %w", err)
		}
		return update, true, nil
	default:
		return assignedUpdate{}, false, unexpectedStatus(url, response)
	}
}

type resultPayload struct {
	Version string `json:"version"`
	Success bool   `json:"success"`
}

func (c *client) reportResult(ctx context.Context, vehicleID, version string, success bool) error {
	url := fmt.Sprintf("%s/api/v1/vehicles/%s/update/result", c.apiURL, vehicleID)
	return c.do(ctx, http.MethodPost, url, resultPayload{Version: version, Success: success}, nil, http.StatusAccepted)
}

// do sends a JSON request and insists on wantStatus. Pass out = nil when the
// response body is not needed.
func (c *client) do(ctx context.Context, method, url string, body any, out any, wantStatus int) error {
	encoded, err := json.Marshal(body)
	if err != nil {
		return fmt.Errorf("encoding request for %s: %w", url, err)
	}

	request, err := http.NewRequestWithContext(ctx, method, url, bytes.NewReader(encoded))
	if err != nil {
		return err
	}
	request.Header.Set("Content-Type", "application/json")

	response, err := c.http.Do(request)
	if err != nil {
		return err
	}
	defer drainAndClose(response)

	if response.StatusCode != wantStatus {
		return unexpectedStatus(url, response)
	}
	if out == nil {
		return nil
	}
	if err := json.NewDecoder(response.Body).Decode(out); err != nil {
		return fmt.Errorf("decoding response from %s: %w", url, err)
	}
	return nil
}

func unexpectedStatus(url string, response *http.Response) error {
	snippet, _ := io.ReadAll(io.LimitReader(response.Body, 300))
	return fmt.Errorf("%s returned %d: %s", url, response.StatusCode, bytes.TrimSpace(snippet))
}

// drainAndClose lets the connection go back to the idle pool instead of being
// closed, which matters once 500 vehicles are sharing it.
func drainAndClose(response *http.Response) {
	_, _ = io.Copy(io.Discard, io.LimitReader(response.Body, 64*1024))
	_ = response.Body.Close()
}

func round1(value float64) float64 {
	return float64(int(value*10+0.5)) / 10
}
