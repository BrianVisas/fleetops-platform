package main

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func testClient(t *testing.T, handler http.Handler) *client {
	t.Helper()
	server := httptest.NewServer(handler)
	t.Cleanup(server.Close)
	return newClient(server.URL, server.URL, 2*time.Second)
}

func TestRegisterReturnsTheVehicleID(t *testing.T) {
	var got registerRequest

	api := testClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/api/v1/vehicles" || r.Method != http.MethodPost {
			t.Errorf("unexpected call: %s %s", r.Method, r.URL.Path)
		}
		if err := json.NewDecoder(r.Body).Decode(&got); err != nil {
			t.Fatalf("decoding request: %v", err)
		}
		w.WriteHeader(http.StatusCreated)
		_, _ = io.WriteString(w, `{"id":"3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90","vin":"SIMABCDEF00000001"}`)
	}))

	id, err := api.register(context.Background(), "SIMABCDEF00000001", "ID.4", "1.0.0")
	if err != nil {
		t.Fatalf("register: %v", err)
	}
	if id != "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90" {
		t.Errorf("id = %q", id)
	}
	if got.Model != "ID.4" || got.SwVersion != "1.0.0" {
		t.Errorf("request body = %+v", got)
	}
}

func TestRegisterReportsConflict(t *testing.T) {
	api := testClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusConflict)
		_, _ = io.WriteString(w, `{"status":409,"message":"a vehicle with VIN ... is already registered"}`)
	}))

	_, err := api.register(context.Background(), "SIMABCDEF00000001", "ID.4", "1.0.0")
	if err == nil {
		t.Fatal("a 409 must be reported as an error")
	}
	if !strings.Contains(err.Error(), "409") {
		t.Errorf("error should name the status code, got %q", err)
	}
}

func TestSendTelemetryPostsTheReading(t *testing.T) {
	var got telemetryPayload

	api := testClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/telemetry" {
			t.Errorf("path = %q, want /telemetry", r.URL.Path)
		}
		if err := json.NewDecoder(r.Body).Decode(&got); err != nil {
			t.Fatalf("decoding request: %v", err)
		}
		w.WriteHeader(http.StatusAccepted)
	}))

	state := vehicleState{SpeedKmh: 82.46, BatteryTemp: 31.02, Voltage: 398.19}
	err := api.sendTelemetry(context.Background(), "vehicle-1", state, time.Now())
	if err != nil {
		t.Fatalf("sendTelemetry: %v", err)
	}
	if got.SpeedKmh != 82.5 {
		t.Errorf("speed = %v, want it rounded to 82.5", got.SpeedKmh)
	}
	if got.RecordedAt.Location() != time.UTC {
		t.Errorf("recordedAt should be sent in UTC, got %v", got.RecordedAt.Location())
	}
}

func TestAssignedUpdateHandlesNoContent(t *testing.T) {
	api := testClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	}))

	_, assigned, err := api.assignedUpdate(context.Background(), "vehicle-1")
	if err != nil {
		t.Fatalf("a 204 is not an error: %v", err)
	}
	if assigned {
		t.Error("assigned should be false on 204")
	}
}

func TestAssignedUpdateReturnsTheRelease(t *testing.T) {
	api := testClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = io.WriteString(w, `{"version":"2.1.0","checksum":"abc123","sizeBytes":1048576}`)
	}))

	update, assigned, err := api.assignedUpdate(context.Background(), "vehicle-1")
	if err != nil || !assigned {
		t.Fatalf("assigned = %v, err = %v", assigned, err)
	}
	if update.Version != "2.1.0" || update.SizeBytes != 1048576 {
		t.Errorf("update = %+v", update)
	}
}

func TestReportResultSendsTheOutcome(t *testing.T) {
	var got resultPayload

	api := testClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !strings.HasSuffix(r.URL.Path, "/update/result") {
			t.Errorf("path = %q", r.URL.Path)
		}
		if err := json.NewDecoder(r.Body).Decode(&got); err != nil {
			t.Fatalf("decoding request: %v", err)
		}
		w.WriteHeader(http.StatusAccepted)
	}))

	if err := api.reportResult(context.Background(), "vehicle-1", "2.1.0", false); err != nil {
		t.Fatalf("reportResult: %v", err)
	}
	if got.Version != "2.1.0" || got.Success {
		t.Errorf("payload = %+v", got)
	}
}

func TestUnexpectedStatusIsAnError(t *testing.T) {
	api := testClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		_, _ = io.WriteString(w, "boom")
	}))

	err := api.sendTelemetry(context.Background(), "vehicle-1", vehicleState{}, time.Now())
	if err == nil {
		t.Fatal("a 500 must be reported")
	}
	if !strings.Contains(err.Error(), "500") || !strings.Contains(err.Error(), "boom") {
		t.Errorf("error should carry the status and the body, got %q", err)
	}
}
