package com.community.group.repository.master;

import com.community.group.entity.GroupSettingHistoryEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 소모임 설정 변경 이력 쓰기 — 조회(페이징·운영자 이름)는 {@link GroupRepositoryQDSL#settingHistory} */
public interface GroupSettingHistoryRepository extends RoboyamRepository<GroupSettingHistoryEntity, Long> {
}
