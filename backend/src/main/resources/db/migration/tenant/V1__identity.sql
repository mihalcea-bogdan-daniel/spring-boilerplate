-- Runs once per tenant schema (search_path = that schema). Never schema-qualify here.

create table users (
    id                   uuid primary key default gen_random_uuid(),
    email                varchar(320) not null,
    password_hash        varchar(100),
    first_name           varchar(100),
    last_name            varchar(100),
    status               varchar(20)  not null default 'PENDING',   -- PENDING | ACTIVE | DISABLED
    email_verified       boolean      not null default false,
    failed_login_count   int          not null default 0,
    locked_until         timestamptz,
    last_login_at        timestamptz,
    created_at           timestamptz  not null default now(),
    updated_at           timestamptz  not null default now()
);
create unique index ux_users_email on users (lower(email));

create table user_identities (               -- linked OAuth2 accounts
    id               uuid primary key default gen_random_uuid(),
    user_id          uuid not null references users (id) on delete cascade,
    provider         varchar(40)  not null,
    provider_user_id varchar(255) not null,
    created_at       timestamptz  not null default now(),
    unique (provider, provider_user_id)
);

create table permissions (
    id          uuid primary key default gen_random_uuid(),
    code        varchar(100) not null unique,   -- e.g. user:read
    description varchar(255)
);

create table roles (
    id          uuid primary key default gen_random_uuid(),
    name        varchar(50)  not null unique,   -- e.g. TENANT_ADMIN
    description varchar(255),
    system_role boolean      not null default false
);

create table role_permissions (
    role_id       uuid not null references roles (id) on delete cascade,
    permission_id uuid not null references permissions (id) on delete cascade,
    primary key (role_id, permission_id)
);

create table user_roles (
    user_id uuid not null references users (id) on delete cascade,
    role_id uuid not null references roles (id) on delete cascade,
    primary key (user_id, role_id)
);

create table refresh_tokens (
    id           uuid primary key default gen_random_uuid(),
    user_id      uuid        not null references users (id) on delete cascade,
    token_hash   varchar(64) not null unique,   -- SHA-256 hex, the raw token is never stored
    family_id    uuid        not null,          -- rotation family: reuse of a rotated token revokes the family
    expires_at   timestamptz not null,
    revoked_at   timestamptz,
    replaced_by  uuid,
    user_agent   varchar(255),
    ip_address   varchar(64),
    created_at   timestamptz not null default now()
);
create index ix_refresh_tokens_user on refresh_tokens (user_id);

create table one_time_tokens (                -- email OTPs + OAuth2 exchange codes
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid        not null references users (id) on delete cascade,
    purpose     varchar(30) not null,           -- EMAIL_VERIFICATION | LOGIN | PASSWORD_RESET | OAUTH2_EXCHANGE
    code_hash   varchar(64) not null,
    attempts    int         not null default 0,
    expires_at  timestamptz not null,
    consumed_at timestamptz,
    created_at  timestamptz not null default now()
);
create index ix_ott_user_purpose on one_time_tokens (user_id, purpose);

create table tenant_settings (
    key        varchar(100) primary key,
    value      text,
    updated_at timestamptz not null default now()
);

-- Seed RBAC catalogue
insert into permissions (code, description) values
    ('user:read',      'List and view users'),
    ('user:write',     'Create, update and disable users'),
    ('role:read',      'List roles and permissions'),
    ('role:write',     'Create and edit roles'),
    ('settings:read',  'Read tenant settings'),
    ('settings:write', 'Change tenant settings');

insert into roles (name, description, system_role) values
    ('TENANT_ADMIN', 'Full administrative access to this tenant', true),
    ('USER',         'Standard user',                              true);

insert into role_permissions (role_id, permission_id)
select r.id, p.id from roles r cross join permissions p where r.name = 'TENANT_ADMIN';

insert into tenant_settings (key, value) values
    ('general.displayName', null),
    ('auth.allowSelfRegistration', 'true'),
    ('auth.allowSocialLogin', 'true');
