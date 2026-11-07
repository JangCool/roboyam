package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;

/**
 * 리뷰의 판매자 공식 답글(리뷰 하나에 하나) — shared V106 {@code group_buy_review_replies}.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 고치기는 저장소의 UPDATE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_review_replies")
public class GroupBuyReviewReplyEntity {

    @Id
    private Long reviewId;
    private String body;
    private Long repliedBy;
    private Instant repliedAt;
    private Long updatedBy;
    private Instant updatedAt;

    protected GroupBuyReviewReplyEntity() {
    }

    /** 첫 답글 */
    public static GroupBuyReviewReplyEntity first(long reviewId, String body, long repliedBy, Instant repliedAt) {
        GroupBuyReviewReplyEntity r = new GroupBuyReviewReplyEntity();
        r.reviewId = reviewId;
        r.body = body;
        r.repliedBy = repliedBy;
        r.repliedAt = repliedAt;
        return r;
    }
}
