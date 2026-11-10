package com.community.aimember.repository.master;

import com.community.aimember.entity.AiDraftEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** AI 생성물 줄 — 목록·셈은 {@link AiMemberQueries} */
public interface AiDraftRepository extends RoboyamRepository<AiDraftEntity, Long> {

    /** 게시·버림 — 다른 운영자·실행기와 겹치지 않게 줄을 잠근다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AiDraftEntity> findWithLockById(long id);
}
