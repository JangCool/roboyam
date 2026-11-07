package com.community.emoticon.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 로보티콘 정산 처리 기록 — shared V145 {@code emoticon_settlement_events}(+ V148 금액·동의 시각, V149 수동 지급). 추가만 한다(고치기·지우기는 트리거가 막는다).
 * 처리 종류 {@code action}(보류·풀기·지급·포기·수동 지급 …)과 칸의 짝은 표의 CHECK 가 본다. 넣기({@code persist})와 질의 경로(Q 클래스)에 쓴다.
 */
@Entity
@Table(name = "emoticon_settlement_events")
public class EmoticonSettlementEventEntity {

    @Id
    private Long id;
    private Long creatorId;
    private LocalDate salesMonth;
    private String action;
    private Instant at;
    private Long operatorId;
    private String note;
    private Long amount;
    private Instant consentedAt;

    protected EmoticonSettlementEventEntity() {
    }

    /** 처리 기록 한 줄 — 금액·동의 시각은 포기·수동 지급 기록만(나머지는 null) */
    public static EmoticonSettlementEventEntity create(long id, long creatorId, LocalDate salesMonth, String action, Instant at, Long operatorId, String note, Long amount,
                                                       Instant consentedAt) {
        EmoticonSettlementEventEntity e = new EmoticonSettlementEventEntity();
        e.id = id;
        e.creatorId = creatorId;
        e.salesMonth = salesMonth;
        e.action = action;
        e.at = at;
        e.operatorId = operatorId;
        e.note = note;
        e.amount = amount;
        e.consentedAt = consentedAt;
        return e;
    }
}
