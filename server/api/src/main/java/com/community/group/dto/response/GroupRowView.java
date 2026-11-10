package com.community.group.dto.response;

/**
 * 소모임 한 줄 — 목록·검색(community {@code GroupRow}).
 *
 * @param imageUrl     대표 이미지 공개 주소(V154) — 없으면 null(화면은 이름 첫 글자)
 * @param summary      소개를 한 줄로(공백을 접고 60자에서 …)
 * @param category     분류(게시판이 어느 분류에도 없으면 null)
 * @param board        소모임 게시판 {@code {slug, name, nameEn}}
 * @param joinType     OPEN · APPROVAL
 * @param recruitState OPEN(모집 중) · FULL(정원 참) · CLOSED(모임장이 마감)
 * @param stockNotice  소모임 게시판이 투자 고지 대상(화면이 「종목 추천 금지」를 붙인다)
 * @param visibility   PUBLIC · PRIVATE · null(공개 범위 기능이 꺼져 있고 비공개 소모임 — 화면은 칩을 그리지 않는다)
 * @param myState      NONE · MEMBER · LEADER · APPLIED · REJECTED · KICKED — 비로그인 null
 * @param aiOnly       AI 전용 소모임(V183 — ai-members-contract 정책 7): AI 회원만 가입하고 일반 회원은 구경만 한다(가입 API 도 거절)
 */
public record GroupRowView(String id, String name, String imageUrl, String summary, GroupCategoryRefView category, GroupBoardRefView board, String joinType, int capacity,
                           int memberCount, boolean recruiting, String recruitState, boolean offline, boolean stockNotice, String visibility, String myState,
                           boolean aiOnly) {

    /** 같은 줄에 내 상태만 바꾼 것 — 공용 캐시 줄(보는 사람 없이 만든 것)에 회원 기준으로 덧입힐 때 */
    public GroupRowView withMyState(String value) {
        return new GroupRowView(id, name, imageUrl, summary, category, board, joinType, capacity, memberCount, recruiting, recruitState, offline, stockNotice, visibility,
                value, aiOnly);
    }
}
