package com.community.point.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.point.dto.PointRules;
import com.community.point.dto.request.PointSettingsRequest;
import com.community.point.dto.response.PointSettingsResponse;
import com.community.point.entity.PointSettingEntity;
import com.community.point.repository.master.PointSettingRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 포인트 정책 — 조회 {@code point.read}, 저장 {@code point.settings}(컨트롤러가 선언).
 *
 * <ul>
 *   <li><b>저장</b>은 14칸을 모두 받는다. 칸마다 정수·범위({@link PointRules#LIMITS})를 검사해 틀린 칸을 모아 400 {@code VALIDATION} {@code fields.<칸>} — 하나라도 틀리면 아무것도 저장하지 않는다.</li>
 *   <li>바뀐 칸이 없으면 쓰지 않는다(마지막 저장 시각도 그대로). 감사 {@code POINT_SETTINGS_UPDATE} 는 바뀐 값만 {@code before}/{@code after}.</li>
 *   <li><b>반영</b>: 이 서버의 정책 사본({@link PointPolicy})은 커밋 직후 비우고, 공개 서버(api)는 사본 주기로 <b>최대 15초</b> 안에 따라온다(BoardCatalog 와 같은 방식). 소급 없음 —
 *       이미 지급한 포인트는 그대로이고 상한을 낮춰도 오늘 이미 센 횟수는 그대로다.</li>
 * </ul>
 */
@Service
public class PointSettingsService {

    private static final Logger log = LoggerFactory.getLogger(PointSettingsService.class);

    private final PointSettingRepository settings;
    private final OperatorRepository operators;
    private final AuditLogService audit;
    private final PointPolicy policy;

    public PointSettingsService(PointSettingRepository settings, OperatorRepository operators, AuditLogService audit, PointPolicy policy) {
        this.settings = settings;
        this.operators = operators;
        this.audit = audit;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public PointSettingsResponse load() {
        return settings.findById(PointSettingEntity.ROW_ID).map(this::response).orElseGet(() -> PointSettingsResponse.of(PointRules.DEFAULTS, null, null));
    }

    @Transactional
    public PointSettingsResponse save(AdminActor actor, PointSettingsRequest body) {
        PointRules wanted = check(body);
        PointSettingEntity row = settings.findWithLockById(PointSettingEntity.ROW_ID)
                .orElseThrow(() -> new IllegalStateException("point_settings 행이 없다"));
        Map<String, Object> current = row.values().toMap();
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : wanted.toMap().entrySet()) {
            Object old = current.get(entry.getKey());
            if (!Objects.equals(old, entry.getValue())) {
                before.put(entry.getKey(), old);
                after.put(entry.getKey(), entry.getValue());
            }
        }
        if (after.isEmpty()) {
            return response(row);
        }
        row.change(wanted, actor.operatorId(), Instant.now());
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.POINT_SETTINGS_UPDATE, AuditResult.SUCCESS).target("POINT_SETTINGS", "points")
                .with("before", before).with("after", after));
        invalidateAfterCommit();
        log.info("포인트 정책을 저장했다 칸={} by operatorId={}", after.keySet(), actor.operatorId());
        return response(row);
    }

    /** 칸마다 정수·범위 — 틀린 칸을 모아 400 */
    static PointRules check(PointSettingsRequest body) {
        Map<String, Number> raw = body == null ? new PointSettingsRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null).toMap()
                : body.toMap();
        Map<String, Integer> values = new LinkedHashMap<>();
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        for (String field : PointRules.FIELDS) {
            int[] range = PointRules.LIMITS.get(field);
            Integer value = integerOf(raw.get(field));
            if (value == null || value < range[0] || value > range[1]) {
                fields.put(field, new MessageRef("point.settings.range", String.format("%,d", range[0]), String.format("%,d", range[1])));
            } else {
                values.put(field, value);
            }
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return PointRules.of(values);
    }

    /** 정수면 그 값, 소수·빈 값·너무 큰 수는 null */
    private static Integer integerOf(Number raw) {
        if (raw == null) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(raw.toString());
            if (value.stripTrailingZeros().scale() > 0 || value.abs().compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
                return null;
            }
            return value.intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private PointSettingsResponse response(PointSettingEntity row) {
        String by = row.getUpdatedBy() == null ? null : operators.findById(row.getUpdatedBy()).map(o -> o.getOperatorName()).orElse(null);
        return PointSettingsResponse.of(row.values(), row.getUpdatedAt(), by);
    }

    /** 커밋된 뒤에만 사본을 버린다(롤백되면 그대로) */
    private void invalidateAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    policy.invalidate();
                }
            });
        } else {
            policy.invalidate();
        }
    }
}
