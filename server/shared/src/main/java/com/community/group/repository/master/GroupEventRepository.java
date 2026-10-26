package com.community.group.repository.master;

import com.community.group.entity.GroupEventEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.Optional;

/** 소모임 일정 한 건 넣기·읽기(V93). 목록·집계는 {@link GroupEventQueries} */
public interface GroupEventRepository extends RoboyamRepository<GroupEventEntity, Long> {

    /** 이 소모임의 일정 한 건(다른 소모임의 일정이면 비어 있다) */
    Optional<GroupEventEntity> findByIdAndGroupId(Long id, Long groupId);
}
