package com.community.notice.dto.response;

import java.util.List;

/**
 * 공지 컨텍스트의 전체 공지 요약 · 게시판 고르기 목록(V174).
 *
 * @param count       지금 살아 있는 전체 공지 수(지운 · 숨긴 · 기간 지난 것 빼고)
 * @param max         동시에 걸 수 있는 수(3)
 * @param boards      뺄 게시판 고르기 — 켜진 일반 게시판(소모임 게시판 · 공지 게시판 자신 빼고), 정렬 순서대로
 * @param groupBoards 뺄 게시판 고르기 — 켜진 소모임 게시판
 */
public record NoticeGlobalContextView(long count, int max, List<NoticeBoardRefView> boards, List<NoticeBoardRefView> groupBoards) {
}
