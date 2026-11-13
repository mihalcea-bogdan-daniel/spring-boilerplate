package com.polygis.group;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class GroupDtos {

    private GroupDtos() {
    }

    public record GroupRequest(@NotBlank @Size(max = 150) String name,
                               @Size(max = 40) @Pattern(regexp = "[a-z][a-z0-9_-]*") String kind) {
    }

    public record GroupMemberRequest(@NotNull UUID userId, @Pattern(regexp = "admin|member") String role) {
    }

    public record GroupMemberDto(UUID userId, String email, String name, String role) {
    }

    public record GroupDto(UUID id, String kind, String name, List<GroupMemberDto> members, boolean canManage) {
    }
}
