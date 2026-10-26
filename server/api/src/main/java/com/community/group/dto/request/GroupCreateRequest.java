package com.community.group.dto.request;

/**
 * {@code POST /api/groups} — 개설 신청. 검사 순서가 계약이라 어노테이션 없이 받고 서비스가 칸별로 모은다({@code fields.<칸>}).
 *
 * @param categoryId   분류 ID({@code GET /api/groups/categories})
 * @param boardSlug    소모임 게시판 주소 — 그 분류에 켜진 소모임 게시판이 하나면 생략할 수 있다
 * @param joinType     OPEN · APPROVAL
 * @param capacity     정원(운영 한도의 정원 범위)
 * @param visibility   PUBLIC · PRIVATE(생략 PRIVATE) — 공개 범위 기능이 꺼져 있으면 무시
 * @param agreeRules   운영 원칙 동의(true)
 * @param agreeStock   투자 고지 게시판이면 true
 * @param agreeOffline 오프라인 모임이면 true
 */
public record GroupCreateRequest(String name, String description, String categoryId, String boardSlug, String joinType, Integer capacity, Boolean offline,
                                 String visibility, Boolean agreeRules, Boolean agreeStock, Boolean agreeOffline) {
}
