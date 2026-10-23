package com.community.audit.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.dto.AuditRetentionClass;
import com.community.audit.dto.request.AuditRetentionSaveRequest;
import com.community.audit.dto.response.AuditRetentionView;
import com.community.audit.entity.AuditResult;
import com.community.audit.repository.master.AuditRetentionStore;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.permission.exception.PermissionDeniedException;
import com.community.permission.service.AccessDeniedRecorder;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 보관 기간(감사 로그 화면의 「보관 기간」, docs/domain/commerce.md 13-4) — 조회는 {@code audit.read}(컨트롤러), <b>저장은 최고 관리자(루트)만</b>(감사 기록을 지우는 범위를
 * 정하는 값이라 권한 코드로 위임하지 않는다 — 루트가 아니면 거부 감사 + 403).
 *
 * <ul>
 *   <li>분류마다 일수는 하한 ~ 3650({@link AuditRetentionClass}). 세 분류를 모두 받아 칸마다 검사하고 틀린 칸을 모아 400 {@code VALIDATION} {@code fields.days.<분류>} —
 *       <b>하한보다 짧게는 못 줄인다</b>. 하나라도 틀리면 아무것도 저장하지 않는다.</li>
 *   <li>바뀐 분류만 쓰고 감사 {@code AUDIT_RETENTION_UPDATE}(보관 분류 MONEY)에 바뀐 값만 {@code before}/{@code after}.</li>
 *   <li>조회의 일수는 정리 배치가 실제로 쓰는 값이다 — 저장된 값이 없거나 범위 밖(DB 를 직접 고친 값)이면 기본값(배치의 {@code batch_param} 과 같은 규칙).</li>
 * </ul>
 */
@Service
public class AuditRetentionService {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionService.class);

    public static final String TARGET_TYPE = "AUDIT_RETENTION";

    private final AuditRetentionStore store;
    private final OperatorRepository operators;
    private final AuditLogService audit;
    private final AccessDeniedRecorder denied;

    public AuditRetentionService(AuditRetentionStore store, OperatorRepository operators, AuditLogService audit, AccessDeniedRecorder denied) {
        this.store = store;
        this.operators = operators;
        this.audit = audit;
        this.denied = denied;
    }

    @Transactional(readOnly = true)
    public AuditRetentionView view(AdminActor actor) {
        return viewOf(effective(store.stored(false)), actor, store.lastChange());
    }

    @Transactional
    public AuditRetentionView save(AdminActor actor, AuditRetentionSaveRequest body) {
        if (!actor.root()) {
            denied.record(actor, "SUPER_ONLY", TARGET_TYPE, AuditRetentionClass.JOB_KEY, List.of());
            throw new PermissionDeniedException();
        }
        Map<AuditRetentionClass, Long> wanted = check(body);
        Map<AuditRetentionClass, Long> current = effective(store.stored(true));
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        for (Map.Entry<AuditRetentionClass, Long> entry : wanted.entrySet()) {
            long old = current.get(entry.getKey());
            if (old != entry.getValue()) {
                store.save(entry.getKey(), entry.getValue(), now);
                before.put(entry.getKey().name(), old);
                after.put(entry.getKey().name(), entry.getValue());
                current.put(entry.getKey(), entry.getValue());
            }
        }
        if (!after.isEmpty()) {
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.AUDIT_RETENTION_UPDATE, AuditResult.SUCCESS)
                    .target(TARGET_TYPE, AuditRetentionClass.JOB_KEY).with("before", before).with("after", after));
            log.info("감사 보관 기간을 바꿨다 분류={} by operatorId={}", after.keySet(), actor.operatorId());
        }
        // 방금 남긴 감사 줄은 JPA 가 아직 DB 에 내리지 않았을 수 있어(JDBC 조회에 안 보인다) 이번 저장을 마지막 저장으로 쓴다
        return viewOf(current, actor, after.isEmpty() ? store.lastChange() : Optional.of(new AuditRetentionStore.LastChange(now, actor.operatorId())));
    }

    /** 세 분류 모두 정수·하한~상한 — 틀린 칸을 모아 400 */
    static Map<AuditRetentionClass, Long> check(AuditRetentionSaveRequest body) {
        Map<String, Object> raw = body == null || body.days() == null ? Map.of() : body.days();
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        Map<AuditRetentionClass, Long> values = new LinkedHashMap<>();
        for (AuditRetentionClass category : AuditRetentionClass.values()) {
            Long value = longOf(raw.get(category.name()));
            if (value == null || !category.allows(value)) {
                fields.put("days." + category.name(), new MessageRef("audit.retention.range", category.minDays(), category.maxDays()));
            } else {
                values.put(category, value);
            }
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return values;
    }

    /** 배치가 실제로 쓰는 일수 — 없거나 범위 밖이면 기본값 */
    static Map<AuditRetentionClass, Long> effective(Map<AuditRetentionClass, Long> stored) {
        Map<AuditRetentionClass, Long> out = new LinkedHashMap<>();
        for (AuditRetentionClass category : AuditRetentionClass.values()) {
            Long value = stored.get(category);
            out.put(category, value != null && category.allows(value) ? value : category.defaultDays());
        }
        return out;
    }

    private static Long longOf(Object raw) {
        if (!(raw instanceof Number number)) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(number.toString());
            if (value.stripTrailingZeros().scale() > 0) {
                return null;
            }
            return value.longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private AuditRetentionView viewOf(Map<AuditRetentionClass, Long> days, AdminActor actor, Optional<AuditRetentionStore.LastChange> last) {
        List<AuditRetentionView.Item> items = new ArrayList<>();
        for (AuditRetentionClass category : AuditRetentionClass.values()) {
            items.add(new AuditRetentionView.Item(category.name(), days.get(category), category.defaultDays(), category.minDays(), category.maxDays()));
        }
        String by = last.map(AuditRetentionStore.LastChange::operatorId).flatMap(operators::findById).map(OperatorEntity::getOperatorName).orElse(null);
        return new AuditRetentionView(items, actor.root(), last.map(AuditRetentionStore.LastChange::at).orElse(null), by);
    }
}
