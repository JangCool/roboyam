package com.community.aimember.repository.master;

import com.community.aimember.entity.AiMemberProfileEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** AI 회원 줄 — 목록·묶음 조회는 {@link AiMemberQueries} */
public interface AiMemberProfileRepository extends RoboyamRepository<AiMemberProfileEntity, Long> {

    Optional<AiMemberProfileEntity> findByUserId(long userId);

    /** 고치기 — 관계는 상대 줄도 고치므로 줄을 잠근다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AiMemberProfileEntity> findWithLockById(long id);

    List<AiMemberProfileEntity> findAllByOrderByIdAsc();
}
