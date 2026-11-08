package com.community.post.dto.request;

/**
 * {@code PUT /api/posts/{id}/process-status}(공개 — 그 게시판 매니저·스텝·운영자 회원 계정, 소모임 글이면 그 소모임의 소모임장·매니저) ·
 * {@code PUT /api/admin/posts/{id}/process-status}(관리자 — {@code post.manage}) 본문.
 *
 * @param status {@code PostProcessStatus} 이름(RECEIVED · CHECKING · IN_PROGRESS · DONE · ON_HOLD · REJECTED · DUPLICATE, 대소문자 무시) — 비었거나 모르면 400 {@code fields.status}
 * @param note   선택 메모(V168) — 앞뒤 공백을 떼고 비면 없음, 1~200자, 줄 바꿈·제어 문자 불가(400 {@code fields.note}). 상태 이력·글쓴이 알림·감사에 남는다
 */
public record PostProcessStatusRequest(String status, String note) {

    /** 메모 없이 */
    public PostProcessStatusRequest(String status) {
        this(status, null);
    }
}
