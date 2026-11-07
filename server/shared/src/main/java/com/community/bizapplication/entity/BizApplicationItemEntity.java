package com.community.bizapplication.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 신청 건의 자리·금액 줄 — shared V112 {@code biz_application_items}(건 · 상품마다 하나, ux_biz_items). 승인 때 통째로 바꾼다(저장소가 지우고 다시 넣는다) */
@Entity
@Table(name = "biz_application_items")
public class BizApplicationItemEntity {

    @Id
    private Long id;

    private Long applicationId;
    @Enumerated(EnumType.STRING)
    private BizProduct product;
    private Long unitPrice;
    private Integer units;
    private Long supply;
    private Instant startAt;
    private Instant endAt;
    private Instant createdAt;

    protected BizApplicationItemEntity() {
    }

    public static BizApplicationItemEntity create(long id, long applicationId, BizProduct product, long unitPrice, int units, long supply, Instant startAt,
                                                  Instant endAt, Instant now) {
        BizApplicationItemEntity i = new BizApplicationItemEntity();
        i.id = id;
        i.applicationId = applicationId;
        i.product = product;
        i.unitPrice = unitPrice;
        i.units = units;
        i.supply = supply;
        i.startAt = startAt;
        i.endAt = endAt;
        i.createdAt = now;
        return i;
    }
}
