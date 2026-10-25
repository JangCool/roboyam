package com.community.popular.dto.request;

/**
 * {@code POST /api/admin/popular-posts/exclusions}.
 *
 * @param postId 뺄 글 ID(문자열)
 * @param reason INAPPROPRIATE · PROMOTION · MANIPULATION · DISPUTE · OTHER
 * @param memo   메모(선택, 100자까지 — OTHER 면 필수). 앞뒤 공백을 떼고 비면 null
 */
public record PopularExcludeRequest(String postId, String reason, String memo) {
}
