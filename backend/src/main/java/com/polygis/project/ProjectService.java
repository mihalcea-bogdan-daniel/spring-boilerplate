package com.polygis.project;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.polygis.access.AccessPolicy;
import com.polygis.access.AccessPolicy.Scope;
import com.polygis.access.RelationTuple;
import com.polygis.access.ResourceRef;
import com.polygis.access.SubjectDirectory;
import com.polygis.access.SubjectRef;
import com.polygis.audit.AuditService;
import com.polygis.common.ApiException;
import com.polygis.common.PageResponse;
import com.polygis.project.ProjectDtos.*;

/**
 * The template for owned modules: every read and write goes through {@link AccessPolicy}; the module never
 * stores or compares owners itself.
 */
@Service
public class ProjectService {

    private static final String TYPE = ProjectAccessConfig.TYPE;
    private static final Set<UUID> NONE = Set.of(new UUID(0, 0));

    private final ProjectRepository projects;
    private final AccessPolicy access;
    private final SubjectDirectory directory;
    private final AuditService audit;

    public ProjectService(ProjectRepository projects, AccessPolicy access, SubjectDirectory directory,
                          AuditService audit) {
        this.projects = projects;
        this.access = access;
        this.directory = directory;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectDto> list(Scope scope, String q, Pageable pageable) {
        Optional<List<String>> visible = access.visibleIds(TYPE, "read", scope);
        Set<UUID> ids = visible.map(list -> list.stream().map(UUID::fromString).collect(Collectors.toSet()))
                .orElse(Set.of());
        Page<Project> page = projects.search(visible.isEmpty(), ids.isEmpty() ? NONE : ids,
                q == null ? "" : q.trim(), pageable);
        return PageResponse.of(page, this::dto);
    }

    @Transactional(readOnly = true)
    public ProjectDto get(UUID id) {
        access.require("read", ref(id));
        return dto(project(id));
    }

    @Transactional
    public ProjectDto create(CreateProjectRequest req) {
        SubjectRef owner = req.ownerKind() == null || req.ownerId() == null
                ? SubjectRef.user(AccessPolicy.currentUserId())
                : new SubjectRef(req.ownerKind(), req.ownerId());
        if (owner.id() == null) throw ApiException.forbidden("person_required", "Only people can own projects");
        Project p = new Project();
        p.setName(req.name().trim());
        p.setDescription(req.description());
        projects.save(p);
        access.createOwned(ref(p.getId()), owner);
        audit.record("project.created", TYPE, p.getId(), p.getName());
        return dto(p);
    }

    @Transactional
    public ProjectDto update(UUID id, UpdateProjectRequest req) {
        access.require("write", ref(id));
        Project p = project(id);
        p.setName(req.name().trim());
        p.setDescription(req.description());
        p.setUpdatedAt(Instant.now());
        audit.record("project.updated", TYPE, id, p.getName());
        return dto(p);
    }

    @Transactional
    public void delete(UUID id) {
        access.require("delete", ref(id));
        Project p = project(id);
        projects.delete(p);
        access.forget(ref(id));
        audit.record("project.deleted", TYPE, id, p.getName());
    }

    private Project project(UUID id) {
        return projects.findById(id).orElseThrow(() -> ApiException.notFound("Project"));
    }

    private static ResourceRef ref(UUID id) {
        return ResourceRef.of(TYPE, id);
    }

    private ProjectDto dto(Project p) {
        List<SubjectRef> owners = access.relations(ref(p.getId())).stream()
                .filter(t -> "owner".equals(t.relation())).map(RelationTuple::subject).toList();
        var names = directory.describe(owners);
        return new ProjectDto(p.getId(), p.getName(), p.getDescription(), owners.stream().map(names::get).toList(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
