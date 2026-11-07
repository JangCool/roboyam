package com.community.bizapplication.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 요금표 한 줄 — shared V112 {@code biz_price_rules}(상품 하나에 한 줄, 시드 값). 편집 API 는 후속 — 지금은 받기 켜기/끄기만 저장소가 바꾼다 */
@Entity
@Table(name = "biz_price_rules")
public class BizPriceRuleEntity {

    @Id
    @Enumerated(EnumType.STRING)
    private BizProduct product;

    /** {@code POST}(건당) · 그 밖은 기간 단위 */
    private String unit;
    private Long unitPrice;
    /** 건당이면 null(한도 없음) */
    private Integer capacity;
    private Integer maxDays;
    private Integer maxLeadDays;
    private Boolean accepting;
    private Instant updatedAt;
    private Long updatedBy;

    protected BizPriceRuleEntity() {
    }
}
