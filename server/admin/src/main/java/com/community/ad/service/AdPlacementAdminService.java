package com.community.ad.service;

import com.community.ad.dto.AdPlacementAdmin;
import com.community.ad.dto.AdPlacementHistory;
import com.community.ad.dto.AdPlacements;
import com.community.ad.entity.AdPlacementHistoryEntity;
import com.community.ad.repository.master.AdPlacementHistoryRepository;
import com.community.ad.repository.master.AdPlacementHistoryRepositoryQDSL;
import com.community.ad.repository.master.AdPlacementStore;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 광고 노출 자리 설정(admin-web {@code /ads/placements}, docs/features/ad-contract.md 「공개 노출 목업」 확정 2026-11-01) — 조회 {@code ad.read} · 저장 {@code ad.settings}
 * (권한은 컨트롤러). 저장은 한 행(V118 {@code ad_placement_settings})을 잠가 덮고, 바뀐 칸만 감사 {@code AD_PLACEMENT_UPDATE} before/after 로 남긴다(처음 저장이면 기본값에서 —
 * 바뀐 칸이 없어도 저장한다). 공개 사이트는 같은 행을 읽는다(공개 광고 목록 사본과 달리 요청마다 읽는다).
 *
 * <ul>
 *   <li>네 묶음({@code postInline}·{@code wings}·{@code boardList}·{@code adult}) 모두 필요 — 빠지면 400 {@code fields.<묶음>}. {@code adult.enabled} 는 「19금 게시판에도
 *       광고 보이기」(사용자 결정 2026-11-01 2차, 기본 끔).</li>
 *   <li>켜기 칸(불리언)이 비면 {@code fields.<묶음>.<칸>}(가정 — 칸을 빠뜨린 요청을 기본값으로 메우지 않는다).</li>
 *   <li>{@code postInline.adType} TEXT_LINE·STRIP_BANNER·SQUARE_BANNER · {@code postInline.position} BETWEEN_CARDS·AFTER_BODY · {@code wings.screens} HOME·PAGE·BOARD·POST 중
 *       0~4개(겹치면 하나로, 보일 차례로 저장 — 비어도 된다: 가정) · {@code boardList.after} 1~20.</li>
 *   <li><b>변경 이력</b>(V120 {@code ad_placement_history}, 사용자 지시 2026-11-01 「변경 이력 관리해.」): 감사와 같은 바뀐 칸 계산으로 저장과 같은 트랜잭션에 한 줄(출처
 *       {@code ADMIN}). 처음 저장(행 없음)은 {@code CREATE} — 메일 서버 설정 이력과 같게 아홉 칸 모두 before null(감사는 기본값에서 바뀐 칸만 · {@code fromDefaults}).
 *       있던 행에 바뀐 칸이 없으면 이력도 감사도 없다. 조회는 메일 서버 설정 이력과 같은 쪽 규칙(기본 20 · 최대 100 · 틀리면 400 {@code fields.page}·{@code fields.size}).</li>
 * </ul>
 */
@Service
public class AdPlacementAdminService {

    static final String AUDIT_TARGET = "AD_PLACEMENTS";
    static final int HISTORY_MAX_SIZE = 100;
    static final int HISTORY_DEFAULT_SIZE = 20;
    /** 이력의 칸 이름 화면 i18n 키 앞부분 — 메일 서버 · 소셜 로그인 설정 이력과 같다 */
    private static final String LABEL_KEY_PREFIX = "settingHistory.field.";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final AdPlacementStore store;
    private final AuditLogService audit;
    private final AdPlacementHistoryRepository history;
    private final AdPlacementHistoryRepositoryQDSL historyQueries;
    private final SnowflakeIdGenerator ids;
    private final Messages messages;

    public AdPlacementAdminService(AdPlacementStore store, AuditLogService audit, AdPlacementHistoryRepository history,
                                   AdPlacementHistoryRepositoryQDSL historyQueries, SnowflakeIdGenerator ids, Messages messages) {
        this.store = store;
        this.audit = audit;
        this.history = history;
        this.historyQueries = historyQueries;
        this.ids = ids;
        this.messages = messages;
    }

    @Transactional(readOnly = true)
    public AdPlacementAdmin.View get() {
        return view(store.find().orElse(null));
    }

    @Transactional
    public AdPlacementAdmin.View save(AdminActor actor, AdPlacementAdmin.Save body) {
        AdPlacements next = validate(body);
        AdPlacementStore.Stored was = store.lock().orElse(null);
        AdPlacements before = was == null ? AdPlacements.DEFAULTS : was.value();
        int beforeRate = was == null ? AdPlacementStore.DEFAULT_STOP_FEE_RATE : was.stopFeeRate();
        // 환불 묶음(V127)은 선택 — 빠지면 지금 수수료율 그대로
        int nextRate = body.refund() == null ? beforeRate : body.refund().stopFeeRate();
        Map<String, Object> from = new LinkedHashMap<>();
        Map<String, Object> to = new LinkedHashMap<>();
        Map<String, Object> a = flat(before, beforeRate);
        Map<String, Object> b = flat(next, nextRate);
        for (String key : a.keySet()) {
            if (!Objects.equals(a.get(key), b.get(key))) {
                from.put(key, a.get(key));
                to.put(key, b.get(key));
            }
        }
        if (was != null && to.isEmpty()) {
            return get();
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS); // DB(timestamptz) 정밀도 — 이력 시각과 저장 시각이 같게
        store.save(next, nextRate, actor.operatorId(), now);
        // 이력 — 감사와 같은 계산(from/to). 처음 저장은 메일 서버 설정 이력처럼 모든 칸을 「없음 → 값」으로
        List<AdPlacementHistory.Change> changes = new ArrayList<>();
        if (was == null) {
            b.forEach((key, value) -> changes.add(new AdPlacementHistory.Change(key, null, text(value))));
        } else {
            to.forEach((key, value) -> changes.add(new AdPlacementHistory.Change(key, text(from.get(key)), text(value))));
        }
        history.persist(AdPlacementHistoryEntity.create(ids.nextId(), was == null ? AdPlacementHistoryEntity.ACTION_CREATE : AdPlacementHistoryEntity.ACTION_UPDATE,
                AdPlacementHistoryEntity.SOURCE_ADMIN, actor.operatorId(), now, MAPPER.writeValueAsString(changes)));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", from);
        detail.put("after", to);
        if (was == null) {
            detail.put("fromDefaults", true);
        }
        audit.record(new AuditEvent(AuditActorType.OPERATOR, actor.operatorId(), AuditActions.AD_PLACEMENT_UPDATE, AuditResult.SUCCESS, AUDIT_TARGET, "1", actor.ip(),
                detail));
        return get();
    }

    @Transactional(readOnly = true)
    public AdPlacementHistory.Page history(String page, String size) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        MessageRef range = new MessageRef("ad.placement.history.page.range", String.valueOf(HISTORY_MAX_SIZE));
        Integer pageNumber = integer(page, 0, 0, Integer.MAX_VALUE / HISTORY_MAX_SIZE);
        if (pageNumber == null) {
            fields.put("page", range);
        }
        Integer pageSize = integer(size, HISTORY_DEFAULT_SIZE, 1, HISTORY_MAX_SIZE);
        if (pageSize == null) {
            fields.put("size", range);
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        long total = historyQueries.count();
        List<AdPlacementHistory.Item> items = new ArrayList<>();
        if (total > 0 && (long) pageNumber * pageSize < total) {
            String deleted = messages.get(new MessageRef("ad.placement.history.operatorDeleted"));
            for (AdPlacementHistory.Row row : historyQueries.page(pageNumber, pageSize)) {
                items.add(item(row, deleted));
            }
        }
        return new AdPlacementHistory.Page(items, total, pageNumber, pageSize);
    }

    /** 비었으면 기본값, 정수가 아니거나 범위 밖이면 null(메일 서버 설정 이력과 같은 규칙) */
    private static Integer integer(String raw, int fallback, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.strip());
            return value < min || value > max ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static AdPlacementHistory.Item item(AdPlacementHistory.Row row, String deletedName) {
        AdPlacementHistoryEntity entity = row.history();
        boolean deleted = row.operatorName() == null;
        List<AdPlacementHistory.ChangeView> changes = parse(entity.getChanges()).stream()
                .map(change -> new AdPlacementHistory.ChangeView(change.field(), LABEL_KEY_PREFIX + change.field(), change.before(), change.after()))
                .toList();
        return new AdPlacementHistory.Item(entity.getId().toString(), entity.getAction(), entity.getSource(),
                entity.getOperatorId() == null ? null : entity.getOperatorId().toString(), deleted ? deletedName : row.operatorName(), deleted,
                entity.getChangedAt(), changes);
    }

    /** 이력의 {@code changes} JSON 을 읽는다. 읽을 수 없으면 빈 목록(옛 줄의 모양이 달라도 조회가 멈추면 안 된다) */
    private static List<AdPlacementHistory.Change> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(MAPPER.readValue(json, AdPlacementHistory.Change[].class));
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** 이력 값 글자 — 켜기 true/false · 이름 그대로 · 화면 목록은 쉼표 이음(빈 목록은 빈 글자) · 숫자 글자 */
    private static String text(Object value) {
        if (value instanceof List<?> list) {
            return String.join(",", list.stream().map(String::valueOf).toList());
        }
        return String.valueOf(value);
    }

    private static AdPlacementAdmin.View view(AdPlacementStore.Stored stored) {
        AdPlacements p = stored == null ? AdPlacements.DEFAULTS : stored.value();
        return new AdPlacementAdmin.View(p.postInline(), p.wings(), p.boardList(), p.adult(), stored == null ? null : stored.updatedAt().toString(),
                stored == null ? null : new AdPlacementAdmin.Operator(Long.toString(stored.updatedBy()), stored.updatedByName()),
                new AdPlacementAdmin.RefundView(stored == null ? AdPlacementStore.DEFAULT_STOP_FEE_RATE : stored.stopFeeRate()));
    }

    private static AdPlacements validate(AdPlacementAdmin.Save body) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        MessageRef required = new MessageRef("ad.placement.required");
        MessageRef option = new MessageRef("ad.placement.option");
        AdPlacementAdmin.PostInline pi = body == null ? null : body.postInline();
        AdPlacementAdmin.Wings w = body == null ? null : body.wings();
        AdPlacementAdmin.BoardList bl = body == null ? null : body.boardList();
        AdPlacementAdmin.Adult ad = body == null ? null : body.adult();
        if (pi == null) {
            fields.put("postInline", required);
        } else {
            if (pi.enabled() == null) {
                fields.put("postInline.enabled", required);
            }
            if (pi.adType() == null || !AdPlacements.INLINE_TYPES.contains(pi.adType())) {
                fields.put("postInline.adType", option);
            }
            if (pi.position() == null || !AdPlacements.INLINE_POSITIONS.contains(pi.position())) {
                fields.put("postInline.position", option);
            }
        }
        List<String> screens = null;
        if (w == null) {
            fields.put("wings", required);
        } else {
            if (w.left() == null) {
                fields.put("wings.left", required);
            }
            if (w.right() == null) {
                fields.put("wings.right", required);
            }
            if (w.screens() == null || w.screens().stream().anyMatch(s -> s == null || !AdPlacements.WING_SCREENS.contains(s))) {
                fields.put("wings.screens", option);
            } else {
                Set<String> picked = new LinkedHashSet<>(w.screens());
                screens = AdPlacements.WING_SCREENS.stream().filter(picked::contains).toList();
            }
        }
        if (bl == null) {
            fields.put("boardList", required);
        } else {
            if (bl.enabled() == null) {
                fields.put("boardList.enabled", required);
            }
            if (bl.after() == null || bl.after() < AdPlacements.AFTER_MIN || bl.after() > AdPlacements.AFTER_MAX) {
                fields.put("boardList.after", new MessageRef("ad.placement.after", String.valueOf(AdPlacements.AFTER_MIN), String.valueOf(AdPlacements.AFTER_MAX)));
            }
        }
        if (ad == null) {
            fields.put("adult", required);
        } else if (ad.enabled() == null) {
            fields.put("adult.enabled", required);
        }
        AdPlacementAdmin.Refund rf = body == null ? null : body.refund();
        if (rf != null && (rf.stopFeeRate() == null || rf.stopFeeRate() < AdPlacementStore.STOP_FEE_RATE_MIN || rf.stopFeeRate() > AdPlacementStore.STOP_FEE_RATE_MAX)) {
            fields.put("refund.stopFeeRate", new MessageRef("ad.placement.stopFeeRate", String.valueOf(AdPlacementStore.STOP_FEE_RATE_MIN),
                    String.valueOf(AdPlacementStore.STOP_FEE_RATE_MAX)));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return new AdPlacements(new AdPlacements.PostInline(pi.enabled(), pi.adType(), pi.position()), new AdPlacements.Wings(w.left(), w.right(), screens),
                new AdPlacements.BoardList(bl.enabled(), bl.after()), new AdPlacements.Adult(ad.enabled()));
    }

    /** 감사·이력 비교용 칸 이름 → 값({@link AdPlacementHistory#FIELDS} 차례) — 끝에 V127 {@code refund.stopFeeRate} */
    private static Map<String, Object> flat(AdPlacements p, int stopFeeRate) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("postInline.enabled", p.postInline().enabled());
        out.put("postInline.adType", p.postInline().adType());
        out.put("postInline.position", p.postInline().position());
        out.put("wings.left", p.wings().left());
        out.put("wings.right", p.wings().right());
        out.put("wings.screens", p.wings().screens());
        out.put("boardList.enabled", p.boardList().enabled());
        out.put("boardList.after", p.boardList().after());
        out.put("adult.enabled", p.adult().enabled());
        out.put("refund.stopFeeRate", stopFeeRate);
        return out;
    }
}
