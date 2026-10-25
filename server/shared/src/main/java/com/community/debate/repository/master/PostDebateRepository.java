package com.community.debate.repository.master;

import com.community.debate.entity.PostDebateEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;

/** 글의 찬반 토론(V79 {@code post_debates}) — 행이 있으면 열림. 쓰기는 {@link com.community.debate.service.PostDebateWriter} 를 거친다 */
public interface PostDebateRepository extends RoboyamRepository<PostDebateEntity, Long> {

    /** 그 글의 토론 — 쓰기 잠금(설정 바꾸기·닫기가 줄을 선다) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PostDebateEntity> findWithLockByPostId(Long postId);

    /** 열린 토론 — 최근 연 순(같으면 글 ID 큰 것 먼저) */
    Page<PostDebateEntity> findAllByOrderByCreatedAtDescPostIdDesc(Pageable pageable);

    /** 열린 토론 전부 — 최근 연 순(관리자 목록의 검색·게시판 거르기) */
    List<PostDebateEntity> findAllByOrderByCreatedAtDescPostIdDesc();
}
