-- Shared registry of tenants. Everything else lives in the tenant's own schema.
create table if not exists tenants (
    id          uuid primary key default gen_random_uuid(),
    slug        varchar(42)  not null unique,
    name        varchar(255) not null,
    schema_name varchar(63)  not null unique,
    domain      varchar(255) unique,
    status      varchar(20)  not null default 'PROVISIONING',
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);

-- The platform tenant hosts super-admins (admin.example.com).
insert into tenants (slug, name, schema_name, domain, status)
values ('${platformSlug}', 'Platform', '${platformSchema}', '${platformDomain}', 'ACTIVE')
on conflict (slug) do nothing;
