package com.polygis.org;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class OrgDtos {

    private OrgDtos() {
    }

    public record RankDto(UUID id, String key, int level, Map<String, String> labels, List<String> aliases,
                          boolean supervises) {
        public static RankDto from(OrgRank r) {
            return new RankDto(r.getId(), r.getKey(), r.getLevel(), r.getLabels(), Arrays.asList(r.getAliases()),
                    r.isSupervises());
        }
    }

    public record RankRequest(
            @NotBlank @Size(max = 50) @Pattern(regexp = "[a-z][a-z0-9_-]*", message = "lowercase letters, digits, - and _")
            String key,
            @Min(0) @Max(10000) int level,
            @NotEmpty Map<String, @Size(max = 100) String> labels,
            List<@Size(max = 100) String> aliases,
            boolean supervises) {
    }

    public record UnitRequest(@NotBlank @Size(max = 150) String name, UUID parentId, Integer sortOrder) {
    }

    public record UnitDto(UUID id, UUID parentId, String name, int sortOrder) {
        public static UnitDto from(OrgUnit u) {
            return new UnitDto(u.getId(), u.getParentId(), u.getName(), u.getSortOrder());
        }
    }

    public record MemberRequest(@NotNull UUID userId, @NotNull UUID rankId) {
    }

    public record RankChangeRequest(@NotNull UUID rankId) {
    }

    /** A person as the organigram shows them. */
    public record MemberDto(UUID userId, String email, String name, UUID rankId, int level) {
    }

    /**
     * One unit of the chart with its members (highest rank first) and sub-units. {@code leadLevel} is the caller's
     * level over this unit (null when they don't lead it): the UI offers member management only where it is set.
     */
    public record ChartNode(UUID id, UUID parentId, String name, int sortOrder, List<MemberDto> members,
                            Integer leadLevel, List<ChartNode> children) {
    }

    public record PersonDto(UUID id, String email, String name) {
    }

    /** Someone the caller supervises, with where they sit. */
    public record SubordinateDto(UUID id, String email, String name, String status, Instant lastLoginAt,
                                 List<Placement> placements) {
        public record Placement(UUID unitId, String unitName, UUID rankId, int level) {
        }
    }
}
