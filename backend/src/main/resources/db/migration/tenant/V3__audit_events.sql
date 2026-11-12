create table audit_events (
    id            uuid primary key default gen_random_uuid(),
    occurred_at   timestamptz  not null default now(),
    actor_user_id uuid,                       -- null for anonymous actions (failed login...)
    actor_email   varchar(320),
    actor_tenant  varchar(42),                -- differs from this schema's tenant when a super-admin acted via X-Tenant-ID
    action        varchar(60)  not null,      -- e.g. user.created
    target_type   varchar(40),
    target_id     varchar(100),
    details       text,
    ip_address    varchar(64)
);
create index ix_audit_occurred on audit_events (occurred_at desc);
create index ix_audit_action on audit_events (action);

insert into permissions (code, description) values ('audit:read', 'View the audit log');
insert into role_permissions (role_id, permission_id)
select r.id, p.id from roles r cross join permissions p where r.name = 'TENANT_ADMIN' and p.code = 'audit:read';
