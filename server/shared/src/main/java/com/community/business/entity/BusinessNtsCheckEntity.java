package com.community.business.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** 국세청 상태조회 스냅샷 한 줄 — shared V111 {@code business_nts_checks}(V125 진위 {@code validity}). 쌓기만 한다 */
@Entity
@Table(name = "business_nts_checks")
public class BusinessNtsCheckEntity {

    @Id
    private Long id;

    private Long profileId;
    @Enumerated(EnumType.STRING)
    private NtsBizStatus status;
    @Enumerated(EnumType.STRING)
    private NtsValidity validity;
    private String taxType;
    private LocalDate closedOn;
    private String message;
    private Instant checkedAt;
    private Long checkedBy;

    protected BusinessNtsCheckEntity() {
    }

    public static BusinessNtsCheckEntity create(long id, long profileId, NtsBizStatus status, NtsValidity validity, String taxType, LocalDate closedOn, String message,
                                                Instant checkedAt, Long checkedBy) {
        BusinessNtsCheckEntity c = new BusinessNtsCheckEntity();
        c.id = id;
        c.profileId = profileId;
        c.status = status;
        c.validity = validity;
        c.taxType = taxType;
        c.closedOn = closedOn;
        c.message = message;
        c.checkedAt = checkedAt;
        c.checkedBy = checkedBy;
        return c;
    }
}
