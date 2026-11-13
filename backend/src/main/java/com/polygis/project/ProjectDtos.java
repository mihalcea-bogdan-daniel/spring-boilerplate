package com.polygis.project;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.polygis.access.SubjectDirectory.SubjectDto;
import com.polygis.access.SubjectKind;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    /** Owner defaults to the caller; a unit or group the caller belongs to may be named instead. */
    public record CreateProjectRequest(@NotBlank @Size(max = 150) String name, @Size(max = 5000) String description,
                                       SubjectKind ownerKind, UUID ownerId) {
    }

    public record UpdateProjectRequest(@NotBlank @Size(max = 150) String name, @Size(max = 5000) String description) {
    }

    public record ProjectDto(UUID id, String name, String description, List<SubjectDto> owners, Instant createdAt,
                             Instant updatedAt) {
    }
}
