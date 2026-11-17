package com.community.title.repository.master;

import com.community.title.entity.TitleApplicationEntity;
import com.community.title.entity.TitleApplicationStatus;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 칭호 신청({@code title_applications}, V240) */
public interface TitleApplicationRepository extends IrobitRepository<TitleApplicationEntity, Long> {

    /** 심사 — 신청 행을 잠가 두 심사자가 같은 신청을 동시에 처리하지 못하게 한다(이 행을 FK 로 가리키는 표가 없다) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TitleApplicationEntity> findWithLockById(Long id);

    Optional<TitleApplicationEntity> findFirstByUserIdAndTitleIdAndStatus(Long userId, Long titleId, TitleApplicationStatus status);

    /** 그 회원·칭호의 마지막 신청(다시 신청할 수 있는 날) */
    Optional<TitleApplicationEntity> findFirstByUserIdAndTitleIdOrderByCreatedAtDescIdDesc(Long userId, Long titleId);

    /** 내 신청(최신 순) */
    List<TitleApplicationEntity> findTop50ByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
