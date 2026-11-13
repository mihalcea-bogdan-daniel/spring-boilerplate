-- Organisation hierarchy: a tree of units, a catalogue of numbered ranks, and who holds which rank where.
-- "A supervises B" = A holds a supervising rank in unit U, B is in U or below it, and A's level > B's level.

create table org_ranks (
    id          uuid primary key default gen_random_uuid(),
    key         varchar(50)  not null unique,       -- stable identifier, e.g. chief
    level       int          not null check (level between 0 and 10000),
    labels      jsonb        not null default '{}', -- {"en": "Chief", "ro": "Șef"}
    aliases     text[]       not null default '{}', -- synonyms for search: Team lead, Manager...
    supervises  boolean      not null default false,
    created_at  timestamptz  not null default now()
);

create table org_units (
    id          uuid primary key default gen_random_uuid(),
    parent_id   uuid references org_units (id),     -- null only for the root
    name        varchar(150) not null,
    sort_order  int          not null default 0,
    created_at  timestamptz  not null default now()
);
create unique index ux_org_units_single_root on org_units ((parent_id is null)) where parent_id is null;
create index ix_org_units_parent on org_units (parent_id);

create table org_memberships (
    user_id     uuid not null references users (id) on delete cascade,
    unit_id     uuid not null references org_units (id) on delete cascade,
    rank_id     uuid not null references org_ranks (id),
    since       timestamptz not null default now(),
    primary key (user_id, unit_id)
);
create index ix_org_memberships_unit on org_memberships (unit_id);

insert into org_ranks (key, level, labels, aliases, supervises) values
    ('director',    900, '{"en": "Director", "ro": "Director"}',                    '{General manager,CEO}',        true),
    ('head',        500, '{"en": "Head of department", "ro": "Șef de departament"}', '{Department head}',            true),
    ('chief',       100, '{"en": "Chief", "ro": "Șef"}',                             '{Team lead,Manager}',          true),
    ('deputy',       80, '{"en": "Deputy chief", "ro": "Adjunct"}',                  '{Șef adjunct,Deputy}',         true),
    ('coordinator',  50, '{"en": "Coordinator", "ro": "Coordonator"}',               '{Supervisor}',                 true),
    ('member',       10, '{"en": "Member", "ro": "Membru"}',                         '{Staff,Angajat}',              false);

insert into org_units (name) values ('Organisation');

insert into permissions (code, description) values
    ('org:read',        'View the organisation chart'),
    ('org:manage',      'Edit units, ranks and any membership'),
    ('org:impersonate', 'Sign in as people you supervise');

insert into role_permissions (role_id, permission_id)
select r.id, p.id from roles r cross join permissions p
where r.name = 'TENANT_ADMIN' and p.code in ('org:read', 'org:manage', 'org:impersonate');

insert into role_permissions (role_id, permission_id)
select r.id, p.id from roles r cross join permissions p
where r.name = 'USER' and p.code = 'org:read';
