package com.community.commerce.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.commerce.dto.request.RequestLimitsSaveRequest;
import com.community.commerce.dto.response.RequestLimitsView;
import com.community.commerce.guard.dto.RequestAction;
import com.community.commerce.guard.entity.RequestRateLimitEntity;
import com.community.commerce.guard.repository.master.RequestRateLimitRepository;
import com.community.commerce.guard.service.RequestRateLimits;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 커머스 설정 › 요청 제한(docs/domain/commerce.md 12-4) — 조회·저장 모두 {@code commerce.settings}(컨트롤러가 선언).
 *
 * <ul>
 *   <li><b>저장</b>은 네 종류를 모두 받는다. 종류마다 정수 0~100(0 = 제한 없음)을 검사해 틀린 칸을 모아 400 {@code VALIDATION} {@code fields.limits.<코드>} —
 *       하나라도 틀리면 아무것도 저장하지 않는다. 모르는 종류 코드는 {@code fields.limits} 로 막는다.</li>
 *   <li>바뀐 종류만 쓰고(마지막 저장 시각·운영자도 그 줄만), 감사 {@code COMMERCE_REQUEST_LIMIT_UPDATE} 에 바뀐 값만 {@code before}/{@code after}. 바뀐 것이 없으면 쓰지 않는다.</li>
 *   <li><b>반영</b>: 이 서버의 사본({@link RequestRateLimits})은 커밋 직후 비우고, 공개 서버(api)는 사본 주기로 <b>최대 15초</b> 안에 따라온다.</li>
 * </ul>
 */
@Service
public class CommerceRequestLimitService {

    private static final Logger log = LoggerFactory.getLogger(CommerceRequestLimitService.class);

    public static final String TARGET_TYPE = "COMMERCE_SETTINGS";
    public static final String TARGET_ID = "request-limits";

    private final RequestRateLimitRepository limits;
    private final OperatorRepository operators;
    private final AuditLogService audit;
    private final RequestRateLimits copy;

    public CommerceRequestLimitService(RequestRateLimitRepository limits, OperatorRepository operators, AuditLogService audit, RequestRateLimits copy) {
        this.limits = limits;
        this.operators = operators;
        this.audit = audit;
        this.copy = copy;
    }

    @Transactional(readOnly = true)
    public RequestLimitsView load() {
        return view(limits.findAll());
    }

    @Transactional
    public RequestLimitsView save(AdminActor actor, RequestLimitsSaveRequest body) {
        Map<RequestAction, Integer> wanted = check(body);
        Map<String, RequestRateLimitEntity> rows = limits.findAllWithLockByActionIsNotNull().stream()
                .collect(Collectors.toMap(RequestRateLimitEntity::getAction, Function.identity()));
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        for (Map.Entry<RequestAction, Integer> entry : wanted.entrySet()) {
            RequestRateLimitEntity row = rows.get(entry.getKey().name());
            if (row == null) {
                throw new IllegalStateException("request_rate_limits 에 " + entry.getKey() + " 행이 없다");
            }
            if (row.getLimitPerMinute() != entry.getValue()) {
                before.put(entry.getKey().name(), row.getLimitPerMinute());
                after.put(entry.getKey().name(), entry.getValue());
                row.change(entry.getValue(), actor.operatorId(), now);
            }
        }
        if (!after.isEmpty()) {
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.COMMERCE_REQUEST_LIMIT_UPDATE, AuditResult.SUCCESS)
                    .target(TARGET_TYPE, TARGET_ID).with("before", before).with("after", after));
            invalidateAfterCommit();
            log.info("요청 제한을 저장했다 종류={} by operatorId={}", after.keySet(), actor.operatorId());
        }
        return view(new ArrayList<>(rows.values()));
    }

    /** 네 종류 모두 정수 0~100 — 틀린 칸을 모아 400 */
    static Map<RequestAction, Integer> check(RequestLimitsSaveRequest body) {
        Map<String, Object> raw = body == null || body.limits() == null ? Map.of() : body.limits();
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        for (String code : raw.keySet()) {
            if (RequestAction.ofCode(code).isEmpty()) {
                fields.put("limits", new MessageRef("commerce.requestLimit.unknown", code));
            }
        }
        Map<RequestAction, Integer> values = new LinkedHashMap<>();
        for (RequestAction action : RequestAction.values()) {
            Integer value = integerOf(raw.get(action.name()));
            if (value == null || value < RequestAction.MIN_LIMIT || value > RequestAction.MAX_LIMIT) {
                fields.put("limits." + action.name(), new MessageRef("commerce.requestLimit.range", RequestAction.MIN_LIMIT, RequestAction.MAX_LIMIT));
            } else {
                values.put(action, value);
            }
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return values;
    }

    /** 정수면 그 값, 소수·빈 값·글자·너무 큰 수는 null */
    private static Integer integerOf(Object raw) {
        if (!(raw instanceof Number number)) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(number.toString());
            if (value.stripTrailingZeros().scale() > 0 || value.abs().compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
                return null;
            }
            return value.intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private RequestLimitsView view(List<RequestRateLimitEntity> rows) {
        Map<String, RequestRateLimitEntity> byAction = rows.stream().collect(Collectors.toMap(RequestRateLimitEntity::getAction, Function.identity()));
        List<RequestLimitsView.Item> items = new ArrayList<>();
        for (RequestAction action : RequestAction.values()) {
            RequestRateLimitEntity row = byAction.get(action.name());
            items.add(new RequestLimitsView.Item(action.name(), row == null ? action.defaultLimit() : row.getLimitPerMinute(), action.defaultLimit()));
        }
        Optional<RequestRateLimitEntity> last = rows.stream().filter(row -> row.getUpdatedAt() != null).max(Comparator.comparing(RequestRateLimitEntity::getUpdatedAt));
        String by = last.map(RequestRateLimitEntity::getUpdatedBy).flatMap(operators::findById).map(OperatorEntity::getOperatorName).orElse(null);
        return new RequestLimitsView(items, last.map(RequestRateLimitEntity::getUpdatedAt).orElse(null), by);
    }

    /** 커밋된 뒤에만 사본을 버린다(롤백되면 그대로) */
    private void invalidateAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    copy.invalidate();
                }
            });
        } else {
            copy.invalidate();
        }
    }
}
