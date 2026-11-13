package com.community.bizapplication.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 가격 기간표 한 줄(「이 시각부터 이 값」) — shared V208 {@code biz_price_schedules}(docs/features/ad-price-rules-contract.md). 상품 하나에 여러 줄, 같은 상품에 같은 적용 시각은 하나
 * ({@code uq_bps_product_effective}). 값 넷(단가·동시 한도·최대 일수·시작일 선택 범위)을 통째로 가진다. 적용 시각이 지나면 읽는 쪽이 이 값을 쓰고, 줄은 기록으로 남는다(지우지 않음).
 * 쓰기·읽기는 {@code BizPriceRuleStore} 가 한다(QueryDSL).
 */
@Entity
@Table(name = "biz_price_schedules")
public class BizPriceScheduleEntity {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    private BizProduct product;

    private Instant effectiveAt;
    private Long unitPrice;
    /** 건당(POST) 상품이면 null */
    private Integer capacity;
    private Integer maxDays;
    private Integer maxLeadDays;
    private Long createdBy;
    private Instant createdAt;
    private Long updatedBy;
    private Instant updatedAt;

    protected BizPriceScheduleEntity() {
    }

    public static BizPriceScheduleEntity create(long id, BizProduct product, Instant effectiveAt, long unitPrice, Integer capacity, int maxDays, int maxLeadDays,
                                                long createdBy, Instant createdAt) {
        BizPriceScheduleEntity row = new BizPriceScheduleEntity();
        row.id = id;
        row.product = product;
        row.effectiveAt = effectiveAt;
        row.unitPrice = unitPrice;
        row.capacity = capacity;
        row.maxDays = maxDays;
        row.maxLeadDays = maxLeadDays;
        row.createdBy = createdBy;
        row.createdAt = createdAt;
        return row;
    }
}
