package com.community.counsel.repository.master;

import com.community.counsel.entity.CounselPostEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** AI 연애상담소 사연 줄({@code counsel_posts}, V274) — 목록·셈은 {@link CounselQueries} */
public interface CounselPostRepository extends IrobitRepository<CounselPostEntity, Long> {

    /** 조언 고르기 · 실행기의 판정 — 겹치지 않게 줄을 잠근다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CounselPostEntity> findWithLockByPostId(long postId);
}
