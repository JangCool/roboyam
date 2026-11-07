package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 공구 단품(MAIN·ADDON, 수량 열) — shared V101.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_skus")
public class GroupBuySkuEntity {

    @Id
    private Long id;
    private Long groupBuyId;
    @Enumerated(EnumType.STRING)
    private SkuKind kind;
    private Long productSkuId;
    @JdbcTypeCode(SqlTypes.ARRAY)
    private Long[] valueIds;
    private String name;
    private Long extraPrice;
    private Long addonPrice;
    private Integer maxQty;
    private Integer initialMaxQty;
    private Integer currentQty;
    private Integer reservedQty;
    private Boolean onSale;
    private Long supplyPrice;
    private Integer sortOrder;

    protected GroupBuySkuEntity() {
    }

    /** 새 공구 단품(MAIN 은 추가금, ADDON 은 자기 가격) — 수량 열은 DB 기본값(0) */
    public static GroupBuySkuEntity create(long id, long groupBuyId, SkuKind kind, long productSkuId, Long[] valueIds, String name, long extraPrice, Long addonPrice,
                                           Integer maxQty, boolean onSale, Long supplyPrice, int sortOrder) {
        GroupBuySkuEntity s = new GroupBuySkuEntity();
        s.id = id;
        s.groupBuyId = groupBuyId;
        s.kind = kind;
        s.productSkuId = productSkuId;
        s.valueIds = valueIds;
        s.name = name;
        s.extraPrice = extraPrice;
        s.addonPrice = addonPrice;
        s.maxQty = maxQty;
        s.onSale = onSale;
        s.supplyPrice = supplyPrice;
        s.sortOrder = sortOrder;
        return s;
    }
}
