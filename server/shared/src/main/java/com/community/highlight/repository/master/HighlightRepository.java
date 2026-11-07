package com.community.highlight.repository.master;

import com.community.highlight.entity.HighlightEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** 하이라이트 — 목록·순서는 놓일 곳({@code place}, V161)마다 따로다. 같은 글이 여러 곳에 있을 수 있다 */
public interface HighlightRepository extends RoboyamRepository<HighlightEntity, Long> {

    /** 그 곳의 저장한 순서(같으면 먼저 만든 것) */
    List<HighlightEntity> findAllByPlaceOrderBySortOrderAscIdAsc(String place);

    /** 곳마다 하이라이트 수 — {@code [place, count]} 줄(관리자 곳 목록의 개수) */
    @Query("select h.place, count(h) from HighlightEntity h group by h.place")
    List<Object[]> countByPlace();

    /** 그 글의 하이라이트(곳마다 하나 — 여러 곳이면 여러 줄) */
    List<HighlightEntity> findAllByPostId(Long postId);

    /** 그 글들의 하이라이트(관리자 토론 줄의 highlightId) */
    List<HighlightEntity> findByPostIdIn(Collection<Long> postIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<HighlightEntity> findWithLockById(Long id);

    /** 그 곳을 모두 쓰기 잠금 — 만들기(맨 끝 순서·중복)·순서 바꾸기를 곳마다 줄 세운다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<HighlightEntity> findAllWithLockByPlaceOrderBySortOrderAscIdAsc(String place);
}
