package com.community.sanction.dto.response;

import java.time.Instant;

/**
 * 이의제기 목록 한 줄(admin-web {@code AppealItem}) — {@code kind} SITE(사이트 제재 — {@code sanction}) · BOARD(게시판 제재 — {@code boardSanction}, board-sanction-contract 결정 4).
 */
public record AppealItemView(String id, long no, String status, SanctionUserRefView user, String kind, AppealSanctionBriefView sanction,
                             AppealBoardSanctionBriefView boardSanction, Instant createdAt, Instant answeredAt) {
}
