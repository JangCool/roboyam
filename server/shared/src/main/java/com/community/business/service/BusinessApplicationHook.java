package com.community.business.service;

import com.community.business.dto.BusinessSuspendPlan;
import com.community.business.entity.BusinessServiceKind;

/**
 * 사업자 신원 → 신청 건(광고·핫딜) 쪽으로 넘기는 자리. <b>신청 건 표가 아직 없어</b> 이 세션은 인터페이스만 두고, 빈이 없으면 {@link #NONE}(건수 0 · 처리 없음)을 쓴다.
 * 신청 건 백엔드가 이 인터페이스를 구현한 빈 하나를 등록하면 신원 쪽 코드는 그대로 그 빈을 부른다(docs/features/biz-application-contract.md).
 *
 * <p>정지·해제·탈퇴 훅은 <b>신원 처리 트랜잭션 안에서</b> 불린다 — 실패하면 신원 처리도 되돌아간다(진행 건 처리 없이 정지만 되는 일이 없게).
 */
public interface BusinessApplicationHook {

    /** 정지하면 영향받는 건수(H2) */
    record SuspendImpact(int awaitingPayment, int scheduled, int running) {
        public static final SuspendImpact ZERO = new SuspendImpact(0, 0, 0);
    }

    /** 서비스 카드의 건수 — 진행·예약 중 · 심사·결제 대기 */
    record ServiceCounts(int liveCount, int pendingCount) {
        public static final ServiceCounts ZERO = new ServiceCounts(0, 0);
    }

    /** 탈퇴 판정(F8) — 진행 중 건이 있으면 막는다(「남은 기간 포기」 동의로 풀 수 있음) */
    record WithdrawalCheck(boolean blocked, int runningCount) {
        public static final WithdrawalCheck FREE = new WithdrawalCheck(false, 0);
    }

    SuspendImpact impact(long profileId);

    ServiceCounts counts(long profileId, BusinessServiceKind service);

    /** 정지 — 고른 처리대로 결제·입금 대기 취소 · 시작 전 환불 · 진행 중 내림(+일할 환불 심사 대기열) */
    void onSuspended(long profileId, BusinessSuspendPlan plan, long operatorId);

    /** 정지 해제 — 정지로 내려간 진행 중 건을 연장 후 되돌리기 또는 환불 유지 */
    void onUnsuspended(long profileId, BusinessSuspendPlan.Restore restore, long operatorId);

    /** 탈퇴할 수 있는가(F8 — 결제 전·시작 전 건은 탈퇴 때 자동 정리, 진행 중만 막는다) */
    WithdrawalCheck withdrawalCheck(long profileId);

    /** 탈퇴 — 결제 전·시작 전 건 자동 취소·환불, {@code forfeitRunning} 이면 진행 중 건도 남은 기간 포기 */
    void onWithdrawn(long profileId, boolean forfeitRunning);

    /** 신청 건 백엔드가 없을 때 — 건수 0, 처리 없음 */
    BusinessApplicationHook NONE = new BusinessApplicationHook() {
        @Override
        public SuspendImpact impact(long profileId) {
            return SuspendImpact.ZERO;
        }

        @Override
        public ServiceCounts counts(long profileId, BusinessServiceKind service) {
            return ServiceCounts.ZERO;
        }

        @Override
        public void onSuspended(long profileId, BusinessSuspendPlan plan, long operatorId) {
        }

        @Override
        public void onUnsuspended(long profileId, BusinessSuspendPlan.Restore restore, long operatorId) {
        }

        @Override
        public WithdrawalCheck withdrawalCheck(long profileId) {
            return WithdrawalCheck.FREE;
        }

        @Override
        public void onWithdrawn(long profileId, boolean forfeitRunning) {
        }
    };
}
