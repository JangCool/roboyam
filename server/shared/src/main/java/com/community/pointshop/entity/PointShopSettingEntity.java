package com.community.pointshop.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 포인트 상점 설정(행 하나) — {@code point_shop_settings}(V86). <b>읽기 전용 매핑</b>(저장은 {@code PointShopCatalogService#updateSettings} 가 JDBC 로 — 감사와 한 트랜잭션).
 * {@code refundDays}: 셀프 환불 기한 일수(1~30, 기본 {@value #DEFAULT_REFUND_DAYS}) — 이미 산 구매도 <b>지금 값</b>으로 판정한다.
 */
@Entity
@Table(name = "point_shop_settings")
public class PointShopSettingEntity {

    /** 행은 하나 */
    public static final int ROW_ID = 1;
    public static final int DEFAULT_REFUND_DAYS = 7;
    public static final int REFUND_DAYS_MIN = 1;
    public static final int REFUND_DAYS_MAX = 30;

    @Id
    private Integer id;

    private int refundDays;
    private Instant updatedAt;
    private Long updatedBy;

    protected PointShopSettingEntity() {
    }

    public Integer getId() {
        return id;
    }

    public int getRefundDays() {
        return refundDays;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
