package com.community.title.dto.response;

import com.community.title.dto.response.TitleBadgeView;
import java.time.Instant;

/**
 * 받은 줄 하나(주고 뺀 기록·수여 결과·회원 칭호·이달의 작가 「이미 받은 회원」 — admin-web {@code TitleGrantRow}).
 *
 * @param grantId     받은 줄 ID(회수 {@code DELETE …/grants/{grantId}})
 * @param title       칭호 표시(이달의 작가 {@code label} 은 「11월의 작가」, 단계 칭호는 {@code tier})
 * @param period      기간({@code YYYY-MM}·{@code YYYY}) — 기간 없는 칭호는 null
 * @param source      AUTO·AWARD·APPLICATION
 * @param grantedBy   준 사람 이름 — 운영자 이름, 게시판 매니저 승인이면 그 회원 닉네임, 자동이면 null
 * @param grantedByKind OPERATOR·BOARD_MANAGER — 자동이면 null
 * @param revokedBy   회수한 운영자 이름(회수 안 됐으면 null)
 * @param active      회수되지 않았나
 * @param honorary    이 수여(이달의 작가)로 명예 작가가 따라왔으면 그 줄 — 그 밖 null
 */
public record AdminTitleGrantView(String grantId, TitleBadgeView title, MemberView member, String period, String source, Instant grantedAt, String grantedBy,
                                  String grantedByKind, String reason, Instant revokedAt, String revokedBy, String revokeReason, boolean active, AdminTitleGrantView honorary) {

    /** 회원 — {@code memberNo} 는 회원 관리의 회원번호(회원 ID 와 같다) */
    public record MemberView(String id, String nickname, String memberNo) {
    }
}
