package com.community.mfa.service;

import com.community.adminauth.service.UserAgentSummary;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.mfa.dto.MfaDecision;
import com.community.mfa.dto.MfaLoginMethod;
import com.community.mfa.dto.MfaSettings;
import com.community.mfa.dto.OperatorMfaRow;
import com.community.mfa.dto.PendingMfa;
import com.community.mfa.dto.response.AdminLoginMfa;
import com.community.mfa.repository.master.MfaStore;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호가 맞은 뒤의 로그인 흐름 — 판정({@link MfaPolicy#decide})과 <b>로그인 완성</b>(감사·마지막 로그인 시각).
 *
 * <ul>
 *   <li>판정: 사이트 설정 · 등록 여부 · 신뢰 기기 쿠키(켜져 있고 등록됐을 때만 본다) · 계정을 만든 시각으로 바로 로그인 / GRACE 안내 / VERIFY·ENROLL 대기를 정한다.</li>
 *   <li>완성({@link #complete}): 감사 {@code LOGIN_SUCCESS}(detail {@code mfa} = OFF·TRUSTED_DEVICE·TOTP·RECOVERY_CODE·ENROLLED·GRACE, 접속 환경 요약 {@code agent},
 *       비밀번호 재설정으로 들어왔으면 {@code via}) + 마지막 로그인 시각. 비밀번호 단계의 성공은 비밀번호 실패 횟수만 지우고 이것을 남기지 않는다 — 로그인 이력은 세션이 완성된 줄만 읽는다.</li>
 * </ul>
 */
@Service
public class MfaLoginService {

    private final MfaStore store;
    private final OperatorRepository operators;
    private final TrustedDeviceService devices;
    private final AuditLogService audit;
    private final Clock clock;

    public MfaLoginService(MfaStore store, OperatorRepository operators, TrustedDeviceService devices, AuditLogService audit, Clock clock) {
        this.store = store;
        this.operators = operators;
        this.devices = devices;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * 비밀번호가 맞은 운영자의 판정.
     *
     * @param trustToken 요청의 신뢰 기기 쿠키 값(없으면 null)
     */
    @Transactional
    public MfaDecision decide(long operatorId, String trustToken) {
        MfaSettings settings = store.settings();
        if (!settings.enabled()) {
            return MfaPolicy.decide(settings, false, false, null, clock.instant());
        }
        OperatorEntity operator = operators.findById(operatorId).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        boolean enrolled = store.find(operatorId).map(OperatorMfaRow::enrolled).orElse(false);
        boolean trusted = enrolled && devices.isTrusted(operatorId, trustToken);
        return MfaPolicy.decide(settings, enrolled, trusted, operator.getCreatedAt(), clock.instant());
    }

    /** 판정을 로그인 응답의 {@code mfa} 로 — 바로 로그인이고 안내가 없으면 null */
    @Transactional(readOnly = true)
    public AdminLoginMfa view(MfaDecision decision) {
        if (decision.stage() == null) {
            return null;
        }
        return new AdminLoginMfa(decision.stage(), decision.graceEndsAt(), store.settings().trustDays());
    }

    /** 2차 대기 세션의 {@code mfa}(새로고침해도 단계가 유지된다) */
    @Transactional(readOnly = true)
    public AdminLoginMfa view(PendingMfa pending) {
        return new AdminLoginMfa(pending.stage(), null, store.settings().trustDays());
    }

    /**
     * 로그인을 완성한다 — 감사 {@code LOGIN_SUCCESS} + 마지막 로그인 시각. 세션을 만드는 것은 웹 계층(AdminSession)이다.
     *
     * @param userAgent 요청의 User-Agent 원문(요약만 남긴다)
     * @param via       다른 흐름으로 들어왔을 때(PASSWORD_RESET). 없으면 null
     */
    @Transactional
    public void complete(long operatorId, MfaLoginMethod method, String ip, String userAgent, String via) {
        AuditEvent event = AuditEvent.byOperator(operatorId, ip, AuditActions.LOGIN_SUCCESS, AuditResult.SUCCESS).target("OPERATOR", Long.toString(operatorId))
                .with("mfa", method.name());
        if (via != null) {
            event = event.with("via", via);
        }
        audit.record(UserAgentSummary.attach(event, userAgent));
        operators.findById(operatorId).ifPresent(operator -> operator.markLoggedIn(clock.instant()));
    }
}
