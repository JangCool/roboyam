package com.community.hotdeal.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 딜 칸 수정 이력 한 줄(D3 — 6개월 보존, 정리는 2단계 배치) — V122 {@code post_deal_revisions}. 쌓기만 한다.
 * {@code changes} = {@code [{field, before, after}]}(바뀐 딜 칸만, 값은 글자 — 관리자 서랍 「딜 칸 수정 이력」).
 */
@Entity
@Table(name = "post_deal_revisions")
public class PostDealRevisionEntity {

    public static final String BY_AUTHOR = "AUTHOR";
    public static final String BY_MANAGER = "MANAGER";
    /** 보존 기간(개월) — 관리자 이력 응답 {@code retentionMonths} */
    public static final int RETENTION_MONTHS = 6;

    @Id
    private Long id;

    private Long postId;
    private String actorKind;
    private Long actorId;
    @JdbcTypeCode(SqlTypes.JSON)
    private String changes;
    private boolean hostChanged;
    private boolean priceEdit;
    private Instant createdAt;

    protected PostDealRevisionEntity() {
    }

    /** @param changesJson {@code [{field, before, after}]} JSON(한 칸 이상) */
    public static PostDealRevisionEntity create(long id, long postId, String actorKind, Long actorId, String changesJson, boolean hostChanged, boolean priceEdit,
                                                Instant now) {
        PostDealRevisionEntity r = new PostDealRevisionEntity();
        r.id = id;
        r.postId = postId;
        r.actorKind = actorKind;
        r.actorId = actorId;
        r.changes = changesJson;
        r.hostChanged = hostChanged;
        r.priceEdit = priceEdit;
        r.createdAt = now;
        return r;
    }

    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public String getActorKind() {
        return actorKind;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getChanges() {
        return changes;
    }

    public boolean isHostChanged() {
        return hostChanged;
    }

    public boolean isPriceEdit() {
        return priceEdit;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
