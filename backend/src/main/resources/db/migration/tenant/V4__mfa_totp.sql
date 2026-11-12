alter table users add column totp_secret    varchar(64);
alter table users add column totp_enabled   boolean not null default false;
alter table users add column totp_last_step bigint;     -- replay protection: a code is accepted once

create table mfa_recovery_codes (
    id        uuid primary key default gen_random_uuid(),
    user_id   uuid        not null references users (id) on delete cascade,
    code_hash varchar(64) not null,
    used_at   timestamptz
);
create index ix_mfa_recovery_user on mfa_recovery_codes (user_id);
