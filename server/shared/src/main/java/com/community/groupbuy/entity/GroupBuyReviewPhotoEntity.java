package com.community.groupbuy.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicInsert;

/**
 * 리뷰 사진(먼저 올리고 나중에 붙인다 — 붙지 않은 사진은 정리 대상) — shared V106 {@code group_buy_review_photos}.
 * 읽기는 저장소의 한 줄 모양(프로젝션), 붙이기·떼기는 저장소의 조건부 UPDATE·DELETE 다 — 이 엔티티는 새 줄 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 * {@code @DynamicInsert}: 값을 주지 않은 칸({@code position} 등)은 INSERT 에서 빠져 DB 기본값을 받는다.
 */
@Entity
@DynamicInsert
@Table(name = "group_buy_review_photos")
public class GroupBuyReviewPhotoEntity {

    @Id
    private Long id;
    private Long reviewId;
    private Long userId;
    private String imageKey;
    /** 붙인 차례(smallint, 기본 0) */
    private Integer position;
    private Instant createdAt;
    private Instant attachedAt;

    protected GroupBuyReviewPhotoEntity() {
    }

    /** 아직 리뷰에 붙지 않은 사진 */
    public static GroupBuyReviewPhotoEntity loose(long id, long userId, String imageKey, Instant now) {
        GroupBuyReviewPhotoEntity p = new GroupBuyReviewPhotoEntity();
        p.id = id;
        p.userId = userId;
        p.imageKey = imageKey;
        p.createdAt = now;
        return p;
    }
}
