package com.community.debate.repository.master;

import com.community.debate.entity.DebatePinRequestEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 의견 고정 요청(V78) */
public interface DebatePinRequestRepository extends RoboyamRepository<DebatePinRequestEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DebatePinRequestEntity> findWithLockById(Long id);

    /** 그 의견의 대기 요청(하나뿐) */
    Optional<DebatePinRequestEntity> findFirstByCommentIdAndStatus(Long commentId, String status);

    /** 그 글의 그 상태 요청 — 오래된 것부터 */
    List<DebatePinRequestEntity> findByPostIdAndStatusOrderByIdAsc(Long postId, String status);

    /** 그 글에서 그 회원이 보낸 요청 전부 — 댓글 목록의 내 요청 상태 */
    List<DebatePinRequestEntity> findByPostIdAndRequesterId(Long postId, Long requesterId);
}
