package com.community.emoticon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 로보티콘 가격 전환 기록 — shared V143 {@code emoticon_price_events}. 추가만 한다(트리거가 고치기·지우기를 막는다). 운영자 가격 바꾸기는 사유, 새 판 승인은 심사 건.
 * 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 */
@Entity
@Table(name = "emoticon_price_events")
public class EmoticonPriceEventEntity {

    @Id
    private Long id;
    private Long packId;
    private int fromPrice;
    private int toPrice;
    private String reason;
    private Long reviewId;
    private Instant changedAt;
    private Long changedBy;

    protected EmoticonPriceEventEntity() {
    }

    public static EmoticonPriceEventEntity create(long id, long packId, int fromPrice, int toPrice, String reason, Long reviewId, Instant changedAt, long changedBy) {
        EmoticonPriceEventEntity e = new EmoticonPriceEventEntity();
        e.id = id;
        e.packId = packId;
        e.fromPrice = fromPrice;
        e.toPrice = toPrice;
        e.reason = reason;
        e.reviewId = reviewId;
        e.changedAt = changedAt;
        e.changedBy = changedBy;
        return e;
    }
}
