package com.polygis.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    /** Access tokens issued before now are refused (refresh tokens untouched: clients silently re-issue). */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query(value = "update users set sessions_invalid_before = now() where id = :id", nativeQuery = true)
    int invalidateAccessTokens(@Param("id") java.util.UUID id);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query(value = "update users set sessions_invalid_before = now() where id in (select user_id from user_roles where role_id = :roleId)", nativeQuery = true)
    int invalidateAccessTokensForRole(@Param("roleId") java.util.UUID roleId);

    @Query(value = "select user_id from user_roles where role_id = :roleId", nativeQuery = true)
    java.util.List<java.util.UUID> userIdsWithRole(@Param("roleId") java.util.UUID roleId);

    @Query("select count(distinct u) from User u join u.roles r where r.name = :role and u.status = com.polygis.user.UserStatus.ACTIVE")
    long countActiveWithRole(@Param("role") String role);

    @Query("""
            select u from User u
            where :q = ''
               or lower(u.email) like lower(concat('%', :q, '%'))
               or lower(coalesce(u.firstName, '')) like lower(concat('%', :q, '%'))
               or lower(coalesce(u.lastName, '')) like lower(concat('%', :q, '%'))
            """)
    Page<User> search(@Param("q") String q, Pageable pageable);
}
