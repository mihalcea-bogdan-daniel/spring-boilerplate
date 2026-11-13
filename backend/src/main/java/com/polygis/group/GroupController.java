package com.polygis.group;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import com.polygis.common.ApiException;
import com.polygis.group.GroupDtos.*;

import jakarta.validation.Valid;

/** Groups for people (households, committees, teams). Machine callers have no place in groups. */
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private static final String MANAGE = "group:manage";

    private final GroupService service;

    public GroupController(GroupService service) {
        this.service = service;
    }

    @GetMapping
    public List<GroupDto> list(Authentication auth) {
        return service.list(userId(auth), canManage(auth));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDto create(@Valid @RequestBody GroupRequest req, Authentication auth) {
        return service.create(userId(auth), req);
    }

    @PutMapping("/{id}")
    public GroupDto update(@PathVariable UUID id, @Valid @RequestBody GroupRequest req, Authentication auth) {
        return service.update(userId(auth), canManage(auth), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication auth) {
        service.delete(userId(auth), canManage(auth), id);
    }

    @PutMapping("/{id}/members")
    public GroupDto putMember(@PathVariable UUID id, @Valid @RequestBody GroupMemberRequest req, Authentication auth) {
        return service.putMember(userId(auth), canManage(auth), id, req);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public GroupDto removeMember(@PathVariable UUID id, @PathVariable UUID userId, Authentication auth) {
        return service.removeMember(userId(auth), canManage(auth), id, userId);
    }

    private static UUID userId(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwt) return UUID.fromString(jwt.getToken().getSubject());
        throw ApiException.forbidden("person_required", "Only people can use groups");
    }

    private static boolean canManage(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> MANAGE.equals(a.getAuthority()));
    }
}
