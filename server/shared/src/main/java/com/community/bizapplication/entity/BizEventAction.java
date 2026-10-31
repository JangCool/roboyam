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
    FORFEITED(true);

    private final boolean publicVisible;

    BizEventAction(boolean publicVisible) {
        this.publicVisible = publicVisible;
    }

    /** 사업자 화면의 처리 기록에 보이는가(계약서 교체·열람 같은 운영 기록은 감춘다) */
    public boolean publicVisible() {
        return publicVisible;
    }
}
