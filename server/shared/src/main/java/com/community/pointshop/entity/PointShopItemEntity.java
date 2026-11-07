package com.community.pointshop.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 포인트 상점 품목 — {@code point_shop_items}(V86). 만들기는 {@link #create}(저장소 {@code PointShopItemStore} 가 넣고 바로 내보낸다). 고치기·재고 차감·묶음 가격은 저장소의
 * UPDATE(QueryDSL — 재고는 {@code stock = stock - 1 WHERE stock > 0} 한 문장으로 한 명만 이긴다)가 한다 — 칸을 엔티티로 고치지 않는다. 읽기는 저장소가 한 줄 모양으로 꺼낸다.
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

    /** 새 품목 — 값 검사는 부르는 서비스가 끝냈다. 만든·고친 운영자·시각은 같다 */
    public static PointShopItemEntity create(long id, String code, PointShopItemKind kind, PointShopUsageType usageType, String name, String nameEn, String description,
                                             String descriptionEn, String color, String borderStyle, String avatarId, int pricePoint, Integer durationDays, Integer stock,
                                             Instant saleStartAt, Instant saleEndAt, boolean active, int sortOrder, long operatorId, Instant now) {
        PointShopItemEntity item = new PointShopItemEntity();
        item.id = id;
        item.code = code;
        item.kind = kind;
        item.usageType = usageType;
        item.name = name;
        item.nameEn = nameEn;
        item.description = description;
        item.descriptionEn = descriptionEn;
        item.color = color;
        item.borderStyle = borderStyle;
        item.avatarId = avatarId;
        item.pricePoint = pricePoint;
        item.durationDays = durationDays;
        item.stock = stock;
        item.saleStartAt = saleStartAt;
        item.saleEndAt = saleEndAt;
        item.active = active;
        item.sortOrder = sortOrder;
        item.createdBy = operatorId;
        item.updatedBy = operatorId;
        item.createdAt = now;
        item.updatedAt = now;
        return item;
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
