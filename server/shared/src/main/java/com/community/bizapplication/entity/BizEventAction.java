package com.community.bizapplication.entity;

/**
 * 처리 기록 코드(biz_application_events.action) — 공개·관리자 화면이 같은 코드 묶음을 i18n 으로 그린다. 한 번 정하면 바꾸지 않는다.
 * user-web {@code BizHistoryEntry.action} 의 값은 모두 여기에 있다(그 밖은 관리자 화면 전용 코드 — 공개 처리 기록에는 공개용만 내보낸다: {@link #publicVisible()}).
 */
public enum BizEventAction {
    SUBMITTED(true),
    CREATIVE_CHANGED(true),
    APPROVED_ONLINE(true),
    APPROVED_MANUAL(true),
    REJECTED(true),
    CANCELED(true),
    PAID(true),
    EXPIRED(true),
    MANUAL_SWITCH(true),
    CONTRACT_RECEIVED(true),
    CONTRACT_REPLACED(false),
    CONTRACT_VIEWED(false),
    DEPOSIT_CONFIRMED(true),
    DEPOSIT_DEADLINE(true),
    SCHEDULED(true),
    TAKEN_DOWN(true),
    RESTORED(true),
    REFUND_REQUESTED(true),
    REFUND_APPROVED(true),
    REFUND_DENIED(true),
    REFUNDED(true),
    REFUND_TRANSFERRED(true),
    AUTO_CANCELED(true),
    FORFEITED(true),
    // ── 사업자 핫딜 게시(2차 — V123, biz-hotdeal-contract) ──
    /** 시작 시각에 핫딜 게시판에 글을 올렸다 */
    POSTED(true),
    /** 시작 시각에 글을 올리지 못했다 — note = 사유 코드 BOARD_CLOSED·LINK_BLOCKED·BUSINESS_SUSPENDED·OTHER */
    POST_FAILED(true),
    /** 신청 때 고른 말머리가 없거나 꺼져 있어 말머리 없이 올렸다({@code POSTED} 다음) — note = 말머리 상단 노출이 걸려 있으면 {@code CATEGORY_TOP_OFF}(그 노출은 나오지 않음), 아니면 null */
    POSTED_WITHOUT_PREFIX(true),
    /** 취소·환불·내림으로 글을 숨겼다 */
    POST_HIDDEN(true),
    /** 되돌리기로 글이 다시 보인다 */
    POST_RESTORED(true),
    /** 사업자가 딜을 끝냈다(환불 없음 · 신청 건 상태 그대로) */
    DEAL_ENDED_BY_BUSINESS(true),
    /** 종료 요청이 모여 딜이 자동으로 끝났다(환불 없음 · 신청 건 상태 그대로) */
    DEAL_AUTO_ENDED(true),
    /** 사업자 글에 종료 요청이 문턱만큼 모였다 — 자동 종료하지 않고 운영팀 확인 중(결정 11, SYSTEM) */
    DEAL_REVIEW_NEEDED(true),
    /** 운영자가 확인 필요 사업자 글을 그대로 두기로 했다 — note = 사유(결정 11) */
    DEAL_KEPT_BY_OPERATOR(true),
    /** 운영진(운영자·게시판 매니저·스텝)이 사업자 글의 딜을 종료·재개했다 — note = 사유, 환불 없음(결정 12) */
    DEAL_ENDED_BY_OPERATOR(true),
    DEAL_REOPENED_BY_OPERATOR(true),
    // ── 광고주 요청 일시 정지·중단(V127 — biz-pause-contract). note 는 ` · ` 로 이은 토큰: 운영자 대신 = `PHONE|EMAIL|OTHER · 메모`(공개 응답은 경로까지만) ──
    /** 일시 정지 */
    PAUSED(true),
    /** 다시 켜기 — note 맨 앞 {@code MIN:<멈춘 분>} */
    RESUMED(true),
    /** 중단 — 정지 중 전환이면 note 맨 앞 {@code FROM_PAUSE} */
    STOPPED(true),
    // ── 운영자 추가 환불(클레임 — V128). 돈은 기존 REFUNDED(온라인 PG 취소 완료)·REFUND_TRANSFERRED(수동 송금 완료) 줄로 이어진다 ──
    /** 추가 환불(바로 승인) — note = 금액(사유는 관리자 상세의 환불 줄·감사에만) */
    CLAIM_REFUNDED(true);

    private final boolean publicVisible;

    BizEventAction(boolean publicVisible) {
        this.publicVisible = publicVisible;
    }

    /** 사업자 화면의 처리 기록에 보이는가(계약서 교체·열람 같은 운영 기록은 감춘다) */
    public boolean publicVisible() {
        return publicVisible;
    }
}
