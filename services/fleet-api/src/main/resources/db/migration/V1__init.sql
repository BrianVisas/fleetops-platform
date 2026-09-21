-- FleetOps initial schema.
-- Owned by fleet-api. telemetry-ingest reads vehicle and writes telemetry.

create table vehicle (
    id            uuid primary key,
    vin           varchar(17) not null unique,
    model         varchar(64) not null,
    sw_version    varchar(32) not null,
    registered_at timestamptz not null default now()
);

create table software_release (
    id           uuid primary key,
    version      varchar(32) not null unique,
    target_model varchar(64) not null,
    checksum     varchar(64) not null,
    size_bytes   bigint      not null,
    published_at timestamptz not null default now()
);

create table rollout (
    id         uuid primary key,
    release_id uuid        not null references software_release (id),
    status     varchar(16) not null,
    percentage int         not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint rollout_status_valid
        check (status in ('CREATED', 'RUNNING', 'PAUSED', 'COMPLETED')),
    constraint rollout_percentage_valid
        check (percentage between 1 and 100)
);

create table rollout_target (
    rollout_id uuid        not null references rollout (id),
    vehicle_id uuid        not null references vehicle (id),
    state      varchar(16) not null,
    updated_at timestamptz not null default now(),
    primary key (rollout_id, vehicle_id),
    constraint rollout_target_state_valid
        check (state in ('PENDING', 'INSTALLING', 'SUCCEEDED', 'FAILED'))
);

create table telemetry (
    id           bigserial primary key,
    vehicle_id   uuid        not null references vehicle (id),
    recorded_at  timestamptz not null,
    speed_kmh    real        not null,
    battery_temp real        not null,
    voltage      real        not null
);

create index telemetry_vehicle_time on telemetry (vehicle_id, recorded_at desc);
create index rollout_target_state on rollout_target (rollout_id, state);
