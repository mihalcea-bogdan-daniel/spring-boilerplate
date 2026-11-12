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
