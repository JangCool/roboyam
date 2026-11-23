package com.community.sitemap.dto;

import java.time.Instant;

/**
 * 사이트맵 한 줄의 재료(본문은 읽지 않는다).
 *
 * @param editedAt 글쓴이가 고친 시각(고친 적 없으면 null)
 */
public record SitemapPostData(Long id, Instant editedAt, Instant createdAt) {

    /** {@code lastmod} — 고친 시각, 없으면 쓴 시각 */
    public Instant lastModified() {
        return editedAt != null ? editedAt : createdAt;
    }
}
