package com.community.group.dto.response;

/**
 * 소모임 한 줄 — 목록·검색(community {@code GroupRow}).
 *
 * @param summary      소개를 한 줄로(공백을 접고 60자에서 …)
 * @param category     분류(게시판이 어느 분류에도 없으면 null)
 * @param board        소모임 게시판 {@code {slug, name, nameEn}}
 * @param joinType     OPEN · APPROVAL
 * @param recruitState OPEN(모집 중) · FULL(정원 참) · CLOSED(모임장이 마감)
 * @param stockNotice  소모임 게시판이 투자 고지 대상(화면이 「종목 추천 금지」를 붙인다)
 * @param visibility   PUBLIC · PRIVATE · null(공개 범위 기능이 꺼져 있고 비공개 소모임 — 화면은 칩을 그리지 않는다)
 * @param myState      NONE · MEMBER · LEADER · APPLIED · REJECTED · KICKED — 비로그인 null
 */
public record GroupRowView(String id, String name, String summary, GroupCategoryRefView category, GroupBoardRefView board, String joinType, int capacity,
                           int memberCount, boolean recruiting, String recruitState, boolean offline, boolean stockNotice, String visibility, String myState) {
}
