package com.community.comment.repository.master;

import com.community.comment.entity.CommentEntity;
import com.community.comment.entity.CommentId;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface CommentRepository extends RoboyamRepository<CommentEntity, CommentId> {

    /** 글 하나의 댓글 전부(지운 것 포함, 오래된순) — 글 ID 로 해시 조각 하나만 읽는다 */
    List<CommentEntity> findByPostIdOrderByIdAsc(Long postId);

    Optional<CommentEntity> findByPostIdAndId(Long postId, Long id);

    /** 댓글 행을 쓰기 잠금으로 읽는다 — 고치기·지우기·숨기기를 줄 세운다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CommentEntity> findWithLockByPostIdAndId(Long postId, Long id);
}
