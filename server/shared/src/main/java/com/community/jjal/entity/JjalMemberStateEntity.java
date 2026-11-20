package com.community.jjal.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 회원의 처음 안내 확인 기록 — V261 {@code jjal_member_states}(시각 · 안내 판). 계정에 남겨 PC·휴대폰에서 두 번 나오지 않게(my-jjal-contract 「정책」) */
@Entity
@Table(name = "jjal_member_states")
public class JjalMemberStateEntity {

    @Id
    private Long userId;
    private Short introVersion;
    private Instant introAcknowledgedAt;
    private Instant createdAt;
    private Instant updatedAt;

    protected JjalMemberStateEntity() {
    }

    public static JjalMemberStateEntity create(long userId, Instant now) {
        JjalMemberStateEntity s = new JjalMemberStateEntity();
        s.userId = userId;
        s.createdAt = now;
        s.updatedAt = now;
        return s;
    }

    /** 이 판의 안내를 확인했다(이미 같은 판이나 뒤 판을 확인했으면 처음 시각 그대로) */
    public void acknowledge(int version, Instant now) {
        if (acknowledged(version)) {
            return;
        }
        this.introVersion = (short) version;
        this.introAcknowledgedAt = now;
        this.updatedAt = now;
    }

    /** 이 판(또는 뒤 판)의 안내를 확인했는가 */
    public boolean acknowledged(int version) {
        return introVersion != null && introVersion >= version;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getIntroAcknowledgedAt() {
        return introAcknowledgedAt;
    }
}
