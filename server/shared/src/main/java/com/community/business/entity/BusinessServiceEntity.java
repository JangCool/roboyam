package com.community.business.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 서비스별 이용 권한 한 줄 — V111 {@code business_services}(신원 하나에 HOTDEAL·AD 두 줄). 이용 자격은 {@code BusinessEligibility#approvedProfileOf} 한 곳이 판정한다
 * (신원 승인 + 그 서비스 줄 승인).
 */
@Entity
@Table(name = "business_services")
@DynamicUpdate
public class BusinessServiceEntity {

    @Id
    private Long id;

    private Long profileId;

    @Enumerated(EnumType.STRING)
    private BusinessServiceKind service;

    @Enumerated(EnumType.STRING)
    private BusinessServiceStatus status;

    private Instant updatedAt;
    private Long updatedBy;

    protected BusinessServiceEntity() {
    }

    public static BusinessServiceEntity create(long id, long profileId, BusinessServiceKind service, BusinessServiceStatus status, Instant now) {
        BusinessServiceEntity e = new BusinessServiceEntity();
        e.id = id;
        e.profileId = profileId;
        e.service = service;
        e.status = status;
        e.updatedAt = now;
        return e;
    }

    /** 상태를 옮긴다 — 같은 상태면 그대로(시각도 그대로) */
    public void moveTo(BusinessServiceStatus next, Long operatorId, Instant now) {
        if (status == next) {
            return;
        }
        this.status = next;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getProfileId() {
        return profileId;
    }

    public BusinessServiceKind getService() {
        return service;
    }

    public BusinessServiceStatus getStatus() {
        return status;
    }
}
