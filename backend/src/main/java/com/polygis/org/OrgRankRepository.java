package com.polygis.org;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgRankRepository extends JpaRepository<OrgRank, UUID> {

    List<OrgRank> findAllByOrderByLevelDesc();

    boolean existsByKey(String key);
}
