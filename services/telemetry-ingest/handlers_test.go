package main

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

// fakeStore stands in for Postgres. The handlers only know the interface, so no
// database is needed to test status codes and bodies.
type fakeStore struct {
	known    bool
	latest   *reading
	failWith error
	inserted []reading
}

func (f *fakeStore) vehicleExists(_ context.Context, _ string) (bool, error) {
	if f.failWith != nil {
		return false, f.failWith
	}
	return f.known, nil
}

func (f *fakeStore) insertReading(_ context.Context, r reading) error {
	if f.failWith != nil {
		return f.failWith
	}
	f.inserted = append(f.inserted, r)
	return nil
}

func (f *fakeStore) latestReading(_ context.Context, _ string) (reading, bool, error) {
	if f.failWith != nil {
		return reading{}, false, f.failWith
	}
	if f.latest == nil {
		return reading{}, false, nil
	}
	return *f.latest, true, nil
}

func testAPI(store telemetryStore) *api {
	quiet := slog.New(slog.NewTextHandler(io.Discard, nil))
	a := newAPI(store, quiet)
	a.now = func() time.Time { return time.Date(2026, 10, 15, 12, 0, 0, 0, time.UTC) }
	return a
}

const validBody = `{
  "vehicleId": "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90",
  "recordedAt": "2026-10-15T11:59:58Z",
  "speedKmh": 82.5,
  "batteryTemp": 31.0,
  "voltage": 398.2
}`

func post(a *api, body string) *httptest.ResponseRecorder {
	request := httptest.NewRequest(http.MethodPost, "/telemetry", strings.NewReader(body))
	request.Header.Set("Content-Type", "application/json")
	recorder := httptest.NewRecorder()
	a.routes().ServeHTTP(recorder, request)
	return recorder
}

func TestPostTelemetryStoresTheReading(t *testing.T) {
	store := &fakeStore{known: true}
	recorder := post(testAPI(store), validBody)

	if recorder.Code != http.StatusAccepted {
		t.Fatalf("status = %d, want %d", recorder.Code, http.StatusAccepted)
	}
	if len(store.inserted) != 1 {
		t.Fatalf("inserted %d readings, want 1", len(store.inserted))
	}
	if store.inserted[0].SpeedKmh != 82.5 {
		t.Errorf("speed = %v, want 82.5", store.inserted[0].SpeedKmh)
	}
}

func TestPostTelemetryRejectsUnknownVehicle(t *testing.T) {
	store := &fakeStore{known: false}
	recorder := post(testAPI(store), validBody)

	if recorder.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", recorder.Code, http.StatusBadRequest)
	}
	if len(store.inserted) != 0 {
		t.Error("nothing should be stored for an unknown vehicle")
	}
	if message := errorMessage(t, recorder); !strings.Contains(message, "no such vehicle") {
		t.Errorf("message = %q, want it to mention the unknown vehicle", message)
	}
}

func TestPostTelemetryRejectsMissingField(t *testing.T) {
	body := `{"vehicleId":"3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90","recordedAt":"2026-10-15T11:59:58Z","speedKmh":10.0,"voltage":398.2}`
	recorder := post(testAPI(&fakeStore{known: true}), body)

	if recorder.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", recorder.Code, http.StatusBadRequest)
	}
	if message := errorMessage(t, recorder); !strings.Contains(message, "batteryTemp") {
		t.Errorf("message = %q, want it to name batteryTemp", message)
	}
}

func TestPostTelemetryRejectsUnknownField(t *testing.T) {
	body := `{"vehicleId":"3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90","recordedAt":"2026-10-15T11:59:58Z","speedKmh":10.0,"batteryTemp":30.0,"voltage":398.2,"tyrePressure":2.4}`
	recorder := post(testAPI(&fakeStore{known: true}), body)

	if recorder.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", recorder.Code, http.StatusBadRequest)
	}
}

func TestPostTelemetryReportsDatabaseTrouble(t *testing.T) {
	store := &fakeStore{known: true, failWith: errors.New("connection refused")}
	recorder := post(testAPI(store), validBody)

	if recorder.Code != http.StatusServiceUnavailable {
		t.Fatalf("status = %d, want %d", recorder.Code, http.StatusServiceUnavailable)
	}
}

func TestGetLatestReturnsTheNewestReading(t *testing.T) {
	recordedAt := time.Date(2026, 10, 15, 11, 59, 0, 0, time.UTC)
	store := &fakeStore{latest: &reading{
		VehicleID:   testVehicleID,
		RecordedAt:  recordedAt,
		SpeedKmh:    50,
		BatteryTemp: 28,
		Voltage:     400,
	}}

	request := httptest.NewRequest(http.MethodGet, "/vehicles/"+testVehicleID+"/latest", nil)
	recorder := httptest.NewRecorder()
	testAPI(store).routes().ServeHTTP(recorder, request)

	if recorder.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", recorder.Code, http.StatusOK)
	}
	var body readingResponse
	if err := json.Unmarshal(recorder.Body.Bytes(), &body); err != nil {
		t.Fatalf("decoding response: %v", err)
	}
	if !body.RecordedAt.Equal(recordedAt) {
		t.Errorf("recordedAt = %v, want %v", body.RecordedAt, recordedAt)
	}
	if body.SpeedKmh != 50 {
		t.Errorf("speed = %v, want 50", body.SpeedKmh)
	}
}

func TestGetLatestWithoutReadings(t *testing.T) {
	request := httptest.NewRequest(http.MethodGet, "/vehicles/"+testVehicleID+"/latest", nil)
	recorder := httptest.NewRecorder()
	testAPI(&fakeStore{}).routes().ServeHTTP(recorder, request)

	if recorder.Code != http.StatusNotFound {
		t.Fatalf("status = %d, want %d", recorder.Code, http.StatusNotFound)
	}
}

func TestGetLatestRejectsMalformedID(t *testing.T) {
	request := httptest.NewRequest(http.MethodGet, "/vehicles/not-a-uuid/latest", nil)
	recorder := httptest.NewRecorder()
	testAPI(&fakeStore{}).routes().ServeHTTP(recorder, request)

	if recorder.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", recorder.Code, http.StatusBadRequest)
	}
}

func TestHealth(t *testing.T) {
	request := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	recorder := httptest.NewRecorder()
	testAPI(&fakeStore{}).routes().ServeHTTP(recorder, request)

	if recorder.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", recorder.Code, http.StatusOK)
	}
}

func errorMessage(t *testing.T, recorder *httptest.ResponseRecorder) string {
	t.Helper()
	var body apiError
	if err := json.Unmarshal(recorder.Body.Bytes(), &body); err != nil {
		t.Fatalf("decoding error body: %v", err)
	}
	return body.Message
}
