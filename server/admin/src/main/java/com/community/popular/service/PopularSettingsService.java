package com.community.popular.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchRequestQueue;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.popular.dto.request.PopularSettingsRequest;
import com.community.popular.dto.response.PopularLimitView;
import com.community.popular.dto.response.PopularLimitsView;
import com.community.popular.dto.response.PopularOperatorView;
import com.community.popular.dto.response.PopularSettingValuesView;
import com.community.popular.dto.response.PopularSettingsView;
import com.community.popular.entity.PopularSettingEntity;
import com.community.popular.repository.master.PopularSettingRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인기글 설정 — 조회 {@code popular.read}(또는 {@code popular.settings}), 저장 {@code popular.settings}(컨트롤러가 선언). docs/features/popular-highlight-contract.md 「서버 구현 결과」.
 * 값은 {@code popular_settings}(V74, 행 하나)이고 DB 배치 {@code popular-rank}(R__batch_24)·공개 인기글 읽기가 이 행을 읽는다.
 *
 * <ul>
 *   <li><b>검사</b>(11칸 모두 필수, 틀린 칸을 모아 {@code 400 VALIDATION fields.<칸>}): 가중치 추천·댓글 0~100 · 조회 0~10(소수점 아래 3자리까지), 감쇠 지수 0.5~3 · 보정 0~48시간
 *       (2자리까지), 최소 추천·댓글·조회 0~100,000(정수), 한 사람 최대 1~10(정수), {@code excludeStaffBoards} 참/거짓, {@code weekStart} MONDAY · SUNDAY.
 *       <b>세 가중치가 모두 0 이면</b> {@code fields.likeWeight}. 하나라도 틀리면 저장하지 않는다.</li>
 *   <li>바뀐 칸이 없으면 쓰지 않는다(시각·다시 계산도 없음). 바뀌면 같은 트랜잭션에서 순위 다시 계산을 요청하고({@link PopularRecompute}) 감사
 *       {@code POPULAR_SETTINGS_UPDATE}(바뀐 칸만 before/after) 를 남긴다.</li>
 *   <li>미리보기({@code current})는 실시간 순위 스냅샷의 상위 10개 — 저장 직후에는 아직 옛 값으로 계산한 것이다(다시 계산이 끝나면 바뀐다, {@code recomputePending}).</li>
 * </ul>
 */
@Service
public class PopularSettingsService {

    private static final Logger log = LoggerFactory.getLogger(PopularSettingsService.class);

    /** 숫자 칸 하나의 범위 — V74 CHECK 와 같다 */
    record Limit(BigDecimal min, BigDecimal max, int decimals) {

        static Limit of(String min, String max, int decimals) {
            return new Limit(new BigDecimal(min), new BigDecimal(max), decimals);
        }

        PopularLimitView view() {
            return new PopularLimitView(min.doubleValue(), max.doubleValue(), decimals);
        }
    }

    static final Limit LIKE_WEIGHT = Limit.of("0", "100", 3);
    static final Limit COMMENT_WEIGHT = Limit.of("0", "100", 3);
    static final Limit VIEW_WEIGHT = Limit.of("0", "10", 3);
    static final Limit GRAVITY = Limit.of("0.5", "3", 2);
    static final Limit AGE_OFFSET_HOURS = Limit.of("0", "48", 2);
    static final Limit MIN_COUNT = Limit.of("0", "100000", 0);
    static final Limit MAX_PER_AUTHOR = Limit.of("1", "10", 0);

    /** 검사를 통과한 값 */
    record Values(BigDecimal likeWeight, BigDecimal commentWeight, BigDecimal viewWeight, BigDecimal gravity, BigDecimal ageOffsetHours, int minLikes,
                  int minComments, int minViews, boolean excludeStaffBoards, int maxPerAuthor, String weekStart) {

        /** 기본값 — 예전 서버 상수(R__batch_24)와 같다 */
        static final Values DEFAULTS = new Values(new BigDecimal("3"), new BigDecimal("2"), new BigDecimal("0.1"), new BigDecimal("1.5"), new BigDecimal("2"),
                3, 0, 0, true, 2, PopularRules.MONDAY);

        static Values of(PopularSettingEntity row) {
            return new Values(row.getLikeWeight(), row.getCommentWeight(), row.getViewWeight(), row.getGravity(), row.getAgeOffsetHours(), row.getMinLikes(),
                    row.getMinComments(), row.getMinViews(), row.isExcludeStaffBoards(), row.getMaxPerAuthor(), row.getWeekStart());
        }

        /** 감사·비교용 — 소수는 끝의 0 을 뗀 글자(3.000 과 3 이 같게) */
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("likeWeight", plain(likeWeight));
            map.put("commentWeight", plain(commentWeight));
            map.put("viewWeight", plain(viewWeight));
            map.put("gravity", plain(gravity));
            map.put("ageOffsetHours", plain(ageOffsetHours));
            map.put("minLikes", minLikes);
            map.put("minComments", minComments);
            map.put("minViews", minViews);
            map.put("excludeStaffBoards", excludeStaffBoards);
            map.put("maxPerAuthor", maxPerAuthor);
            map.put("weekStart", weekStart);
            return map;
        }

        PopularSettingValuesView view() {
            return new PopularSettingValuesView(likeWeight.doubleValue(), commentWeight.doubleValue(), viewWeight.doubleValue(), gravity.doubleValue(),
                    ageOffsetHours.doubleValue(), minLikes, minComments, minViews, excludeStaffBoards, maxPerAuthor, weekStart);
        }

        private static String plain(BigDecimal value) {
            return value == null ? null : value.stripTrailingZeros().toPlainString();
        }
    }

    private static final PopularLimitsView LIMITS = new PopularLimitsView(LIKE_WEIGHT.view(), COMMENT_WEIGHT.view(), VIEW_WEIGHT.view(), GRAVITY.view(),
            AGE_OFFSET_HOURS.view(), MIN_COUNT.view(), MIN_COUNT.view(), MIN_COUNT.view(), MAX_PER_AUTHOR.view());

    private final PopularSettingRepository settings;
    private final PopularPreview preview;
    private final PopularRecompute recompute;
    private final BatchRequestQueue requests;
    private final OperatorRepository operators;
    private final AuditLogService audit;

    public PopularSettingsService(PopularSettingRepository settings, PopularPreview preview, PopularRecompute recompute, BatchRequestQueue requests,
                                  OperatorRepository operators, AuditLogService audit) {
        this.settings = settings;
        this.preview = preview;
        this.recompute = recompute;
        this.requests = requests;
        this.operators = operators;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PopularSettingsView load() {
        PopularSettingEntity row = settings.findById(PopularSettingEntity.ROW_ID).orElseThrow(() -> new IllegalStateException("popular_settings 행이 없다"));
        return view(row, false);
    }

    @Transactional
    public PopularSettingsView save(AdminActor actor, PopularSettingsRequest body) {
        Values wanted = check(body);
        PopularSettingEntity row = settings.findWithLockById(PopularSettingEntity.ROW_ID)
                .orElseThrow(() -> new IllegalStateException("popular_settings 행이 없다"));
        Map<String, Object> current = Values.of(row).toMap();
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
            return view(row, false);
        }
        row.change(wanted.likeWeight(), wanted.commentWeight(), wanted.viewWeight(), wanted.gravity(), wanted.ageOffsetHours(), wanted.minLikes(),
                wanted.minComments(), wanted.minViews(), wanted.excludeStaffBoards(), wanted.maxPerAuthor(), wanted.weekStart(), actor.operatorId(), Instant.now());
        settings.flush(); // 다시 계산 요청(같은 트랜잭션의 JDBC)보다 설정 행이 먼저 나가게 — 커밋 전에는 어차피 아무도 못 보지만 순서를 분명히 한다
        boolean requested = recompute.request(actor.operatorId());
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.POPULAR_SETTINGS_UPDATE, AuditResult.SUCCESS)
                .target("POPULAR_SETTINGS", "popular").with("before", before).with("after", after).with("recomputeRequested", requested));
        log.info("인기글 설정을 저장했다 칸={} recompute={} by operatorId={}", after.keySet(), requested, actor.operatorId());
        return view(row, requested);
    }

    /** 칸마다 검사 — 틀린 칸을 모아 400 */
    static Values check(PopularSettingsRequest body) {
        PopularSettingsRequest b = body == null ? new PopularSettingsRequest(null, null, null, null, null, null, null, null, null, null, null) : body;
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        BigDecimal likeWeight = ranged(fields, "likeWeight", b.likeWeight(), LIKE_WEIGHT);
        BigDecimal commentWeight = ranged(fields, "commentWeight", b.commentWeight(), COMMENT_WEIGHT);
        BigDecimal viewWeight = ranged(fields, "viewWeight", b.viewWeight(), VIEW_WEIGHT);
        if (likeWeight != null && commentWeight != null && viewWeight != null && likeWeight.signum() == 0 && commentWeight.signum() == 0
                && viewWeight.signum() == 0) {
            fields.put("likeWeight", new MessageRef("popular.settings.weights.allZero"));
        }
        BigDecimal gravity = ranged(fields, "gravity", b.gravity(), GRAVITY);
        BigDecimal ageOffsetHours = ranged(fields, "ageOffsetHours", b.ageOffsetHours(), AGE_OFFSET_HOURS);
        BigDecimal minLikes = ranged(fields, "minLikes", b.minLikes(), MIN_COUNT);
        BigDecimal minComments = ranged(fields, "minComments", b.minComments(), MIN_COUNT);
        BigDecimal minViews = ranged(fields, "minViews", b.minViews(), MIN_COUNT);
        if (b.excludeStaffBoards() == null) {
            fields.put("excludeStaffBoards", new MessageRef("popular.settings.excludeStaffBoards.required"));
        }
        BigDecimal maxPerAuthor = ranged(fields, "maxPerAuthor", b.maxPerAuthor(), MAX_PER_AUTHOR);
        String weekStart = b.weekStart() == null ? null : b.weekStart().strip().toUpperCase(Locale.ROOT);
        if (weekStart == null || !PopularRules.WEEK_STARTS.contains(weekStart)) {
            fields.put("weekStart", new MessageRef("popular.settings.weekStart.invalid"));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return new Values(likeWeight, commentWeight, viewWeight, gravity, ageOffsetHours, minLikes.intValueExact(), minComments.intValueExact(),
                minViews.intValueExact(), b.excludeStaffBoards(), maxPerAuthor.intValueExact(), weekStart);
    }

    /** 범위 안이고 자릿수가 맞으면 그 값(끝의 0 을 뗀), 아니면 칸 오류를 더하고 null */
    private static BigDecimal ranged(Map<String, MessageRef> fields, String field, Number raw, Limit limit) {
        BigDecimal value = decimalOf(raw);
        boolean ok = value != null && value.compareTo(limit.min()) >= 0 && value.compareTo(limit.max()) <= 0
                && Math.max(0, value.stripTrailingZeros().scale()) <= limit.decimals();
        if (!ok) {
            fields.put(field, limit.decimals() == 0
                    ? new MessageRef("popular.settings.range.integer", plain(limit.min()), plain(limit.max()))
                    : new MessageRef("popular.settings.range.decimal", plain(limit.min()), plain(limit.max()), String.valueOf(limit.decimals())));
            return null;
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private static BigDecimal decimalOf(Number raw) {
        if (raw == null) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(raw.toString());
            return value.abs().compareTo(BigDecimal.valueOf(1_000_000_000L)) > 0 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private PopularSettingsView view(PopularSettingEntity row, boolean requested) {
        Values values = Values.of(row);
        PopularPreview.Result current = preview.realtimeTop(values.excludeStaffBoards());
        PopularOperatorView by = row.getUpdatedBy() == null ? null
                : new PopularOperatorView(Long.toString(row.getUpdatedBy()), operators.findById(row.getUpdatedBy()).map(OperatorEntity::getOperatorName).orElse(null));
        return new PopularSettingsView(values.view(), Values.DEFAULTS.view(), LIMITS, PopularRules.WEEK_STARTS, current.items(), current.computedAt(),
                requests.hasOpen(BatchCatalog.POPULAR_RANK.key()), requested, row.getUpdatedAt(), by);
    }
}
