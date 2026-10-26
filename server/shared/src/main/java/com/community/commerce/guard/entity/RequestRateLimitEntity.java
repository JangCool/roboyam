package com.community.commerce.guard.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 요청 종류별 1분 한도 한 줄 — V62 의 {@code request_rate_limits}(기본 4행). 관리자 화면(커머스 설정 › 요청 제한)이 {@link #change} 로 바꾸고,
 * 돈 요청은 이 표를 직접 읽지 않고 15초 사본({@code RequestRateLimits})을 읽는다.
 */
@Entity
@Table(name = "request_rate_limits")
public class RequestRateLimitEntity {

    @Id
    private String action;

    private int limitPerMinute;
    private Instant updatedAt;
    private Long updatedBy;

    protected RequestRateLimitEntity() {
    }

    public void change(int limit, long operatorId, Instant now) {
        this.limitPerMinute = limit;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public String getAction() {
        return action;
    }

    public int getLimitPerMinute() {
        return limitPerMinute;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
