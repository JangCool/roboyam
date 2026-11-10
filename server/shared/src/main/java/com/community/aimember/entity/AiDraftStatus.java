package com.community.aimember.entity;

/**
 * 생성물 상태 — {@code PENDING}(검수 대기) → {@code APPROVED}(게시 승인, 실제 등록은 api 서버 실행기가) → {@code PUBLISHED} | {@code FAILED}, 또는 {@code DISCARDED}(버림).
 * 바로 게시면 처음부터 {@code APPROVED}. 생성 호출이 실패하면 처음부터 {@code FAILED}.
 */
public enum AiDraftStatus {
    PENDING, APPROVED, PUBLISHED, DISCARDED, FAILED;

    /** 아직 살아 있는(댓글 수·주고받기 차례에 드는) 상태 */
    public boolean counts() {
        return this == PENDING || this == APPROVED || this == PUBLISHED;
    }
}
