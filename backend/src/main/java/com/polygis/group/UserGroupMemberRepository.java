package com.polygis.group;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserGroupMemberRepository extends JpaRepository<UserGroupMember, UserGroupMember.Key> {

    List<UserGroupMember> findByGroupId(UUID groupId);

    @Query("select m.groupId from UserGroupMember m where m.userId = :userId")
    List<UUID> groupIdsOf(@Param("userId") UUID userId);

    long countByGroupIdAndRole(UUID groupId, String role);
}
