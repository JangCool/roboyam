package com.community.popular.repository.master;

import com.community.popular.entity.PopularExclusionEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.List;

public interface PopularExclusionRepository extends RoboyamRepository<PopularExclusionEntity, Long> {

    /** 지금 빠져 있는 글 — 최근에 뺀 것부터 200개(관리 화면의 「뺀 글」) */
    List<PopularExclusionEntity> findTop200ByOrderByExcludedAtDescPostIdDesc();
}
