package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;

/**
 * 공구 리뷰(주문 줄마다 하나 — 지워도 줄은 남는다) — shared V106 {@code group_buy_reviews}. 유일 {@code ux_gbr_item(item_id)}.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 고치기·숨김·지우기는 저장소의 조건부 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸은 INSERT 에서 빠진다(예전 INSERT 문장의 칸 목록과 같다).
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_reviews")
public class GroupBuyReviewEntity {

    @Id
    private Long id;
    private Long productId;
    private Long groupBuyId;
    private Long participationId;
    private Long itemId;
    private Long userId;
    private String skuName;
    /** 별점 1~5(smallint) */
    private Integer rating;
    private String body;
    private String hiddenReasonCode;
    private String hiddenMemo;
    private Instant hiddenAt;
    private Long hiddenBy;
    private Instant deletedAt;
    private Instant updatedAt;
    private Instant createdAt;

    protected GroupBuyReviewEntity() {
    }

    public static GroupBuyReviewEntity written(long id, long productId, long groupBuyId, long participationId, long itemId, long userId, String skuName, int rating,
                                               String body, Instant now) {
        GroupBuyReviewEntity r = new GroupBuyReviewEntity();
        r.id = id;
        r.productId = productId;
        r.groupBuyId = groupBuyId;
        r.participationId = participationId;
        r.itemId = itemId;
        r.userId = userId;
        r.skuName = skuName;
        r.rating = rating;
        r.body = body;
        r.createdAt = now;
        return r;
    }
}
