package com.community.hotdeal.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 핫딜 종료 요청 한 줄 — V122 {@code deal_end_requests}(글·회원마다 한 줄). 요청자 신원은 이 표 밖으로 내지 않는다.
 * {@code flaggedPeriod}: 의심 구간에 든 요청(보상 대상) — 회차 단위로 켜고 끄는 일은 {@code DealEndRequestStore} 의 대량 UPDATE 가 한다.
 * reason: SOLD_OUT · PRICE_CHANGED · LINK_DEAD.
 */
@Entity
@Table(name = "deal_end_requests")
public class DealEndRequestEntity {

    @Id
    private Long id;

    private Long postId;
    private Long memberId;
    /** 글 작성자(같은 작성자 기간 한도) — 비회원 글 등이면 null */
    private Long authorId;
    private String reason;
    private boolean flaggedPeriod;
    private boolean fromReport;
    private Instant createdAt;

    protected DealEndRequestEntity() {
    }

    public static DealEndRequestEntity create(long id, long postId, long memberId, Long authorId, String reason, boolean flaggedPeriod, boolean fromReport, Instant now) {
        DealEndRequestEntity row = new DealEndRequestEntity();
        row.id = id;
        row.postId = postId;
        row.memberId = memberId;
        row.authorId = authorId;
        row.reason = reason;
        row.flaggedPeriod = flaggedPeriod;
        row.fromReport = fromReport;
        row.createdAt = now;
        return row;
    }
}
