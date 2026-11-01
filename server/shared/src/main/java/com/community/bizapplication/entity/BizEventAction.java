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
    DEAL_AUTO_ENDED(true);

    private final boolean publicVisible;

    BizEventAction(boolean publicVisible) {
        this.publicVisible = publicVisible;
    }

    /** 사업자 화면의 처리 기록에 보이는가(계약서 교체·열람 같은 운영 기록은 감춘다) */
    public boolean publicVisible() {
        return publicVisible;
    }
}
