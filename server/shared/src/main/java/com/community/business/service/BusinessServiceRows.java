package com.community.business.service;

import com.community.business.entity.BusinessServiceEntity;
import com.community.business.entity.BusinessServiceKind;
import com.community.business.entity.BusinessServiceStatus;
import com.community.business.repository.master.BusinessServiceRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 서비스별 이용 권한 줄 맞추기 — 신원 처리를 따라간다(신청 = 심사 중 · 승인 · 반려 · 정지 · 해제 = 승인 · 탈퇴 = 닫힘). 단 「서비스만 정지」된 줄은 반려·닫힘 말고는
 * 건드리지 않는다(서비스 정지·해제는 {@code BusinessAdminService} 가 줄 하나만 바꾼다 — 2026-11-02).
 * 신원 처리 트랜잭션 안에서 부른다.
 */
@Component
public class BusinessServiceRows {

    private final BusinessServiceRepository services;
    private final SnowflakeIdGenerator ids;

    public BusinessServiceRows(BusinessServiceRepository services, SnowflakeIdGenerator ids) {
        this.services = services;
        this.ids = ids;
    }

    /** 없는 서비스 줄을 만들고 닫히지 않은 줄을 모두 {@code target} 으로({@code OFF} 면 모두 닫는다) */
    public void sync(long profileId, BusinessServiceStatus target, Long operatorId, Instant now) {
        move(profileId, target == BusinessServiceStatus.OFF ? EnumSet.allOf(BusinessServiceStatus.class) : EnumSet.complementOf(EnumSet.of(BusinessServiceStatus.OFF)),
                target, operatorId, now);
    }

    /** {@code from} 상태의 줄만 {@code target} 으로(정지 해제 — 정지 줄만 승인으로) — 없는 서비스 줄은 {@code target} 으로 만든다 */
    public void move(long profileId, Set<BusinessServiceStatus> from, BusinessServiceStatus target, Long operatorId, Instant now) {
        List<BusinessServiceEntity> rows = services.findByProfileId(profileId);
        EnumSet<BusinessServiceKind> missing = EnumSet.allOf(BusinessServiceKind.class);
        for (BusinessServiceEntity row : rows) {
            missing.remove(row.getService());
            if (row.suspendedAlone() && target != BusinessServiceStatus.REJECTED && target != BusinessServiceStatus.OFF) {
                continue; // 서비스만 정지된 줄 — 신원 정지·해제·재승인이 건드리지 않는다(서비스 해제가 따로 한다, business-contract 2026-11-02)
            }
            if (from.contains(row.getStatus())) {
                row.moveTo(target, operatorId, now);
            }
        }
        for (BusinessServiceKind kind : missing) {
            services.persist(BusinessServiceEntity.create(ids.nextId(), profileId, kind, target, now));
        }
    }
}
