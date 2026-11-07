package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;

/**
 * 상품 마스터(결정 89) — shared V101.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_products")
public class GroupBuyProductEntity {

    @Id
    private Long id;
    private Long categoryId;
    private String name;
    private String summary;
    private String spec;
    private String description;
    private Long normalPrice;
    private String normalPriceBasis;
    private String normalPriceSource;
    private String withdrawalRestriction;
    private Integer version;
    private Long createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    protected GroupBuyProductEntity() {
    }

    public static GroupBuyProductEntity create(long id, long categoryId, String name, String summary, String spec, String description, Long normalPrice,
                                               String normalPriceBasis, String normalPriceSource, String withdrawalRestriction, Long createdBy, Instant now) {
        GroupBuyProductEntity p = new GroupBuyProductEntity();
        p.id = id;
        p.categoryId = categoryId;
        p.name = name;
        p.summary = summary;
        p.spec = spec;
        p.description = description;
        p.normalPrice = normalPrice;
        p.normalPriceBasis = normalPriceBasis;
        p.normalPriceSource = normalPriceSource;
        p.withdrawalRestriction = withdrawalRestriction;
        p.createdBy = createdBy;
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }
}
