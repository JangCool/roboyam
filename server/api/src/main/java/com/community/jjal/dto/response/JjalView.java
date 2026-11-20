package com.community.jjal.dto.response;

/**
 * 글·댓글 속 짤 하나의 표시 정보(user-web {@code JjalView}).
 *
 * @param url         그림 주소 — 내려간 짤이면 null(「내려간 짤」 자리)
 * @param blocked     신고로 내려간 짤
 * @param collectable 「내 짤에 담기」를 보일까 — 내려간 짤 false, 맥락 글({@code postId})을 주면 19금 게시판·19금 글이거나 그 글에 쓰이지 않은 짤도 false
 */
public record JjalView(String id, String url, int width, int height, boolean animated, boolean blocked, boolean collectable) {
}
