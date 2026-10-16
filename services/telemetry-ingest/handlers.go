package main

import (
	"context"
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"
	"time"
)

// maxBodyBytes caps a request body. Telemetry is a handful of numbers; anything
// larger is either a mistake or someone probing the service.
const maxBodyBytes = 4 * 1024

// telemetryStore is everything the handlers need from the database. Declaring it
// here, rather than in the store, lets the tests swap in a fake.
type telemetryStore interface {
	vehicleExists(ctx context.Context, vehicleID string) (bool, error)
	insertReading(ctx context.Context, r reading) error
	latestReading(ctx context.Context, vehicleID string) (reading, bool, error)
}

type api struct {
	store telemetryStore
	now   func() time.Time
	log   *slog.Logger
}

func newAPI(store telemetryStore, log *slog.Logger) *api {
	return &api{store: store, now: time.Now, log: log}
}

func (a *api) routes() *http.ServeMux {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", a.health)
	mux.HandleFunc("POST /telemetry", a.postTelemetry)
	mux.HandleFunc("GET /vehicles/{id}/latest", a.getLatest)
	return mux
}

func (a *api) health(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "UP"})
}

func (a *api) postTelemetry(w http.ResponseWriter, r *http.Request) {
	var request telemetryRequest
	decoder := json.NewDecoder(http.MaxBytesReader(w, r.Body, maxBodyBytes))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&request); err != nil {
		writeError(w, r, http.StatusBadRequest, "body: "+err.Error())
		return
	}

	sample, err := request.toReading(a.now())
	if err != nil {
		writeError(w, r, http.StatusBadRequest, err.Error())
		return
	}

	known, err := a.store.vehicleExists(r.Context(), sample.VehicleID)
	if err != nil {
		a.log.Error("vehicle lookup failed", "error", err)
		writeError(w, r, http.StatusServiceUnavailable, "database unavailable")
		return
	}
	if !known {
		writeError(w, r, http.StatusBadRequest, "vehicleId: no such vehicle")
		return
	}

	if err := a.store.insertReading(r.Context(), sample); err != nil {
		a.log.Error("insert failed", "error", err)
		writeError(w, r, http.StatusServiceUnavailable, "database unavailable")
		return
	}

	w.WriteHeader(http.StatusAccepted)
}

type readingResponse struct {
	VehicleID   string    `json:"vehicleId"`
	RecordedAt  time.Time `json:"recordedAt"`
	SpeedKmh    float64   `json:"speedKmh"`
	BatteryTemp float64   `json:"batteryTemp"`
	Voltage     float64   `json:"voltage"`
}

func (a *api) getLatest(w http.ResponseWriter, r *http.Request) {
	vehicleID := r.PathValue("id")
	if !isUUID(vehicleID) {
		writeError(w, r, http.StatusBadRequest, "id: must be a UUID")
		return
	}

	sample, found, err := a.store.latestReading(r.Context(), vehicleID)
	if err != nil {
		a.log.Error("latest lookup failed", "error", err)
		writeError(w, r, http.StatusServiceUnavailable, "database unavailable")
		return
	}
	if !found {
		writeError(w, r, http.StatusNotFound, "no telemetry for vehicle "+vehicleID)
		return
	}

	// reading and readingResponse have the same fields, so a conversion is enough;
	// the json tags on the response type are ignored by the conversion.
	writeJSON(w, http.StatusOK, readingResponse(sample))
}

// apiError matches the error body fleet-api returns, so a client sees one shape
// across both services.
type apiError struct {
	Timestamp time.Time `json:"timestamp"`
	Status    int       `json:"status"`
	Error     string    `json:"error"`
	Message   string    `json:"message"`
	Path      string    `json:"path"`
}

func writeError(w http.ResponseWriter, r *http.Request, status int, message string) {
	writeJSON(w, status, apiError{
		Timestamp: time.Now().UTC(),
		Status:    status,
		Error:     http.StatusText(status),
		Message:   message,
		Path:      r.URL.Path,
	})
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(body); err != nil && !errors.Is(err, http.ErrHandlerTimeout) {
		// The status line is already sent, so there is nothing to tell the client.
		slog.Default().Error("writing response failed", "error", err)
	}
}
