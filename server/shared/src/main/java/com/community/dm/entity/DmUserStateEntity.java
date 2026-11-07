package com.community.dm.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 회원마다 쪽지 상태 한 줄 — shared V70 의 {@code dm_user_states}: 수신 설정(줄이 없으면 받기)과 오늘(한국 시간)의 한도 사용량·마지막 전송 시각.
 * 줄은 api {@code DmQueries} 의 한 문장 upsert({@code ON CONFLICT})가 만들고, 보낼 때 이 줄을 쓰기 잠금으로 읽어 간격·한도 판정과 차감을 한 트랜잭션에서 한다.
 */
@Entity
@Table(name = "dm_user_states")
public class DmUserStateEntity {

    @Id
    private Long userId;

    private boolean acceptDm;
    /** 한도를 센 날(한국 시간) */
    private LocalDate quotaDay;
    private int sentToday;
    private int newPartnersToday;
    private Instant lastSentAt;
    private Instant updatedAt;

    protected DmUserStateEntity() {
    }

    public boolean isAcceptDm() {
        return acceptDm;
    }

    public LocalDate getQuotaDay() {
        return quotaDay;
    }

    public int getSentToday() {
        return sentToday;
    }

    public int getNewPartnersToday() {
        return newPartnersToday;
    }

    public Instant getLastSentAt() {
        return lastSentAt;
    }
}
