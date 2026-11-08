package com.community.notice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 전체 공지 — shared V174 {@code notice_global_pins}(공지 글 하나에 한 줄, post_id PK, 외래 키 없음 — 글 표가 파티션이다). docs/features/notice-admin-contract.md 「전체 공지」.
 *
 * <ul>
 *   <li>{@code pinnedAt} = 걸 때의 글 고정 시각 사본. 공개 쪽은 글의 고정 시각이 이 값과 같을 때만 보인다(공지 관리 밖에서 고정이 풀리거나 다시 고정되면 저절로 힘을 잃는다).</li>
 *   <li>{@code expiresAt} 이 지나면 다른 게시판에서만 내려간다(읽을 때 거른다) — 공지 게시판의 고정은 그대로.</li>
 * </ul>
 */
@Entity
@Table(name = "notice_global_pins")
public class NoticeGlobalPinEntity {

    /** 사이트 전체에서 동시에 걸 수 있는 전체 공지 수 */
    public static final int MAX = 3;

    @Id
    private Long postId;
    private Instant pinnedAt;
    @JdbcTypeCode(SqlTypes.ARRAY)
    private Long[] excludedBoardIds = new Long[0];
    private boolean excludeGroupBoards;
    private Instant expiresAt;
    private Instant createdAt;
    private Long createdBy;
    private Instant updatedAt;
    private Long updatedBy;

    protected NoticeGlobalPinEntity() {
    }

    public static NoticeGlobalPinEntity create(long postId, Instant pinnedAt, Collection<Long> excludedBoardIds, boolean excludeGroupBoards, Instant expiresAt,
                                               long operatorId, Instant now) {
        NoticeGlobalPinEntity e = new NoticeGlobalPinEntity();
        e.postId = postId;
        e.createdAt = now;
        e.createdBy = operatorId;
        e.change(pinnedAt, excludedBoardIds, excludeGroupBoards, expiresAt, operatorId, now);
        return e;
    }

    /** 범위 · 기간을 바꾼다(글을 다시 고정했으면 새 고정 시각) */
    public void change(Instant pinnedAt, Collection<Long> excludedBoardIds, boolean excludeGroupBoards, Instant expiresAt, long operatorId, Instant now) {
        this.pinnedAt = pinnedAt;
        this.excludedBoardIds = excludedBoardIds.toArray(Long[]::new);
        this.excludeGroupBoards = excludeGroupBoards;
        this.expiresAt = expiresAt;
        this.updatedAt = now;
        this.updatedBy = operatorId;
    }

    public Long getPostId() {
        return postId;
    }

    public Instant getPinnedAt() {
        return pinnedAt;
    }

    public List<Long> getExcludedBoardIds() {
        return excludedBoardIds == null ? List.of() : Arrays.asList(excludedBoardIds);
    }

    public boolean isExcludeGroupBoards() {
        return excludeGroupBoards;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** 기간이 지났는가(끝 없음이면 false) */
    public boolean expiredAt(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
