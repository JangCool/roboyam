package com.community.post.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * 처리 상태 이력 한 줄 — 공개 {@code GET /api/posts/{id}/process-status/history} 와 관리자 {@code GET /api/admin/posts/{id}/process-status/history} 가 같은 모양
 * (관리자는 {@code actorName} 을 채운다).
 *
 * @param actor     바꾼 쪽 OPERATOR(운영자 — 관리자 앱·운영자 회원 계정) · BOARD_STAFF(그 게시판 매니저·스텝) · GROUP_STAFF(소모임장·매니저)
 * @param actorName 관리자 응답만 — 운영자 이름 또는 회원 닉네임(탈퇴·없으면 null — 그때는 칸이 나가지 않는다). 공개 응답에는 칸이 없다
 */
public record PostProcessStatusHistoryItemView(String fromStatus, String toStatus, String note, Instant changedAt, String actor,
                                               @JsonInclude(JsonInclude.Include.NON_NULL) String actorName) {

    /** 공개 응답(이름 없음) */
    public static PostProcessStatusHistoryItemView of(com.community.post.entity.PostProcessStatusHistoryEntity row) {
        return new PostProcessStatusHistoryItemView(row.getFromStatus(), row.getToStatus(), row.getNote(), row.getChangedAt(), row.getActorKind(), null);
    }
}
