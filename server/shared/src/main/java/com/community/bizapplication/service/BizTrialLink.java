package com.community.bizapplication.service;

import java.util.Optional;

/**
 * 광고 신청 건(kind TRIAL) ↔ 체험단 모집을 잇는 고리(trial-group-contract 「유료 결제 연결」 — 점검 결정 D1·D7). 구현은 체험단 쪽({@code com.community.trial.service.TrialPayments})이고
 * 광고 신청 쪽은 이 인터페이스만 본다(패키지 순환을 끊는다 — 핫딜 게시 고리 {@link BizHotdealPosting} 과 같은 방식). 모두 <b>부르는 쪽 트랜잭션 안</b>에서 돈다.
 *
 * <p>잠금 차례: 결제 행 → 신청 건 행 → 모집 행. 광고 쪽 처리는 신청 건 행을 잠근 뒤 이 고리를 부르고, 체험단 쪽 처리도 신청 건 행을 먼저 잠근 다음 모집 행을 잠근다.
 */
public interface BizTrialLink {

    /** 신청 건 상세에 싣는 모집 요약 — 단위 PER_RECRUIT·PER_PERSON · 보내는 쪽 ADVERTISER·OPERATOR */
    record TrialInfo(long trialId, String title, String status, String productName, String form, String shipper, Integer selectedCount, String priceUnit,
                     long unitPrice, long shippingFeePerPerson, Long amount) {
    }

    /** 사업자 정지·탈퇴 영향 — 모집 전 단계(준비·모집 중·뽑는 중 — 정지·탈퇴면 취소) · 진행 중(그대로 진행) */
    record TrialCounts(int recruiting, int running) {
        public static final TrialCounts ZERO = new TrialCounts(0, 0);
    }

    /** 그 신청 건의 모집 요약(체험단 건이 아니면 비어 있다) */
    Optional<TrialInfo> info(long bizApplicationId);

    /** 발송이 하나라도 있었나(송장·받음·사용기 연결) — 결제 뒤 취소를 자동 전액 환불로 할 수 있는지 */
    boolean shippingStarted(long bizApplicationId);

    /**
     * 광고 쪽에서 신청 건이 닫혔다(결제 기한 만료·반려·운영자 취소·환불 승인) — 모집이 아직 열려 있으면 취소한다(신청자·뽑힌 회원 알림 · 주소 파기). 발송이 있었으면
     * 409 {@code BIZ_APPLICATION_STATE_CHANGED}(부르는 쪽 처리 전체가 되돌아간다). 신청 건은 부르는 쪽이 이미 닫았다.
     */
    void bizClosed(long bizApplicationId, String reason, Long operatorId);

    /** 사업자 신원의 체험단 수(광고주가 그 신원에 이어진 모집) */
    TrialCounts counts(long businessProfileId);

    /** 사업자 정지·탈퇴 — 모집 전 단계 모집은 취소(신청 건도 취소), 진행 중은 그대로 진행 */
    void businessEnded(long businessProfileId, String reason, Long operatorId);

    /** 체험단 모듈이 없을 때(시험 문맥 등) */
    BizTrialLink NONE = new BizTrialLink() {
        @Override
        public Optional<TrialInfo> info(long bizApplicationId) {
            return Optional.empty();
        }

        @Override
        public boolean shippingStarted(long bizApplicationId) {
            return false;
        }

        @Override
        public void bizClosed(long bizApplicationId, String reason, Long operatorId) {
        }

        @Override
        public TrialCounts counts(long businessProfileId) {
            return TrialCounts.ZERO;
        }

        @Override
        public void businessEnded(long businessProfileId, String reason, Long operatorId) {
        }
    };
}
