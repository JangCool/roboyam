package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicInsert;

/**
 * 주문 줄 — shared V101 · V102(교환 출고 수량).
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_participation_items")
public class GroupBuyParticipationItemEntity {

    @Id
    private Long id;
    private Long participationId;
    private Long groupBuyId;
    private Long skuId;
    private Integer qty;
    private Long unitPrice;
    private Long itemAmount;
    private Integer canceledQty;
    private Integer returnedQty;
    private Integer restockedQty;
    private Integer exchangedQty;

    protected GroupBuyParticipationItemEntity() {
    }

    /** 새 주문 줄 — 줄 금액 = 단가 × 수량(CHECK), 취소·반품 수량은 DB 기본값(0) */
    public static GroupBuyParticipationItemEntity create(long id, long participationId, long groupBuyId, long skuId, int qty, long unitPrice) {
        GroupBuyParticipationItemEntity i = new GroupBuyParticipationItemEntity();
        i.id = id;
        i.participationId = participationId;
        i.groupBuyId = groupBuyId;
        i.skuId = skuId;
        i.qty = qty;
        i.unitPrice = unitPrice;
        i.itemAmount = unitPrice * qty;
        return i;
    }
}
