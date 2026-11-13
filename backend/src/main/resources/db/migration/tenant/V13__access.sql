-- Access model: who may do what with any resource, whatever module (or external system) it lives in.
-- A relation links a resource (type + id) to a subject (a user, an org unit or a group): "project 7f3 owner user 12".
-- Resource ids are text so external ids (Flowable task ids, S3 object ids...) fit too.

create table resource_relations (
    id            uuid primary key default gen_random_uuid(),
    resource_type varchar(60)  not null,
    resource_id   varchar(200) not null,
    relation      varchar(40)  not null,       -- owner | editor | viewer | assignee | candidate ...
    subject_kind  varchar(10)  not null check (subject_kind in ('USER', 'UNIT', 'GROUP')),
    subject_id    uuid         not null,
    created_by    uuid,
    created_at    timestamptz  not null default now(),
    unique (resource_type, resource_id, relation, subject_kind, subject_id)
);
create index ix_relations_subject on resource_relations (subject_kind, subject_id, resource_type);

-- Flat, non-hierarchical groups: households, committees, project teams. Not part of the organigram.
create table user_groups (
    id          uuid primary key default gen_random_uuid(),
    kind        varchar(40)  not null default 'team',   -- free text: household, committee, team...
    name        varchar(150) not null,
    created_at  timestamptz  not null default now()
);

create table user_group_members (
    group_id    uuid not null references user_groups (id) on delete cascade,
    user_id     uuid not null references users (id) on delete cascade,
    role        varchar(20) not null default 'member' check (role in ('admin', 'member')),
    primary key (group_id, user_id)
);
create index ix_group_members_user on user_group_members (user_id);

-- Reference module: ownership is a resource_relations row, never a column here.
create table projects (
    id          uuid primary key default gen_random_uuid(),
    name        varchar(150) not null,
    description text,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);

insert into permissions (code, description) values
    ('group:manage',     'Create groups and manage any group''s members'),
    ('project:read',     'Use projects (which ones is decided by ownership and sharing)'),
    ('project:write',    'Create projects'),
    ('project:read-all', 'See every project, whoever owns it');

insert into role_permissions (role_id, permission_id)
select r.id, p.id from roles r cross join permissions p
where r.name = 'TENANT_ADMIN' and p.code in ('group:manage', 'project:read', 'project:write', 'project:read-all');

insert into role_permissions (role_id, permission_id)
select r.id, p.id from roles r cross join permissions p
where r.name = 'USER' and p.code in ('project:read', 'project:write');
