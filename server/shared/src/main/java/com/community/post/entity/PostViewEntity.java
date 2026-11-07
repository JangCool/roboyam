package com.community.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 조회 기록 — V46 {@code post_views}(글 ID 해시 16 조각 파티션 표, 부모 표에 매핑). 회원 {@code m:<id>} · 비회원 {@code g:<IP 의 SHA-256>} 마다 한 줄.
 * 기록은 {@code PostCounterQueries#recordView}({@code ON CONFLICT … DO UPDATE … WHERE} — 처음이거나 중복 방지 시간이 지났을 때만), 정리는 DB 배치(board-post-upkeep).
 */
@Entity
@Table(name = "post_views")
@IdClass(PostViewId.class)
public class PostViewEntity {

    @Id
    private Long postId;

    @Id
    private String viewerKey;

    @Column(insertable = false, updatable = false)
    private Instant viewedAt;

    protected PostViewEntity() {
    }
}
