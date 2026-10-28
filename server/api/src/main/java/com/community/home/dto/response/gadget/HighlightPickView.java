package com.community.home.dto.response.gadget;

import com.community.post.dto.response.PostFeedRowView;

/**
 * {@code HIGHLIGHT} 가젯의 한 칸 — user-web {@code HomeHighlight}. 운영자가 고른 글(관리자 하이라이트)이고 노출 중인 것만.
 *
 * @param id       하이라이트 ID
 * @param title    운영자가 적은 노출 제목(없으면 null — 화면이 글 제목)
 * @param summary  운영자가 적은 요약(없으면 null)
 * @param imageUrl 운영자가 올린 대표 이미지 주소(없으면 null — 화면이 글 썸네일)
 */
public record HighlightPickView(String id, PostFeedRowView post, String title, String summary, String imageUrl) {
}
