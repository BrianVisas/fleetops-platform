-- Issue #6: a rollout can now pause itself, so a paused rollout records why.
-- MANUAL is the operator's pause; FAILURE_THRESHOLD is the automatic one.

alter table rollout add column pause_reason varchar(32);

-- Every rollout paused before this migration was paused by an operator.
update rollout set pause_reason = 'MANUAL' where status = 'PAUSED';

alter table rollout add constraint rollout_pause_reason_known
    check (pause_reason in ('MANUAL', 'FAILURE_THRESHOLD'));

-- A reason exactly when the rollout is paused, and never otherwise.
alter table rollout add constraint rollout_pause_reason_when_paused
    check ((status = 'PAUSED') = (pause_reason is not null));
