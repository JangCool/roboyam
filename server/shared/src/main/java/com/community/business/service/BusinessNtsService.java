package com.community.business.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.business.entity.BusinessProfileEntity;
import com.community.business.exception.BusinessException;
import com.community.business.nts.NtsLookup;
import com.community.business.nts.NtsStatusClient;
import com.community.business.repository.master.BusinessProfileRepository;
import com.community.business.repository.master.BusinessStore;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 국세청 상태조회 한 번(B1) — 등록번호를 풀어 외부 조회(트랜잭션 밖) → 스냅샷 한 줄 + 처리 기록(+ 운영자면 감사)을 한 트랜잭션에. 클래스 트랜잭션을 두지 않는다
 * (외부 호출 동안 DB 연결을 붙잡지 않는다). 신청·수정 뒤 서버가 한 번(실패해도 신청은 그대로 — {@link #checkQuietly}), 운영자 [다시 조회]가 한 번씩 부른다.
 */
@Service
public class BusinessNtsService {

    private static final Logger log = LoggerFactory.getLogger(BusinessNtsService.class);

    public static final String AUDIT_TARGET = "BUSINESS";

    private final BusinessProfileRepository profiles;
    private final BusinessStore store;
    private final BusinessPii pii;
    private final NtsStatusClient client;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;
    private final Clock clock;

    public BusinessNtsService(BusinessProfileRepository profiles, BusinessStore store, BusinessPii pii, NtsStatusClient client, AuditLogService audit,
                              SnowflakeIdGenerator ids, PlatformTransactionManager transactions, ObjectProvider<Clock> clocks) {
        this.profiles = profiles;
        this.store = store;
        this.pii = pii;
        this.client = client;
        this.audit = audit;
        this.ids = ids;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /**
     * 조회해 스냅샷을 남긴다.
     *
     * @param operatorId 다시 조회한 운영자(서버가 신청 뒤 한 조회는 null)
     * @throws BusinessException 없는 신원(404) · 파기된 신원(409 {@code BUSINESS_STATE_CHANGED})
     */
    public NtsLookup check(long profileId, Long operatorId, String ip) {
        BusinessProfileEntity p = profiles.findById(profileId).orElseThrow(BusinessException::notFound);
        if (p.getBizNoEnc() == null) {
            throw BusinessException.stateChanged();
        }
        String bizNo = pii.decrypt(BusinessPii.BIZ_NO, p.getBizNoEnc());
        NtsLookup result = client.lookup(bizNo);
        tx.executeWithoutResult(status -> {
            Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
            store.insertNtsCheck(ids.nextId(), profileId, result.status(), result.taxType(), result.closedOn(), result.message(), now, operatorId);
            store.insertHistory(ids.nextId(), profileId, BusinessStore.NTS_CHECKED, operatorId == null ? BusinessStore.SYSTEM : BusinessStore.OPERATOR, operatorId,
                    result.status().name(), now);
            if (operatorId != null) {
                audit.record(new AuditEvent(AuditActorType.OPERATOR, operatorId, AuditActions.BUSINESS_NTS_CHECK, AuditResult.SUCCESS, AUDIT_TARGET,
                        Long.toString(profileId), ip, Map.of("status", result.status().name())));
            }
        });
        return result;
    }

    /** 신청·수정 뒤 서버가 하는 조회 — 실패해도 던지지 않는다(외부 장애가 신청을 막지 않는다). 운영자는 [다시 조회]로 다시 한다 */
    public void checkQuietly(long profileId) {
        try {
            check(profileId, null, null);
        } catch (RuntimeException e) {
            log.warn("신청 뒤 국세청 상태조회를 남기지 못했다 — {}", e.getClass().getSimpleName());
        }
    }
}
