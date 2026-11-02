package com.community.business.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.business.entity.BusinessProfileEntity;
import com.community.business.entity.NtsBizStatus;
import com.community.business.entity.NtsValidity;
import com.community.business.exception.BusinessException;
import com.community.business.nts.NtsLookup;
import com.community.business.nts.NtsStatusClient;
import com.community.business.repository.master.BusinessProfileRepository;
import com.community.business.repository.master.BusinessStore;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 국세청 조회(B1 + 진위확인 — business-contract 「국세청 진위확인 · 공공데이터 설정」). 외부 호출은 트랜잭션 밖이고, 스냅샷 한 줄 + 처리 기록(+ 운영자면 감사)은 한 트랜잭션에.
 * 클래스 트랜잭션을 두지 않는다(외부 호출 동안 DB 연결을 붙잡지 않는다).
 *
 * <ul>
 *   <li>신청·수정: {@link #validate}(트랜잭션 밖) → 신청 트랜잭션 안에서 {@link #record} — 일치면 같은 응답의 상태로 스냅샷을 채운다(상태조회를 따로 부르지 않는다).</li>
 *   <li>[다시 조회]({@link #check}): 개업일이 있으면 진위확인(상태 포함 — 불일치면 국세청이 상태를 주지 않아 상태조회를 한 번 더 한다), 없으면 상태조회만 + {@code UNCHECKED}.</li>
 * </ul>
 */
@Service
public class BusinessNtsService {

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

    /** 진위확인 한 번(외부 호출만 — 트랜잭션 밖에서 부른다). 던지지 않는다 */
    public NtsLookup validate(String bizNo, LocalDate openedOn, String ownerName, String companyName) {
        return client.validate(bizNo, openedOn, ownerName, companyName);
    }

    /**
     * 조회 결과를 스냅샷 + 처리 기록(+ 운영자면 감사)으로 남긴다 — <b>부르는 쪽의 트랜잭션 안에서</b>.
     *
     * @param operatorId 다시 조회한 운영자(신청·수정 때 서버가 한 조회는 null)
     */
    public void record(long profileId, NtsLookup result, Long operatorId, String ip) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        store.insertNtsCheck(ids.nextId(), profileId, result.status(), result.validity(), result.taxType(), result.closedOn(), result.message(), now, operatorId);
        store.insertHistory(ids.nextId(), profileId, BusinessStore.NTS_CHECKED, operatorId == null ? BusinessStore.SYSTEM : BusinessStore.OPERATOR, operatorId,
                historyNote(result), now);
        if (operatorId != null) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("status", result.status().name());
            detail.put("validity", result.validity().name());
            audit.record(new AuditEvent(AuditActorType.OPERATOR, operatorId, AuditActions.BUSINESS_NTS_CHECK, AuditResult.SUCCESS, AUDIT_TARGET,
                    Long.toString(profileId), ip, detail));
        }
    }

    /**
     * 운영자 [다시 조회] — 개업일이 있으면 진위확인, 없으면 상태조회만(진위 {@code UNCHECKED}). 조회해 스냅샷을 남긴다.
     *
     * @throws BusinessException 없는 신원(404) · 파기된 신원(409 {@code BUSINESS_STATE_CHANGED})
     */
    public NtsLookup check(long profileId, Long operatorId, String ip) {
        BusinessProfileEntity p = profiles.findById(profileId).orElseThrow(BusinessException::notFound);
        if (p.getBizNoEnc() == null) {
            throw BusinessException.stateChanged();
        }
        String bizNo = pii.decrypt(BusinessPii.BIZ_NO, p.getBizNoEnc());
        NtsLookup result;
        if (p.getOpenedOn() != null && p.getOwnerNameEnc() != null) {
            result = client.validate(bizNo, p.getOpenedOn(), pii.decrypt(BusinessPii.OWNER, p.getOwnerNameEnc()), p.getCompanyName());
            if (result.validity() == NtsValidity.MISMATCH && result.status() == NtsBizStatus.UNAVAILABLE && result.message() == null) {
                // 불일치면 국세청이 상태를 주지 않는다 — 심사 화면에 상태(없는 번호인지 등)를 보이려고 상태조회를 한 번 더 한다
                result = client.lookup(bizNo).withValidity(NtsValidity.MISMATCH);
            }
        } else {
            result = client.lookup(bizNo);
        }
        NtsLookup saved = result;
        tx.executeWithoutResult(status -> record(profileId, saved, operatorId, ip));
        return result;
    }

    /** 처리 기록 메모 — 진위 불일치면 {@code MISMATCH}, 그 밖은 상태(번들 {@code business.history.nts.<값>}) */
    private static String historyNote(NtsLookup result) {
        return result.validity() == NtsValidity.MISMATCH ? "MISMATCH" : result.status().name();
    }
}
