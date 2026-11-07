package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicInsert;

/**
 * 고시 양식이 적용되는 상품 단품 — shared V101(복합 FK 두 개: 양식·단품, 둘 다 같은 상품).
 * 읽기는 저장소의 한 줄 모양(프로젝션), 상태 전이·수량은 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠져 DB 기본값을 받는다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_notice_skus")
@IdClass(GroupBuyNoticeSkuId.class)
public class GroupBuyNoticeSkuEntity {

    @Id
    private Long noticeId;
    private Long productId;
    @Id
    private Long productSkuId;

    protected GroupBuyNoticeSkuEntity() {
    }

    public static GroupBuyNoticeSkuEntity create(long noticeId, long productId, long productSkuId) {
        GroupBuyNoticeSkuEntity s = new GroupBuyNoticeSkuEntity();
        s.noticeId = noticeId;
        s.productId = productId;
        s.productSkuId = productSkuId;
        return s;
    }
}
