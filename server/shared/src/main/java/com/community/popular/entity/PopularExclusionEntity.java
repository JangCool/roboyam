package com.community.popular.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 인기글에서 뺀 글 — V74 의 {@code popular_exclusions}(글마다 한 줄, 다시 넣으면 줄을 지운다). 글 표는 파티션 표라 외래 키가 없다.
 * 사유는 {@code PopularRules#EXCLUSION_REASONS}, 기타(OTHER)면 {@code memo} 필수.
 */
@Entity
@Table(name = "popular_exclusions")
public class PopularExclusionEntity {

    @Id
    private Long postId;

    private String reason;
    private String memo;
    private Long excludedBy;
    private Instant excludedAt;

    protected PopularExclusionEntity() {
    }

    public static PopularExclusionEntity create(long postId, String reason, String memo, long operatorId, Instant now) {
        PopularExclusionEntity row = new PopularExclusionEntity();
        row.postId = postId;
        row.reason = reason;
        row.memo = memo;
        row.excludedBy = operatorId;
        row.excludedAt = now;
        return row;
    }

    public Long getPostId() {
        return postId;
    }

    public String getReason() {
        return reason;
    }

    public String getMemo() {
        return memo;
    }

    public Long getExcludedBy() {
        return excludedBy;
    }

    public Instant getExcludedAt() {
        return excludedAt;
    }
}
