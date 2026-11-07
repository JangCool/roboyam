package com.community.mfa.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 로그인 보안 설정(행이 하나뿐이다, {@code id = 1}) — admin 스트림 V3 의 {@code admin_security_settings}. 행은 마이그레이션이 넣는다(없으면 {@code MfaStore} 가 기본값 행을 만든다).
 * 바꾸는 일은 행을 쓰기 잠금으로 읽은 트랜잭션 안에서 {@link #change} 로만 한다(변경 감지). 칸 범위(유예 1~90일 · 1~12개월 · 실패 3~10회)는 서비스와 표 검사가 막는다.
 */
@Entity
@Table(name = "admin_security_settings")
public class AdminSecuritySettingEntity {

    /** 행 번호 — 행은 하나뿐이라 항상 1 이다 */
    public static final int ROW_ID = 1;

    @Id
    private Integer id;

    private boolean mfaEnabled;
    private int graceDays;
    private int trustMonths;
    private int maxFailures;
    /** 마지막으로 켠 시각(유예의 시작). 켠 적 없으면 null */
    private Instant mfaEnabledAt;
    /** 마지막으로 켠 운영자. 없으면 null */
    private Long mfaEnabledBy;
    private Instant updatedAt;
    private Long updatedBy;

    protected AdminSecuritySettingEntity() {
    }

    /** 설정을 통째로 바꾸고 바꾼 사람·시각을 남긴다 */
    public void change(boolean mfaEnabled, int graceDays, int trustMonths, int maxFailures, Instant mfaEnabledAt, Long mfaEnabledBy, long updatedBy, Instant now) {
        this.mfaEnabled = mfaEnabled;
        this.graceDays = graceDays;
        this.trustMonths = trustMonths;
        this.maxFailures = maxFailures;
        this.mfaEnabledAt = mfaEnabledAt;
        this.mfaEnabledBy = mfaEnabledBy;
        this.updatedAt = now;
        this.updatedBy = updatedBy;
    }

    public boolean isMfaEnabled() {
        return mfaEnabled;
    }

    public int getGraceDays() {
        return graceDays;
    }

    public int getTrustMonths() {
        return trustMonths;
    }

    public int getMaxFailures() {
        return maxFailures;
    }

    public Instant getMfaEnabledAt() {
        return mfaEnabledAt;
    }

    public Long getMfaEnabledBy() {
        return mfaEnabledBy;
    }
}
