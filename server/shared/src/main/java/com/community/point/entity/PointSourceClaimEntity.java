package com.community.point.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 정보 등급 「같은 출처 링크는 한 번만 적립」의 열쇠 한 줄 — {@code point_source_claims}(V238, point-board-tier-contract). 글 적립을 받은 출처 링크 열쇠(같은 딜 키 규칙의 SHA-256)와
 * 그 글·회원. 사이트 전체에서 열쇠마다 하나. 넣기는 글쓰기 세이브포인트 안이라 JDBC({@code PointLedgerWriter#claimSources} — {@code ON CONFLICT DO NOTHING})이고 고치지 않는다.
 */
@Entity
@Table(name = "point_source_claims")
public class PointSourceClaimEntity {

    @Id
    private String sourceKey;

    private Long postId;
    private Long userId;
    private Instant createdAt;

    protected PointSourceClaimEntity() {
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
