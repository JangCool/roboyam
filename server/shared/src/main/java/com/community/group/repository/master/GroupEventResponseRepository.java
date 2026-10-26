package com.community.group.repository.master;

import com.community.group.entity.GroupEventResponseEntity;
import com.community.group.entity.GroupEventResponseId;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.Optional;

/** 일정 참석 응답 한 행 넣기·읽기·지우기(V92). 집계·참석자는 {@link GroupEventQueries} */
public interface GroupEventResponseRepository extends RoboyamRepository<GroupEventResponseEntity, GroupEventResponseId> {

    Optional<GroupEventResponseEntity> findByEventIdAndUserId(Long eventId, Long userId);
}
