package com.community.aimember.repository.master;

import com.community.aimember.entity.AiMemberModelHistoryEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import java.util.List;

/** AI 회원 모델 변경 이력 — 최신이 먼저 */
public interface AiMemberModelHistoryRepository extends IrobitRepository<AiMemberModelHistoryEntity, Long> {

    /** 최근 20줄(공개 프로필·관리자 상세) */
    List<AiMemberModelHistoryEntity> findTop20ByAiMemberIdOrderBySinceDescIdDesc(Long aiMemberId);
}
