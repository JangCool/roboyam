package com.community.bizapplication.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 요금표 변경 이력 한 줄 — shared V208 {@code biz_price_rule_history}(광고 노출 자리 설정 이력 {@code AdPlacementHistoryEntity} 와 같은 짜임). 고치거나 지우지 않는다.
 * 관리자 서버가 운영자가 한 일(예약 만들기·고치기·취소 · 받기)만 넣는다 — 예약이 적용되는 순간은 기간표({@code biz_price_schedules}) 자체로 보인다.
 * 바뀐 칸은 JSON 배열 글자({@code changes}, {@code [{field, before, after}]}).
 */
@Entity
@Table(name = "biz_price_rule_history")
public class BizPriceRuleHistoryEntity {

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    /** 관리자 화면({@code /ads/placements?tab=prices}) — 유일한 출처 */
    public static final String SOURCE_ADMIN = "ADMIN";

    @Id
    private Long id;

    private String action;
    private String source;
    /** 바꾼 운영자. 운영자가 지워져도 값은 남는다(FK 없음) */
    private Long operatorId;
    /** 지금 값이 바로 바뀐 상품(받기 켬/끔) — 예약 줄은 null */
    @Enumerated(EnumType.STRING)
    private BizProduct product;
    private Instant changedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private String changes;

    protected BizPriceRuleHistoryEntity() {
    }

    public static BizPriceRuleHistoryEntity admin(long id, String action, long operatorId, BizProduct product, Instant changedAt, String changesJson) {
        BizPriceRuleHistoryEntity row = new BizPriceRuleHistoryEntity();
        row.id = id;
        row.action = action;
        row.source = SOURCE_ADMIN;
        row.operatorId = operatorId;
        row.product = product;
        row.changedAt = changedAt;
        row.changes = changesJson;
        return row;
    }

    public Long getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public String getSource() {
        return source;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public BizProduct getProduct() {
        return product;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public String getChanges() {
        return changes;
    }
}
