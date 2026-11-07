package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 리뷰 작성자 수정 이력(고치기 전 별점·글·사진 수) — shared V106 {@code group_buy_review_revisions}. 넣기만 한다.
 */
@Entity
@Table(name = "group_buy_review_revisions")
public class GroupBuyReviewRevisionEntity {

    @Id
    private Long id;
    private Long reviewId;
    private Integer rating;
    private String body;
    private Integer photoCount;
    private Instant writtenAt;
    private Instant replacedAt;

    protected GroupBuyReviewRevisionEntity() {
    }

    public static GroupBuyReviewRevisionEntity of(long id, long reviewId, int rating, String body, int photoCount, Instant writtenAt, Instant replacedAt) {
        GroupBuyReviewRevisionEntity r = new GroupBuyReviewRevisionEntity();
        r.id = id;
        r.reviewId = reviewId;
        r.rating = rating;
        r.body = body;
        r.photoCount = photoCount;
        r.writtenAt = writtenAt;
        r.replacedAt = replacedAt;
        return r;
    }
}
