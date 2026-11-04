package com.community.sanction.dto.response;

import java.time.Instant;

/**
 * 내가 받은 게시판 제재 하나(user-web {@code MyBoardSanction}, docs/features/board-sanction-contract.md). <b>처리자 신원은 없다</b>(결정 7).
 *
 * @param endsAt         쓰기 제한만 — 경고는 null
 * @param acknowledged   경고의 「확인했어요」 — 쓰기 제한은 늘 true
 * @param appealDeadline 이의제기 마감(시작 + 30일) — 무효면 null
 */
public record MyBoardSanctionView(String id, long no, MyBoardRefView board, String kind, String status, String reasonCode, String reasonText, Instant startsAt,
                                  Instant endsAt, Instant createdAt, boolean acknowledged, MyAppealView appeal, Instant appealDeadline) {
}
