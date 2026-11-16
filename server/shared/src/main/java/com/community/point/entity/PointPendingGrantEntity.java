package com.community.point.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 포인트 보류 지급 한 줄 — {@code point_pending_grants}(V238, point-board-tier-contract 「보류 확정 방식」). 추천 문턱 보너스({@code LIKE_BONUS})·우수 글 선정({@code POST_FEATURED})을
 * 게시판 등급의 보류 시간 뒤에 확정한다. 금액은 보류를 시작한 순간의 정책값으로 고정.
 *
 * <p>넣기·취소는 업무 트랜잭션의 세이브포인트 안이라 JDBC({@code PointLedgerWriter#insertPending}·{@code #cancelPending})이고, 확정은 배치가 이 엔티티를 잠가
 * ({@code PointPendingGrantStore#lockDue} — {@code SKIP LOCKED}) {@link #release}·{@link #cancel} 로 바꾼다. 상태 PENDING → RELEASED | CANCELLED(까닭 남김), 되돌리지 않는다.
 */
@Entity
@Table(name = "point_pending_grants")
public class PointPendingGrantEntity {

    public static final String PENDING = "PENDING";
    public static final String RELEASED = "RELEASED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    private Long id;

    private Long userId;
    private String reason;
    private String refType;
    private Long refId;
    private int amount;
    private Instant releaseAt;
    private String status;
    private String cancelReason;
    private Instant createdAt;
    private Instant settledAt;

    protected PointPendingGrantEntity() {
    }

    /** 확정 — 지급했다(대기 중일 때만 부른다) */
    public void release(Instant now) {
        this.status = RELEASED;
        this.settledAt = now;
    }

    /** 취소 — 주지 않았다(까닭 코드) */
    public void cancel(String reason, Instant now) {
        this.status = CANCELLED;
        this.cancelReason = reason;
        this.settledAt = now;
    }

    public boolean isPending() {
        return PENDING.equals(status);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getReason() {
        return reason;
    }

    public String getRefType() {
        return refType;
    }

    public Long getRefId() {
        return refId;
    }

    public int getAmount() {
        return amount;
    }

    public Instant getReleaseAt() {
        return releaseAt;
    }

    public String getStatus() {
        return status;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }
}
