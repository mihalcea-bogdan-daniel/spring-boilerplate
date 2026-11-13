# Ownership and access

Who may do what with an owned thing — a project, a file in object storage, a workflow task, a household's
car — is decided in one place, `com.polygis.access.AccessPolicy`. Modules never store an `owner_id` or compare
owners themselves. An ArchUnit rule keeps it that way: only the `access` package touches relations.

Two questions stay separate:

- **RBAC** (`@PreAuthorize("hasAuthority('project:read')")`) decides whether someone uses a module at all.
- **Access** (`AccessPolicy`) decides *which* things they may read, change, delete or share.

## Vocabulary

| Concept | Meaning | Examples |
| --- | --- | --- |
| `ResourceRef(type, id)` | something that can be owned; the id is a string so external ids fit | `project:7f3…`, `file:a91…`, `flowable.task:12345` |
| `SubjectRef(kind, id)` | who a relation points to: `USER`, `UNIT` (org unit), `GROUP` (flat group) | `user:3c…`, `unit:9a…`, `group:41…` |
| relation | a named link from a resource to a subject; an open set per type | `owner`, `editor`, `viewer`, `assignee`, `candidate` |
| action | what a caller wants to do | `read`, `write`, `delete`, `share`, `complete` |

## Who may perform an action

A caller may perform an action on a resource when one of its relations listed for that action names:

1. the caller;
2. a unit the caller belongs to. For types with `UnitInheritance.SUBTREE` (the default), a unit *above* one
   they belong to also counts: a document owned by Engineering is readable in the Platform team below it;
3. a group the caller belongs to (a household, a committee);
4. someone the caller supervises, or a unit inside a subtree the caller leads, when the type lists the action
   in `supervisorActions` (see [the organisation guide](../../frontend/docs/organisation.md));
5. or the caller holds the type's admin permission for that action (e.g. `project:read-all`).

A caller who can't even read a resource gets **404**, so its existence stays private. A caller who can read
it but lacks the action gets **403**.

## Adding an owned module

The Projects module (`com.polygis.project`) is the template.

1. **Register the type.**

   ```java
   @Bean
   ResourceTypeDefinition projectResourceType() {
       return ResourceTypeDefinition.of("project")
               .action("read",   "owner", "editor", "viewer")
               .action("write",  "owner", "editor")
               .action("delete", "owner")
               .action("share",  "owner")
               .supervisorActions("read")
               .adminPermission("project:read-all", "read");
   }
   ```

2. **The table has no owner column.** On create, record the owner:
   `access.createOwned(ResourceRef.of("project", p.getId()), owner)`. The caller may only name themself, a unit
   they belong to or lead, or a group they belong to (unless they hold the admin permission).
3. **Check every read and write:** `access.require("write", ref)` in the service, or
   `@PreAuthorize("@access.can('write', 'project', #id)")` on the controller.
4. **Lists:** `access.visibleIds("project", "read", scope)` returns the ids the caller can see (empty
   `Optional` = everything, for admins). Pass them to the repository (`where p.id in :ids`). `scope` is
   `VISIBLE`, `MINE`, `TEAM` or `ALL`.
5. **Delete:** call `access.forget(ref)` so no relations are left behind.
6. **UI:** `useAccess('project', id)` for buttons and `<AccessPanel type="project" :id>` for sharing. Both use
   the generic `/api/access/{type}/{id}` endpoints, so nothing module-specific is needed.

Grants, revokes and transfers are audited as `access.granted`, `access.revoked` and `access.transferred`. The
last owner can't be removed, only replaced (`transfer`).

## Systems that keep their own assignments

Implement `RelationSource` for types whose relations live elsewhere. The policy, the `/api/access` endpoints
and the UI work unchanged; `AccessIT` proves it with an in-memory source.

### Flowable tasks (recipe)

```java
@Component
class FlowableTaskRelations implements RelationSource {
    private final TaskService tasks;  // org.flowable.engine.TaskService

    public boolean supports(String type) { return "flowable.task".equals(type); }

    public List<RelationTuple> relationsOf(ResourceRef ref) {
        var task = tasks.createTaskQuery().taskId(ref.id()).singleResult();
        // assignee "user:<uuid>" -> USER; candidate groups "unit:<uuid>" / "group:<uuid>" -> UNIT / GROUP
        …
    }

    public Optional<List<String>> visibleIds(String type, AccessFilter f) {
        return Optional.of(tasks.createTaskQuery()
                .taskCandidateOrAssigned(f.userIds().iterator().next().toString())
                .taskCandidateGroupIn(List.copyOf(f.subjectKeys()))
                .list().stream().map(Task::getId).toList());
    }
}
```

Store subjects in Flowable as `SubjectRef.toString()` (`user:<uuid>`, `unit:<uuid>`, `group:<uuid>`) and read
them back with `SubjectRef.parse(...)`. Register the type with actions such as `read`/`complete` →
`assignee`, `candidate`. Relations of external types can't be changed through `/api/access`; the source isn't
`writable()`.

### Files in S3 (recipe)

- A `files` table holds the metadata: `id`, `storage_key` (`<tenant-slug>/<uuid>`), `size`, `content_type`,
  `sha256`. Relations go in `resource_relations` like any other type; no adapter is needed.
- The bucket is private. Downloads are presigned GET URLs with a short expiry, issued only after
  `access.require("read", ResourceRef.of("file", id))`. Uploads are presigned PUTs issued after
  `createOwned(...)`.
- Delete removes the object, the row, and `access.forget(ref)`.

## Groups

Flat sets of people that own things together: households, committees, project teams (`/api/groups`). Each
member is `admin` or `member`; a group keeps at least one admin. Holders of `group:manage` see and manage
every group. Groups are not part of the organigram, and deleting a group leaves things it owned without
that owner.
