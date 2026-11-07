package com.community.mfa.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 「이 기기에서 다시 묻지 않기」 한 대 — admin 스트림 V3 의 {@code operator_trusted_devices}. 쿠키 값(256비트)은 두지 않고 SHA-256 만 둔다(전체 유일).
 * 기간 = 설정 개월 × 30일({@code expiresAt > trustedAt}). {@code toString} 을 덮어쓰지 않는다(기본 {@code Object#toString} 은 해시를 싣지 않는다).
 */
@Entity
@Table(name = "operator_trusted_devices")
public class OperatorTrustedDeviceEntity {

    @Id
    private Long id;

    private long operatorId;
    private String tokenHash;
    /** User-Agent 요약(원문 아님) */
    private String agent;
    private String ip;
    private Instant trustedAt;
    private Instant expiresAt;
    private Instant lastUsedAt;

    protected OperatorTrustedDeviceEntity() {
    }

    /** 새로 믿는 기기 — 아직 쓴 적 없다 */
    public static OperatorTrustedDeviceEntity create(long id, long operatorId, String tokenHash, String agent, String ip, Instant trustedAt, Instant expiresAt) {
        OperatorTrustedDeviceEntity row = new OperatorTrustedDeviceEntity();
        row.id = id;
        row.operatorId = operatorId;
        row.tokenHash = tokenHash;
        row.agent = agent;
        row.ip = ip;
        row.trustedAt = trustedAt;
        row.expiresAt = expiresAt;
        return row;
    }

    public Long getId() {
        return id;
    }
}
