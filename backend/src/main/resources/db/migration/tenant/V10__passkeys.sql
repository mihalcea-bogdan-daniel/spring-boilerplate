create table passkeys (
    id                 uuid primary key default gen_random_uuid(),
    user_id            uuid         not null references users (id) on delete cascade,
    credential_id      varchar(1400) not null unique,   -- base64url, as sent by the browser
    credential_data    bytea        not null,           -- AAGUID + credential id + COSE public key
    sign_count         bigint       not null default 0,
    uv_initialized     boolean      not null default false,
    backup_eligible    boolean      not null default false,
    backup_state       boolean      not null default false,
    transports         varchar(200),
    name               varchar(100) not null,
    created_at         timestamptz  not null default now(),
    last_used_at       timestamptz
);
create index ix_passkeys_user on passkeys (user_id);

-- Sign-in challenges are issued before we know who is signing in.
alter table one_time_tokens alter column user_id drop not null;
