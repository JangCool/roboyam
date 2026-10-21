package com.community.operator.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 승인번호 요청 한도 기록 한 줄 — V23__operator_password_reset.sql 의 {@code operator_cert_requests}. 요청이 받아들여질 때마다 이메일 키·IP 키 각각 한 줄이 쌓이고,
 * 쿨다운(60초)과 시간당 한도를 센다. 키는 접두어를 붙인 SHA-256 해시라 이메일·IP 원문이 남지 않는다. 쌓인 기록은 DB 배치 {@code auth-token-cleanup} 이 지운다.
 */
@Entity
@Table(name = "operator_cert_requests")
public class OperatorCertRequestEntity {

    @Id
    private Long id;

    private String keyHash;
    private Instant requestedAt;

    protected OperatorCertRequestEntity() {
    }

    public static OperatorCertRequestEntity create(long id, String keyHash, Instant now) {
        OperatorCertRequestEntity entity = new OperatorCertRequestEntity();
        entity.id = id;
        entity.keyHash = keyHash;
        entity.requestedAt = now;
        return entity;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }
}
