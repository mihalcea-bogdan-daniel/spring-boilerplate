package com.polygis.user.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.polygis.user.Permission;
import com.polygis.user.Role;
import com.polygis.user.User;
import com.polygis.user.UserStatus;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserDto(UUID id, String email, String firstName, String lastName, UserStatus status,
                          boolean emailVerified, Set<String> roles, Instant lastLoginAt, Instant createdAt) {
        public static UserDto from(User u) {
            return new UserDto(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(), u.getStatus(),
                    u.isEmailVerified(), u.roleNames(), u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    /** Password optional: when absent the user signs in via e-mail OTP / social login. */
    public record CreateUserRequest(@NotBlank @Email @Size(max = 320) String email,
                                    @Size(max = 100) String firstName,
                                    @Size(max = 100) String lastName,
                                    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{10,128}$") String password,
                                    Set<String> roles) {
    }

    public record UpdateUserRequest(@Size(max = 100) String firstName,
                                    @Size(max = 100) String lastName,
                                    @NotNull UserStatus status,
                                    Set<String> roles) {
    }

    public record RoleDto(UUID id, String name, String description, boolean systemRole, Set<String> permissions) {
        public static RoleDto from(Role r) {
            return new RoleDto(r.getId(), r.getName(), r.getDescription(), r.isSystemRole(),
                    r.getPermissions().stream().map(Permission::getCode).collect(java.util.stream.Collectors.toSet()));
        }
    }

    public record RoleRequest(@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,49}$") String name,
                              @Size(max = 255) String description,
                              Set<String> permissions) {
    }

    public record PermissionDto(String code, String description) {
        public static PermissionDto from(Permission p) {
            return new PermissionDto(p.getCode(), p.getDescription());
        }
    }
}
