package com.community.post.dto.response;

/**
 * 게시글 관리의 소모임 글 표시(소모임 2단계) — 일반 글이면 줄의 {@code group} 이 null.
 *
 * @param name       소모임 이름(소모임 행이 없으면 null)
 * @param visibility PRIVATE(멤버만 — 줄의 {@code restricted} 가 참, 제목·본문을 싣지 않는다) · PUBLIC(공개로 전환됨)
 * @param dissolved  해산한 소모임의 글
 * @param left       작성자가 소모임을 나가 가려진 글(다시 가입하면 되살아난다)
 */
public record AdminPostGroupView(String id, String name, String visibility, boolean dissolved, boolean left) {
}
