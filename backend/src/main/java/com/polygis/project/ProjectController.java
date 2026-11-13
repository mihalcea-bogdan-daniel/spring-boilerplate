package com.polygis.project;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.polygis.access.AccessPolicy.Scope;
import com.polygis.common.PageResponse;
import com.polygis.common.Sorts;
import com.polygis.project.ProjectDtos.*;

import jakarta.validation.Valid;

/**
 * Permissions say whether someone uses projects at all ({@code project:read}, {@code project:write}); the access
 * model says which projects. {@code scope}: VISIBLE (default), MINE, TEAM, ALL ({@code project:read-all}).
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('project:read')")
    public PageResponse<ProjectDto> list(@RequestParam(defaultValue = "VISIBLE") Scope scope,
                                         @RequestParam(required = false) String q,
                                         @PageableDefault(size = 20, sort = "createdAt",
                                                 direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(scope, q, Sorts.allow(pageable, "name", "createdAt", "updatedAt"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('project:read')")
    public ProjectDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('project:write')")
    public ProjectDto create(@Valid @RequestBody CreateProjectRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('project:write')")
    public ProjectDto update(@PathVariable UUID id, @Valid @RequestBody UpdateProjectRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('project:write')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
