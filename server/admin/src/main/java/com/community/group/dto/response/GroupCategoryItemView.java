package com.community.group.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 분류 하나(admin-web {@code GroupCategoryItem}, community {@code AdminCategory}).
 *
 * @param sortOrder        저장한 순서 값(10 단위, 작을수록 앞)
 * @param groupCount       이 분류 게시판들을 쓰는 소모임 수(삭제·반려 제외) — 소모임 서버 단계 전까지 늘 0
 * @param investmentNotice 할당한 게시판 중 하나라도 투자 고지
 * @param boards           할당한 순서
 * @param updatedAt        서버가 이 분류를 마지막으로 고친 시각 — 저장의 「다른 관리자가 먼저 고쳤어요」 비교 기준
 */
public record GroupCategoryItemView(String id, String name, int sortOrder, int boardCount, long groupCount, boolean investmentNotice,
                                    List<GroupCategoryBoardView> boards, Instant updatedAt) {
}
