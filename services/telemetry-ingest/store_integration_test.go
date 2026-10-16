//go:build integration

// These tests need Docker, so they are behind a build tag and run with
//
//	go test -tags=integration ./...
//
// They apply fleet-api's own migration files to a throwaway Postgres, which is
// what makes the shared-database coupling from ADR-0004 visible: rename a column
// in fleet-api and this test fails instead of production.
package main

import (
	"context"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/modules/postgres"
	"github.com/testcontainers/testcontainers-go/wait"
)

const migrations = "../fleet-api/src/main/resources/db/migration"

func startPostgres(t *testing.T) string {
	t.Helper()
	ctx := context.Background()

	container, err := postgres.Run(ctx, "postgres:17",
		postgres.WithDatabase("fleetops"),
		postgres.WithUsername("fleetops"),
		postgres.WithPassword("fleetops"),
		testcontainers.WithWaitStrategy(
			wait.ForLog("database system is ready to accept connections").
				WithOccurrence(2).
				WithStartupTimeout(60*time.Second)))
	if err != nil {
		t.Fatalf("starting postgres: %v", err)
	}
	t.Cleanup(func() { testcontainers.CleanupContainer(t, container) })

	dsn, err := container.ConnectionString(ctx, "sslmode=disable")
	if err != nil {
		t.Fatalf("connection string: %v", err)
	}

	applyMigrations(t, ctx, dsn)
	return dsn
}

// applyMigrations runs V1 and V2 in order. The simple protocol is needed because
// each file holds several statements, and pgx's default prepared-statement mode
// refuses more than one per call.
func applyMigrations(t *testing.T, ctx context.Context, dsn string) {
	t.Helper()

	config, err := pgxpool.ParseConfig(dsn + "&default_query_exec_mode=simple_protocol")
	if err != nil {
		t.Fatalf("parsing dsn: %v", err)
	}
	pool, err := pgxpool.NewWithConfig(ctx, config)
	if err != nil {
		t.Fatalf("connecting for migrations: %v", err)
	}
	defer pool.Close()

	files, err := filepath.Glob(filepath.Join(migrations, "V*.sql"))
	if err != nil || len(files) == 0 {
		t.Fatalf("no migration files found under %s (err %v)", migrations, err)
	}
	// Glob sorts lexically, which matches V1, V2 ... up to V9.
	for _, file := range files {
		sql, err := os.ReadFile(file)
		if err != nil {
			t.Fatalf("reading %s: %v", file, err)
		}
		if _, err := pool.Exec(ctx, string(sql)); err != nil {
			t.Fatalf("applying %s: %v", filepath.Base(file), err)
		}
	}
}

func insertVehicle(t *testing.T, dsn, id, vin string) {
	t.Helper()
	ctx := context.Background()

	config, _ := pgxpool.ParseConfig(dsn)
	pool, err := pgxpool.NewWithConfig(ctx, config)
	if err != nil {
		t.Fatalf("connecting: %v", err)
	}
	defer pool.Close()

	_, err = pool.Exec(ctx,
		`insert into vehicle (id, vin, model, sw_version) values ($1, $2, 'ID.4', '1.0.0')`, id, vin)
	if err != nil {
		t.Fatalf("inserting vehicle: %v", err)
	}
}

func TestStoreAgainstRealPostgres(t *testing.T) {
	dsn := startPostgres(t)
	ctx := context.Background()

	store, err := newPostgresStore(ctx, dsn, 5)
	if err != nil {
		t.Fatalf("creating store: %v", err)
	}
	defer store.close()

	const vehicleID = "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90"
	insertVehicle(t, dsn, vehicleID, "WVWZZZ1JZXW000500")

	t.Run("vehicleExists", func(t *testing.T) {
		known, err := store.vehicleExists(ctx, vehicleID)
		if err != nil || !known {
			t.Fatalf("known = %v, err = %v", known, err)
		}

		unknown, err := store.vehicleExists(ctx, "00000000-0000-0000-0000-000000000000")
		if err != nil || unknown {
			t.Fatalf("unknown = %v, err = %v", unknown, err)
		}
	})

	t.Run("no readings yet", func(t *testing.T) {
		_, found, err := store.latestReading(ctx, vehicleID)
		if err != nil {
			t.Fatalf("latestReading: %v", err)
		}
		if found {
			t.Error("expected no readings before any insert")
		}
	})

	t.Run("insert and read back the newest", func(t *testing.T) {
		older := time.Now().Add(-time.Minute).UTC().Truncate(time.Millisecond)
		newer := time.Now().UTC().Truncate(time.Millisecond)

		for _, r := range []reading{
			{VehicleID: vehicleID, RecordedAt: older, SpeedKmh: 10, BatteryTemp: 20, Voltage: 390},
			{VehicleID: vehicleID, RecordedAt: newer, SpeedKmh: 82.5, BatteryTemp: 31, Voltage: 398.2},
		} {
			if err := store.insertReading(ctx, r); err != nil {
				t.Fatalf("insertReading: %v", err)
			}
		}

		latest, found, err := store.latestReading(ctx, vehicleID)
		if err != nil || !found {
			t.Fatalf("found = %v, err = %v", found, err)
		}
		if !latest.RecordedAt.Equal(newer) {
			t.Errorf("recordedAt = %v, want the newer reading %v", latest.RecordedAt, newer)
		}
		if latest.SpeedKmh < 82.4 || latest.SpeedKmh > 82.6 {
			t.Errorf("speed = %v, want about 82.5", latest.SpeedKmh)
		}
	})

	t.Run("telemetry for an unknown vehicle is rejected by the foreign key", func(t *testing.T) {
		err := store.insertReading(ctx, reading{
			VehicleID:   "00000000-0000-0000-0000-000000000000",
			RecordedAt:  time.Now().UTC(),
			SpeedKmh:    1,
			BatteryTemp: 20,
			Voltage:     390,
		})
		if err == nil {
			t.Error("the foreign key on telemetry.vehicle_id should reject this insert")
		}
	})
}
