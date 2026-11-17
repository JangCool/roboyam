package com.community.title.dto.response;

import com.community.title.dto.response.TitleBadgeView;
import java.time.Instant;
import java.util.List;

/**
 * 칭호 신청 한 줄({@code GET /api/admin/titles/applications?status&key} — 「확인할 일」, admin-web {@code AdminTitleApplication}). {@code status} PENDING·APPROVED·REJECTED.
 *
 * @param conditions 신청자의 조건별 채움 — 대기 중인 신청만 지금 기준으로 센다(처리된 신청은 빈 목록)
 * @param boardName  연결 게시판(게시판 매니저도 심사하는 칭호) — 없으면 null
 * @param reviewedBy 심사한 사람(운영자 이름 · 게시판 매니저 닉네임) — 대기면 null. {@code reviewedByKind} OPERATOR·BOARD_MANAGER
 * @param reapplyAt  거절이면 다시 신청할 수 있는 때
 */
public record AdminTitleApplicationView(String id, TitleBadgeView title, AdminTitleGrantView.MemberView member, String status, List<ConditionView> conditions,
                                        String boardName, String reason, Instant reapplyAt, Instant createdAt, Instant reviewedAt, String reviewedBy, String reviewedByKind) {

    public record ConditionView(String type, String label, boolean met) {
    }
}
