package com.community.report.repository.master;

import com.community.report.entity.ReportCaseEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 신고 사건(V82 {@code report_cases}) — 대상마다 열린 사건은 하나(부분 유일 색인 {@code ux_report_cases_open_target}) */
public interface ReportCaseRepository extends RoboyamRepository<ReportCaseEntity, Long> {

    /** 사건 행을 쓰기 잠금으로 읽는다 — 두 운영자가 동시에 처리해도 한 번만 처리된다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ReportCaseEntity> findWithLockById(Long id);

    /**
     * 대상의 열린 사건을 쓰기 잠금으로 — 신고 접수가 쓴다({@code statuses} = {@link ReportCaseEntity#OPEN}). 운영자가 막 닫은 사건은 잠금이 풀린 뒤 조건을 다시 보므로 걸리지 않는다
     * (그때는 새 사건을 연다).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ReportCaseEntity> findWithLockByTargetTypeAndTargetIdAndStatusIn(String targetType, Long targetId, Collection<String> statuses);

    boolean existsByTargetTypeAndTargetIdAndStatusIn(String targetType, Long targetId, Collection<String> statuses);

    /** 대상의 사건 수(닫힌 것 포함) — 관리자 소모임 상세의 {@code reportCaseCount}. 색인 {@code ix_report_cases_target} */
    long countByTargetTypeAndTargetId(String targetType, Long targetId);
}
