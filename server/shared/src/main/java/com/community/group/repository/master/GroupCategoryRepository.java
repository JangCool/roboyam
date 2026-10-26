package com.community.group.repository.master;

import com.community.group.entity.GroupCategoryEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.List;

public interface GroupCategoryRepository extends RoboyamRepository<GroupCategoryEntity, Long> {

    /** 분류 순서대로(같으면 먼저 만든 것) */
    List<GroupCategoryEntity> findAllByOrderBySortOrderAscIdAsc();
}
