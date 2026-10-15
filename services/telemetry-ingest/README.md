# telemetry-ingest

Receives telemetry from vehicles, validates it, stores it, and serves the latest
reading per vehicle. Go, standard library only except for the Postgres driver.

## Endpoints

| Method | Path | Body | Returns |
|--------|------|------|---------|
| GET | `/healthz` | - | 200 `{"status":"UP"}` |
| POST | `/telemetry` | `{vehicleId, recordedAt, speedKmh, batteryTemp, voltage}` | 202, or 400 |
| GET | `/vehicles/{id}/latest` | - | 200 + newest reading, or 404 |

Errors use the same JSON shape as `fleet-api`:
`{timestamp, status, error, message, path}`.

## Configuration

| Variable | Default |
|----------|---------|
| `PORT` | `8081` |
| `DB_URL` | `postgres://fleetops:fleetops@localhost:5432/fleetops` |
| `DB_MAX_CONNS` | `10` |

## Run it

```bash
go mod tidy
go test ./...
go run .
```

The schema belongs to `fleet-api`: this service reads `vehicle` and writes
`telemetry`, and never migrates anything. See ADR-0004.

## Files

- `telemetry.go` - the wire format and all validation rules (no dependencies)
- `handlers.go` - routing, status codes, error bodies, the `telemetryStore` interface
- `store.go` - the only file that touches Postgres
- `main.go` - configuration, server timeouts, graceful shutdown
