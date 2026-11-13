package com.polygis.group;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserGroupRepository extends JpaRepository<UserGroup, UUID> {

    List<UserGroup> findAllByOrderByNameAsc();

    @Query("select g from UserGroup g where g.id in (select m.groupId from UserGroupMember m where m.userId = :userId) order by g.name")
    List<UserGroup> findForMember(@Param("userId") UUID userId);

    @Query("select g from UserGroup g where lower(g.name) like lower(concat('%', :q, '%')) order by g.name")
    List<UserGroup> search(@Param("q") String q);
}
