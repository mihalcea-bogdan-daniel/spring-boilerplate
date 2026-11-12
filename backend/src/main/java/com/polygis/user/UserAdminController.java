package com.polygis.user;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.polygis.common.PageResponse;
import com.polygis.user.dto.UserDtos.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class UserAdminController {

    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('user:read')")
    public PageResponse<UserDto> users(@RequestParam(required = false) String q,
                                       @PageableDefault(size = 20, sort = "createdAt",
                                               direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(q, pageable);
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('user:read')")
    public UserDto user(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('user:write')")
    public UserDto create(@Valid @RequestBody CreateUserRequest req) {
        return service.create(req);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('user:write')")
    public UserDto update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/users/{id}/invite")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('user:write')")
    public void resendInvite(@PathVariable UUID id) {
        service.resendInvite(id);
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('user:write')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('role:read')")
    public List<RoleDto> roles() {
        return service.roles();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('role:read')")
    public List<PermissionDto> permissions() {
        return service.permissions();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('role:write')")
    public RoleDto createRole(@Valid @RequestBody RoleRequest req) {
        return service.createRole(req);
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('role:write')")
    public RoleDto updateRole(@PathVariable UUID id, @Valid @RequestBody RoleRequest req) {
        return service.updateRole(id, req);
    }

    @DeleteMapping("/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('role:write')")
    public void deleteRole(@PathVariable UUID id) {
        service.deleteRole(id);
    }
}
