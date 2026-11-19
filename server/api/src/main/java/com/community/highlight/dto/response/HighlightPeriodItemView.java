package com.community.highlight.dto.response;

import com.community.post.dto.response.PostFeedRowView;
import java.time.Instant;

/**
 * 「아추!」 페이지의 한 줄 — user-web {@code AchuItem}(홈 하이라이트 가젯의 {@code HomeHighlight} + 고른 시각).
 *
 * @param id       하이라이트 ID
 * @param post     글 줄(섞인 목록 줄 — 보는 사람의 차단·메모 포함, 홈 하이라이트 가젯과 같은 모양)
 * @param title    운영자가 적은 노출 제목(없으면 null — 화면이 글 제목)
 * @param summary  운영자가 적은 요약(없으면 null)
 * @param imageUrl 운영자가 올린 대표 이미지 주소(없으면 null — 화면이 글 썸네일)
 * @param pickedAt 고른 시각 — 노출 시작, 없으면 만든 시각(ISO-8601 UTC)
 */
public record HighlightPeriodItemView(String id, PostFeedRowView post, String title, String summary, String imageUrl, Instant pickedAt) {
}
