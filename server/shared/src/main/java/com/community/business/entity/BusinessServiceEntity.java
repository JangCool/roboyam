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

    /** 「서비스만 정지」 표시(V130 — 신원 정지를 따라간 정지는 비어 있다). 있는 동안 신원 해제·재승인이 이 줄을 되살리지 않는다 */
    @Enumerated(EnumType.STRING)
    private BusinessSuspendCode suspendCode;
    private String suspendText;
    private Instant suspendedAt;
    private Long suspendedBy;

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

    /** 상태를 옮긴다 — 같은 상태면 그대로(시각도 그대로). 반려·닫힘이면 「서비스만 정지」 표시도 지운다 */
    public void moveTo(BusinessServiceStatus next, Long operatorId, Instant now) {
        if (next == BusinessServiceStatus.REJECTED || next == BusinessServiceStatus.OFF) {
            clearSuspension();
        }
        if (status == next) {
            return;
        }
        this.status = next;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    /** 서비스만 정지(신원은 그대로) — 승인 줄만. 사유 코드·글·운영자를 남긴다 */
    public void suspendAlone(BusinessSuspendCode code, String text, long operatorId, Instant now) {
        this.status = BusinessServiceStatus.SUSPENDED;
        this.suspendCode = code;
        this.suspendText = text;
        this.suspendedAt = now;
        this.suspendedBy = operatorId;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    /** 서비스만 정지 해제 — 승인으로 되돌리고 표시를 지운다 */
    public void resumeAlone(long operatorId, Instant now) {
        clearSuspension();
        this.status = BusinessServiceStatus.APPROVED;
        this.updatedBy = operatorId;
        this.updatedAt = now;
    }

    /** 「서비스만 정지」인가(사유가 남아 있다) */
    public boolean suspendedAlone() {
        return suspendCode != null;
    }

    private void clearSuspension() {
        this.suspendCode = null;
        this.suspendText = null;
        this.suspendedAt = null;
        this.suspendedBy = null;
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

    public BusinessSuspendCode getSuspendCode() {
        return suspendCode;
    }

    public String getSuspendText() {
        return suspendText;
    }

    public Instant getSuspendedAt() {
        return suspendedAt;
    }

    public Long getSuspendedBy() {
        return suspendedBy;
    }
}
