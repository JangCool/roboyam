package com.community.sanction.repository.master;

import com.community.sanction.entity.BoardStaffActionEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** {@code BoardStaffActionEntity} 넣기({@code persist}) — 읽기와 조건부 고치기는 저장소의 QueryDSL */
public interface BoardStaffActionRepository extends RoboyamRepository<BoardStaffActionEntity, Long> {
}
