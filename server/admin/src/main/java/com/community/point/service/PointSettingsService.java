package com.community.point.service;

import com.community.cache.service.CacheRefresher;
import com.community.cache.service.CacheSaveSource;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.point.dto.PointLotRules;
import com.community.point.dto.PointRules;
import com.community.point.dto.PointTierRules;
import com.community.point.dto.PointTierValues;
import com.community.point.entity.PointBoardTier;
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

/**
 * 포인트 정책 — 조회 {@code point.read}, 저장 {@code point.settings}(컨트롤러가 선언).
 *
 * <ul>
 *   <li><b>저장</b>은 칸을 모두 받는다(지급 13칸 + 커머스 1-3 적립 조건 8칸 + 큰 금액 조정 문턱). 숫자 칸은 정수·범위({@link PointRules#LIMITS}, 문턱 0~100,000), 켬·끔 칸은 값이 있어야 한다 —
 *       틀린 칸을 모아 400 {@code VALIDATION} {@code fields.<칸>}, 하나라도 틀리면 아무것도 저장하지 않는다.</li>
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
    private final CacheRefresher refresher;

    public PointSettingsService(PointSettingRepository settings, OperatorRepository operators, AuditLogService audit, CacheRefresher refresher) {
        this.settings = settings;
        this.operators = operators;
        this.audit = audit;
        this.refresher = refresher;
    }

    @Transactional(readOnly = true)
    public PointSettingsResponse load() {
        return settings.findById(PointSettingEntity.ROW_ID).map(this::response).orElseGet(() -> PointSettingsResponse.of(PointRules.DEFAULTS, PointLotRules.DEFAULTS,
                PointTierRules.DEFAULTS, null, null));
    }

    /** 검사를 마친 저장 값 — 지급·조건 칸과 큰 금액 조정 문턱 */
    record Wanted(PointRules rules, int adjustLargeThreshold, PointTierRules tiers) {

        Map<String, Object> toMap() {
            Map<String, Object> map = rules.toMap();
            map.put(ADJUST_LARGE_THRESHOLD, adjustLargeThreshold);
            map.putAll(tiers.toMap());
            return map;
        }
    }

    /** 정보 등급 같은 출처 1회 · AI·운영팀 제외 스위치(point-board-tier-contract) */
    static final java.util.List<String> TIER_SWITCHES = java.util.List.of("infoSourceOnce", "excludeAiAndOps");

    static final String ADJUST_LARGE_THRESHOLD = "adjustLargeThreshold";
    /** 큰 금액 조정 문턱 범위(조정 최대값과 같다 — V85 CHECK) */
    static final int[] ADJUST_LARGE_THRESHOLD_LIMIT = {0, 100_000};

    @Transactional
    public PointSettingsResponse save(AdminActor actor, PointSettingsRequest body) {
        Wanted wanted = check(body);
        PointSettingEntity row = settings.findWithLockById(PointSettingEntity.ROW_ID)
                .orElseThrow(() -> new IllegalStateException("point_settings 행이 없다"));
        Map<String, Object> current = row.values().toMap();
        current.put(ADJUST_LARGE_THRESHOLD, row.lotRules().adjustLargeThreshold());
        current.putAll(row.tierRules().toMap());
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
        Instant now = Instant.now();
        row.change(wanted.rules(), actor.operatorId(), now);
        if (after.containsKey(ADJUST_LARGE_THRESHOLD)) {
            row.changeAdjustLargeThreshold(wanted.adjustLargeThreshold(), actor.operatorId(), now);
        }
        if (after.keySet().stream().anyMatch(field -> field.startsWith("boardTiers.") || TIER_SWITCHES.contains(field))) {
            row.changeTiers(wanted.tiers(), actor.operatorId(), now);
        }
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.POINT_SETTINGS_UPDATE, AuditResult.SUCCESS).target("POINT_SETTINGS", "points")
                .with("before", before).with("after", after));
        // 모든 서버의 포인트 정책 사본을 비운다(세대 번호 — 같은 트랜잭션, 이 서버는 커밋 뒤 바로)
        refresher.onSave(CacheSaveSource.POINT_POLICY_SAVE, null, actor.operatorId());
        log.info("포인트 정책을 저장했다 칸={} by operatorId={}", after.keySet(), actor.operatorId());
        return response(row);
    }

    /** 칸마다 정수·범위·켬끔 — 틀린 칸을 모아 400 */
    static Wanted check(PointSettingsRequest body) {
        PointSettingsRequest request = body == null ? PointSettingsRequest.empty() : body;
        Map<String, Number> raw = request.numbers();
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        for (String field : PointRules.FIELDS) {
            Integer value = ranged(raw.get(field), PointRules.LIMITS.get(field), field, fields);
            if (value != null) {
                values.put(field, value);
            }
        }
        Map<String, Boolean> switches = request.switches();
        for (String field : PointRules.BOOLEAN_FIELDS) {
            Boolean value = switches.get(field);
            if (value == null) {
                fields.put(field, new MessageRef("point.settings.boolean"));
            } else {
                values.put(field, value);
            }
        }
        Integer threshold = ranged(raw.get(ADJUST_LARGE_THRESHOLD), ADJUST_LARGE_THRESHOLD_LIMIT, ADJUST_LARGE_THRESHOLD, fields);
        // 게시판 등급 칸(point-board-tier-contract) — 칸 오류 키 boardTiers.<등급>.<칸>. 일반 등급의 쓰면 바로·최소 글자 수는 받지 않는다(전역 칸)
        Map<String, PointSettingsRequest.Tier> rawTiers = request.boardTiers() == null ? Map.of() : request.boardTiers();
        Map<PointBoardTier, PointTierValues> tierValues = new java.util.EnumMap<>(PointBoardTier.class);
        for (PointBoardTier tier : PointBoardTier.values()) {
            PointSettingsRequest.Tier given = rawTiers.get(tier.name());
            Map<String, Number> numbers = given == null ? Map.of() : given.numbers();
            Map<String, Object> one = new LinkedHashMap<>();
            for (String field : PointTierValues.FIELDS) {
                if (tier == PointBoardTier.NORMAL && PointTierRules.NORMAL_FOLLOWS.contains(field)) {
                    one.put(field, 0);
                    continue;
                }
                String key = "boardTiers." + tier.name() + "." + field;
                Integer value = ranged(numbers.get(field), PointTierValues.LIMIT, key, fields);
                if (value != null) {
                    one.put(field, value);
                }
            }
            if (one.size() == PointTierValues.FIELDS.size()) {
                tierValues.put(tier, PointTierValues.of(one));
            }
        }
        Map<String, Boolean> tierSwitches = new LinkedHashMap<>();
        for (String field : TIER_SWITCHES) {
            Boolean value = switches.get(field);
            if (value == null) {
                fields.put(field, new MessageRef("point.settings.boolean"));
            } else {
                tierSwitches.put(field, value);
            }
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        PointTierRules tiers = new PointTierRules(tierValues.get(PointBoardTier.NORMAL), tierValues.get(PointBoardTier.INFO), tierValues.get(PointBoardTier.CORE),
                tierSwitches.get("infoSourceOnce"), tierSwitches.get("excludeAiAndOps"));
        return new Wanted(PointRules.of(values), threshold, tiers);
    }

    /** 정수이고 범위 안이면 그 값, 아니면 칸 오류를 남기고 null */
    private static Integer ranged(Number raw, int[] range, String field, Map<String, MessageRef> fields) {
        Integer value = integerOf(raw);
        if (value == null || value < range[0] || value > range[1]) {
            fields.put(field, new MessageRef("point.settings.range", String.format("%,d", range[0]), String.format("%,d", range[1])));
            return null;
        }
        return value;
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
        return PointSettingsResponse.of(row.values(), row.lotRules(), row.tierRules(), row.getUpdatedAt(), by);
    }
}
