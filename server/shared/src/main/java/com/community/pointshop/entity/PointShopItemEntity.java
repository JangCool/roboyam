package com.community.pointshop.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 포인트 상점 품목 — {@code point_shop_items}(V86). <b>읽기 전용 매핑</b>이다(만들기·고치기·재고 차감은 {@code PointShopCatalogService}·{@code PointShopPurchaseService} 가
 * JDBC 저장소 {@code PointShopItemStore} 로 한다 — 재고는 조건부 UPDATE 한 문장, 포인트 사용과 같은 JDBC 트랜잭션). 관리자·회원 화면의 질의(QueryDSL)가 읽을 때 쓴다.
 * 코드·종류·아바타 ID 는 만든 뒤 바뀌지 않는다(트리거).
 */
@Entity
@Table(name = "point_shop_items")
public class PointShopItemEntity {

    @Id
    private Long id;

    private String code;
    @Enumerated(EnumType.STRING)
    private PointShopItemKind kind;
    @Enumerated(EnumType.STRING)
    private PointShopUsageType usageType;
    private String name;
    private String nameEn;
    private String description;
    private String descriptionEn;
    /** 닉네임 색만 — {@code #RRGGBB} */
    private String color;
    /** 프로필 테두리만 — {@link ProfileBorderStyle} 키 */
    private String borderStyle;
    /** 프로필 아바타만 — 카탈로그 ID */
    private String avatarId;
    private int pricePoint;
    private Integer durationDays;
    /** null = 무제한 */
    private Integer stock;
    private Instant saleStartAt;
    private Instant saleEndAt;
    private boolean active;
    private int sortOrder;
    private Long createdBy;
    private Long updatedBy;
    private Instant createdAt;
    private Instant updatedAt;

    protected PointShopItemEntity() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public PointShopItemKind getKind() {
        return kind;
    }

    public PointShopUsageType getUsageType() {
        return usageType;
    }

    public String getName() {
        return name;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getDescription() {
        return description;
    }

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public String getColor() {
        return color;
    }

    public String getBorderStyle() {
        return borderStyle;
    }

    public String getAvatarId() {
        return avatarId;
    }

    public int getPricePoint() {
        return pricePoint;
    }

    public Integer getDurationDays() {
        return durationDays;
    }

    public Integer getStock() {
        return stock;
    }

    public Instant getSaleStartAt() {
        return saleStartAt;
    }

    public Instant getSaleEndAt() {
        return saleEndAt;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
