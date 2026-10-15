-- The composite primary key (rollout_id, vehicle_id) is correct relationally but
-- awkward to map in JPA. Replace it with a surrogate key and keep the pair unique,
-- so the database still rejects a vehicle appearing twice in one rollout.

alter table rollout_target drop constraint rollout_target_pkey;

alter table rollout_target add column id uuid;
update rollout_target set id = gen_random_uuid() where id is null;
alter table rollout_target alter column id set not null;

alter table rollout_target add primary key (id);
alter table rollout_target add constraint rollout_target_unique unique (rollout_id, vehicle_id);
