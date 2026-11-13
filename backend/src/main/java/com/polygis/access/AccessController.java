package com.polygis.access;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.polygis.access.SubjectDirectory.SubjectDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Generic access API for every resource type: what may I do, who has access, share, revoke, transfer.
 * The UI's {@code useAccess()} and {@code <AccessPanel>} talk to this, whatever the module.
 */
@RestController
@RequestMapping("/api/access")
public class AccessController {

    public record RelationDto(String relation, SubjectDto subject) {
    }

    public record AccessDto(String type, String id, Map<String, Boolean> can, List<String> relationNames,
                            String ownerRelation, List<RelationDto> relations) {
    }

    public record RelationRequest(@NotBlank @Size(max = 40) String relation, @NotNull SubjectKind subjectKind,
                                  @NotNull UUID subjectId) {
    }

    public record TransferRequest(@NotNull SubjectKind subjectKind, @NotNull UUID subjectId) {
    }

    private final AccessPolicy policy;
    private final SubjectDirectory directory;

    public AccessController(AccessPolicy policy, SubjectDirectory directory) {
        this.policy = policy;
        this.directory = directory;
    }

    @GetMapping("/{type}/{id}")
    public AccessDto get(@PathVariable String type, @PathVariable String id) {
        ResourceRef ref = new ResourceRef(type, id);
        policy.require("read", ref);
        ResourceTypeDefinition def = policy.type(type);
        List<RelationTuple> tuples = policy.relations(ref);
        var names = directory.describe(tuples.stream().map(RelationTuple::subject).distinct().toList());
        return new AccessDto(type, id, policy.allowedActions(ref), List.copyOf(def.relations()), def.ownerRelation(),
                tuples.stream().map(t -> new RelationDto(t.relation(), names.get(t.subject()))).toList());
    }

    @PostMapping("/{type}/{id}/relations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grant(@PathVariable String type, @PathVariable String id, @Valid @RequestBody RelationRequest req) {
        policy.grant(new ResourceRef(type, id), req.relation(), new SubjectRef(req.subjectKind(), req.subjectId()));
    }

    @DeleteMapping("/{type}/{id}/relations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable String type, @PathVariable String id, @Valid @RequestBody RelationRequest req) {
        policy.revoke(new ResourceRef(type, id), req.relation(), new SubjectRef(req.subjectKind(), req.subjectId()));
    }

    @PostMapping("/{type}/{id}/transfer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transfer(@PathVariable String type, @PathVariable String id, @Valid @RequestBody TransferRequest req) {
        policy.transferOwnership(new ResourceRef(type, id), new SubjectRef(req.subjectKind(), req.subjectId()));
    }

    /** Candidates for "share with…": people, units and groups. */
    @GetMapping("/subjects")
    @PreAuthorize("isAuthenticated()")
    public List<SubjectDto> subjects(@RequestParam(required = false) String q) {
        if (AccessPolicy.currentUserId() == null) return List.of();
        return directory.search(q);
    }
}
