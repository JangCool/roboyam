package com.community.post.dto.response;

/**
 * 글 상세의 {@code viewer} — 보는 사람이 이 글에 할 수 있는 일(게시판 응답 {@code viewer} 와 같은 이름 관례). 일반 글·소모임 글이 같은 칸이다.
 *
 * @param canChangeStatus 처리 상태를 바꿀 수 있는가(V168) — 상태를 쓰는 게시판이고, 보는 사람이 정지 중이 아닌 운영자 회원 계정 · 그 게시판 매니저·스텝
 *                        · 소모임 글이면 그 소모임의 소모임장·글 관리 매니저. 비회원·그 밖 false
 */
public record PostViewerView(boolean canChangeStatus) {
}
