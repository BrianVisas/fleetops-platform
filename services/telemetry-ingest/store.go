package main

import (
	"context"
	"errors"
	"fmt"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// postgresStore is the only file in this service that knows about the database.
// Everything else talks to the telemetryStore interface.
type postgresStore struct {
	pool *pgxpool.Pool
}

func newPostgresStore(ctx context.Context, url string, maxConns int32) (*postgresStore, error) {
	config, err := pgxpool.ParseConfig(url)
	if err != nil {
		return nil, fmt.Errorf("parsing database url: %w", err)
	}
	config.MaxConns = maxConns

	pool, err := pgxpool.NewWithConfig(ctx, config)
	if err != nil {
		return nil, fmt.Errorf("creating pool: %w", err)
	}
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("pinging database: %w", err)
	}
	return &postgresStore{pool: pool}, nil
}

func (s *postgresStore) close() {
	s.pool.Close()
}

func (s *postgresStore) vehicleExists(ctx context.Context, vehicleID string) (bool, error) {
	var exists bool
	err := s.pool.QueryRow(ctx,
		`select exists(select 1 from vehicle where id = $1)`, vehicleID).Scan(&exists)
	if err != nil {
		return false, fmt.Errorf("checking vehicle: %w", err)
	}
	return exists, nil
}

func (s *postgresStore) insertReading(ctx context.Context, r reading) error {
	_, err := s.pool.Exec(ctx,
		`insert into telemetry (vehicle_id, recorded_at, speed_kmh, battery_temp, voltage)
		 values ($1, $2, $3, $4, $5)`,
		r.VehicleID, r.RecordedAt, r.SpeedKmh, r.BatteryTemp, r.Voltage)
	if err != nil {
		return fmt.Errorf("inserting telemetry: %w", err)
	}
	return nil
}

func (s *postgresStore) latestReading(ctx context.Context, vehicleID string) (reading, bool, error) {
	var r reading
	err := s.pool.QueryRow(ctx,
		`select vehicle_id, recorded_at, speed_kmh, battery_temp, voltage
		   from telemetry
		  where vehicle_id = $1
		  order by recorded_at desc
		  limit 1`, vehicleID).
		Scan(&r.VehicleID, &r.RecordedAt, &r.SpeedKmh, &r.BatteryTemp, &r.Voltage)

	if errors.Is(err, pgx.ErrNoRows) {
		return reading{}, false, nil
	}
	if err != nil {
		return reading{}, false, fmt.Errorf("reading latest telemetry: %w", err)
	}
	return r, true, nil
}
