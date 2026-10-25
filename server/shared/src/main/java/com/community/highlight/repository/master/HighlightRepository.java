package com.community.highlight.repository.master;

import com.community.highlight.entity.HighlightEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface HighlightRepository extends RoboyamRepository<HighlightEntity, Long> {

    /** 저장한 순서(같으면 먼저 만든 것) */
    List<HighlightEntity> findAllByOrderBySortOrderAscIdAsc();

    /** 그 글의 하이라이트(글마다 하나) */
    Optional<HighlightEntity> findByPostId(Long postId);

    boolean existsByPostId(Long postId);

    /** 그 글들의 하이라이트(관리자 토론 줄의 highlightId) */
    List<HighlightEntity> findByPostIdIn(Collection<Long> postIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<HighlightEntity> findWithLockById(Long id);

    /** 모두 쓰기 잠금 — 만들기(맨 끝 순서)·순서 바꾸기를 줄 세운다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<HighlightEntity> findAllWithLockByOrderBySortOrderAscIdAsc();
}
