package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 상품 단품(축 값 조합) — shared V101.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_product_skus")
public class GroupBuyProductSkuEntity {

    @Id
    private Long id;
    private Long productId;
    @JdbcTypeCode(SqlTypes.ARRAY)
    private Long[] valueIds;
    private String name;
    private Long defaultSupplyPrice;
    private Boolean onSale;
    private Integer sortOrder;

    protected GroupBuyProductSkuEntity() {
    }

    public static GroupBuyProductSkuEntity create(long id, long productId, Long[] valueIds, String name, Long defaultSupplyPrice, boolean onSale, int sortOrder) {
        GroupBuyProductSkuEntity s = new GroupBuyProductSkuEntity();
        s.id = id;
        s.productId = productId;
        s.valueIds = valueIds;
        s.name = name;
        s.defaultSupplyPrice = defaultSupplyPrice;
        s.onSale = onSale;
        s.sortOrder = sortOrder;
        return s;
    }
}
