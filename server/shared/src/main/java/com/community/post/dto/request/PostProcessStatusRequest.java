package com.community.post.dto.request;

/**
 * {@code PUT /api/posts/{id}/process-status}(공개 — 그 게시판 매니저·스텝·운영자 회원 계정) · {@code PUT /api/admin/posts/{id}/process-status}(관리자 — {@code post.manage}) 본문.
 *
 * @param status {@code PostProcessStatus} 이름(RECEIVED · CHECKING · IN_PROGRESS · DONE · ON_HOLD · REJECTED · DUPLICATE, 대소문자 무시) — 비었거나 모르면 400 {@code fields.status}
 */
public record PostProcessStatusRequest(String status) {
}
