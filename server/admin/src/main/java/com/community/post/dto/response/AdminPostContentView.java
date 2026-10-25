package com.community.post.dto.response;

import java.time.Instant;

/**
 * 글 본문 — 지운 글도 파기 전까지 보인다.
 *
 * @param format   TEXT · HTML(서버가 정화해 저장한 HTML 그대로)
 * @param body     본문 — 원문을 파기했으면 null
 * @param editedAt 작성자가 고친 시각(없으면 null)
 */
public record AdminPostContentView(String format, String body, Instant editedAt) {
}
