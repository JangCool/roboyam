package com.community.mfa.service;

import com.community.adminauth.service.UserAgentSummary;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.mfa.repository.master.MfaStore;
import com.community.zconfiguration.crypto.Digests;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 「이 기기에서 다시 묻지 않기」(신뢰 기기).
 *
 * <ul>
 *   <li>쿠키 값은 256비트 무작위({@link Digests#newToken}) — DB 에는 SHA-256 만(전체 유일). 기간 = 설정 개월 × 30일(1~12개월, 기본 30일), 만든 때 정한다.</li>
 *   <li>통과는 <b>그 운영자의</b> 기간이 남은 기기 기록일 때만 — 다른 운영자의 쿠키로는 통과하지 않는다.</li>
 *   <li>모두 끊기({@link #revokeAll}): 비밀번호 변경·재설정, OTP 다시 등록 확정, 계정 잠김, 루트의 OTP 초기화 때 부른다 — 감사 {@code MFA_TRUSTED_DEVICE_REVOKE}({@code cause}).</li>
 * </ul>
 * 쿠키 값·해시를 로그·감사에 싣지 않는다.
 */
@Service
public class TrustedDeviceService {

    private static final Logger log = LoggerFactory.getLogger(TrustedDeviceService.class);

    private final MfaStore store;
    private final SnowflakeIdGenerator ids;
    private final AuditLogService audit;
    private final Clock clock;

    public TrustedDeviceService(MfaStore store, SnowflakeIdGenerator ids, AuditLogService audit, Clock clock) {
        this.store = store;
        this.ids = ids;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * 이 기기를 믿는다 — 쿠키에 실을 값을 돌려준다(부르는 쪽의 트랜잭션에 참여한다).
     *
     * @param trustDays 기간(일) — 설정의 개월 × 30
     * @param userAgent 요청의 User-Agent 원문(요약만 저장한다)
     */
    @Transactional
    public String trust(long operatorId, int trustDays, String ip, String userAgent) {
        Instant now = clock.instant();
        String token = Digests.newToken();
        store.insertDevice(ids.nextId(), operatorId, hash(token), UserAgentSummary.of(userAgent), ip, now, now.plus(Duration.ofDays(trustDays)));
        return token;
    }

    /** 이 운영자의 기간이 남은 신뢰 기기 쿠키인가 — 맞으면 마지막 사용 시각을 갱신한다 */
    @Transactional
    public boolean isTrusted(long operatorId, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Instant now = clock.instant();
        return store.findValidDevice(operatorId, hash(token), now).map(id -> {
            store.touchDevice(id, now);
            return true;
        }).orElse(false);
    }

    /**
     * 그 운영자의 신뢰 기기를 모두 끊는다. 하나라도 끊었으면 감사를 남긴다.
     *
     * @param cause   끊은 까닭(PASSWORD_CHANGE · PASSWORD_RESET · REENROLL · ACCOUNT_LOCKED · MFA_RESET · SELF)
     * @param actorId 행위자 운영자(본인이거나 잠근·초기화한 운영자)
     * @return 끊은 수
     */
    @Transactional
    public int revokeAll(long operatorId, String cause, long actorId, String ip) {
        int removed = store.deleteDevices(operatorId);
        if (removed > 0) {
            audit.record(AuditEvent.byOperator(actorId, ip, AuditActions.MFA_TRUSTED_DEVICE_REVOKE, AuditResult.SUCCESS).target("OPERATOR", Long.toString(operatorId))
                    .with("count", removed).with("all", true).with("cause", cause));
            log.info("신뢰 기기를 모두 끊었다 operatorId={} {}대 까닭={}", operatorId, removed, cause);
        }
        return removed;
    }

    /** 쿠키 값의 해시(null 이면 null) */
    public static String hash(String token) {
        return token == null || token.isBlank() ? null : Digests.sha256Hex(token);
    }
}
