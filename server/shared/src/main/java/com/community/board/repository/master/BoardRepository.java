package com.community.board.repository.master;

import com.community.board.entity.BoardEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface BoardRepository extends RoboyamRepository<BoardEntity, Long> {

    /** 행을 쓰기 잠금으로 읽는다 — 같은 게시판을 동시에 고치거나 담당을 지정·해제하거나 지우는 요청을 줄 세울 때. 트랜잭션 안에서만 의미가 있다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BoardEntity> findWithLockById(Long id);

    Optional<BoardEntity> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** 정렬 순서대로 모든 게시판(꺼진 것 포함) */
    List<BoardEntity> findAllByOrderBySortOrderAscIdAsc();
}
