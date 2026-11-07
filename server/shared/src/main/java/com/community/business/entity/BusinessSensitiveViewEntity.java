package com.community.business.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 사업자 평문·등록증 열람 기록 한 줄 — shared V111 {@code business_sensitive_views}(운영자 · 사유 · IP). 쌓기만 한다 */
@Entity
@Table(name = "business_sensitive_views")
public class BusinessSensitiveViewEntity {

    @Id
    private Long id;

    private Long profileId;
    private String kind;
    private Long operatorId;
    private String reason;
    private String ip;
    private Instant createdAt;

    protected BusinessSensitiveViewEntity() {
    }

    public static BusinessSensitiveViewEntity create(long id, long profileId, String kind, long operatorId, String reason, String ip, Instant now) {
        BusinessSensitiveViewEntity v = new BusinessSensitiveViewEntity();
        v.id = id;
        v.profileId = profileId;
        v.kind = kind;
        v.operatorId = operatorId;
        v.reason = reason;
        v.ip = ip;
        v.createdAt = now;
        return v;
    }
}
