package com.community.post.repository.master;

import com.community.post.entity.PostEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

public interface PostRepository extends RoboyamRepository<PostEntity, Long> {

    /** 글 행을 쓰기 잠금으로 읽는다 — 고치기·지우기·숨기기를 줄 세운다. 트랜잭션 안에서만 의미가 있다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PostEntity> findWithLockById(Long id);
}
