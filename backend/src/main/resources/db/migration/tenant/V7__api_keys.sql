create table api_keys (
    id           uuid primary key default gen_random_uuid(),
    name         varchar(100) not null,
    key_prefix   varchar(16)  not null,          -- shown in the UI to recognise a key
    key_hash     varchar(64)  not null unique,   -- SHA-256 of the full key; the key itself is never stored
    permissions  text         not null default '',  -- comma separated permission codes
    created_by   varchar(320),
    created_at   timestamptz  not null default now(),
    last_used_at timestamptz,
    revoked_at   timestamptz
);

insert into permissions (code, description) values ('apikey:manage', 'Create and revoke API keys');
insert into role_permissions (role_id, permission_id)
select r.id, p.id from roles r cross join permissions p where r.name = 'TENANT_ADMIN' and p.code = 'apikey:manage';
