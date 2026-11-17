package com.community.title.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchRequestQueue;
import com.community.batch.entity.BatchRequestKind;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.service.BoardCatalog;
import com.community.permission.dto.AdminActor;
import com.community.title.batch.TitleAutoGrantJob;
import com.community.title.dto.TitleCondition;
import com.community.title.dto.TitleSpec;
import com.community.title.dto.request.AdminTitleRequest;
import com.community.title.dto.request.AdminTitleSettingsRequest;
import com.community.title.dto.response.AdminTitleSettingsView;
import com.community.title.dto.response.AdminTitleView;
import com.community.title.entity.TitleConditionType;
import com.community.title.entity.TitleEntity;
import com.community.title.entity.TitleMethod;
import com.community.title.entity.TitleMetric;
import com.community.title.entity.TitleReviewer;
import com.community.title.entity.TitleSettingEntity;
import com.community.title.entity.TitleTier;
import com.community.title.exception.TitleException;
import com.community.title.repository.master.TitleQueries;
import com.community.title.repository.master.TitleQueries.Counted;
import com.community.title.repository.master.TitleRepository;
import com.community.title.repository.master.TitleSettingRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 칭호 정의·작가 칭호 설정(member-title-contract 「작가 칭호 설정」·「화면」 관리자 서랍·「구현 결정」 2·8).
 *
 * <ul>
 *   <li>검사(칸 경로 키로 모아 400 {@code VALIDATION}): 이름 공백 뺀 1~6자·등급 이름과 겹치면 {@code name} · 영어 이름 40자 · 그림·칩·받는 방법·심사자 목록 · 매니저 심사면 연결 게시판 필수 ·
 *       조건 최대 10개(세는 조건 1~100,000, 등급 조건은 있는 등급, 게시판은 있는 게시판) · 단계는 셋 다 커지는 차례 · 자동 칭호는 조건이나 단계가 있어야 한다(명예 작가 제외).
 *       다른 칭호와 이름이 같으면 409 {@code TITLE_NAME_TAKEN}, 기본 칭호의 받는 방법을 바꾸면 409 {@code TITLE_BUILTIN_LOCKED}.</li>
 *   <li>저장 시 소급({@code retroactive} — 자동 칭호만): 그 칭호만 자동 판정 배치 「지금 실행」 요청을 넣는다(이미 대기·실행 중인 요청이 있으면 넣지 않는다 — 매일 판정이 따라온다).</li>
 *   <li>작가 칭호 설정: 명예 작가 기준을 내리면 새 기준을 채운 회원에게 같은 트랜잭션에서 바로 준다, 올려도 이미 받은 명예 작가는 그대로.</li>
 *   <li>감사 {@code TITLE_CREATE}·{@code TITLE_UPDATE}(대상 TITLE, 바뀐 칸 before/after)·{@code TITLE_SETTINGS_UPDATE}(대상 TITLE_SETTINGS).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AdminTitleService {

    static final int MAX_CONDITIONS = 10;
    static final int MAX_COUNT = 100_000;

    private final TitleRepository titles;
    private final TitleSettingRepository settingRows;
    private final TitleSettingsReader settings;
    private final TitleQueries queries;
    private final TitleEligibility eligibility;
    private final TitleTexts texts;
    private final TitleGrantService grants;
    private final BoardCatalog catalog;
    private final BatchRequestQueue batchQueue;
    private final AuditLogService audit;
    private final SnowflakeIdGenerator ids;

    public AdminTitleService(TitleRepository titles, TitleSettingRepository settingRows, TitleSettingsReader settings, TitleQueries queries, TitleEligibility eligibility,
                             TitleTexts texts, TitleGrantService grants, BoardCatalog catalog, BatchRequestQueue batchQueue, AuditLogService audit,
                             SnowflakeIdGenerator ids) {
        this.titles = titles;
        this.settingRows = settingRows;
        this.settings = settings;
        this.queries = queries;
        this.eligibility = eligibility;
        this.texts = texts;
        this.grants = grants;
        this.catalog = catalog;
        this.batchQueue = batchQueue;
        this.audit = audit;
        this.ids = ids;
    }

    // ── 목록 ─────────────────────────────────────────────────────────────

    public List<AdminTitleView> list() {
        Map<Long, Long> holders = queries.holders();
        return titles.findAllByOrderBySortOrderAscIdAsc().stream().map(t -> view(t, holders.getOrDefault(t.getId(), 0L))).toList();
    }

    AdminTitleView view(TitleEntity t, long holders) {
        BoardCatalogSnapshot s = catalog.snapshot();
        List<AdminTitleView.ConditionView> conditions = new ArrayList<>();
        for (TitleCondition c : TitleConditions.read(t.getConditions())) {
            Optional<Long> board = TitleEligibility.board(c, s);
            conditions.add(new AdminTitleView.ConditionView(c.type().name(), c.value(), board == null || board.isEmpty() ? null : Long.toString(board.get()),
                    board == null ? c.boardSlug() : null, c.gradeKey(), texts.condition(c)));
        }
        Long board = eligibility.linkedBoard(t);
        String boardName = board == null ? null : s.board(board).map(b -> b.name()).orElse(null);
        return new AdminTitleView(t.getTitleKey(), t.getName(), t.getNameEn(), t.getIcon(), t.getChip(), t.getMethod().name(), t.getReviewer().name(),
                board == null ? null : Long.toString(board), boardName, conditions, t.getTierMetric() == null ? null : t.getTierMetric().name(), t.getTierBronze(),
                t.getTierSilver(), t.getTierGold(), t.isBenefitTrialPriority(), t.isBenefitMagazine(), t.isBenefitAvatarFrame(), t.isDormantHide(), t.isEnabled(),
                t.getSortOrder(), t.isBuiltin(), holders, texts.howTo(t), t.getUpdatedAt());
    }

    // ── 만들기·고치기 ────────────────────────────────────────────────────────

    /** 운영자가 고른 칭호 키 — 소문자·숫자·하이픈 2~32자, 화면 경로와 겹치는 낱말은 쓰지 않는다 */
    static final java.util.regex.Pattern KEY = java.util.regex.Pattern.compile("^[a-z0-9][a-z0-9-]{1,31}$");
    static final java.util.Set<String> RESERVED_KEYS = java.util.Set.of("settings", "grants", "applications", "monthly-candidates");

    @Transactional
    public AdminTitleView create(AdminActor actor, AdminTitleRequest body) {
        String wanted = body == null || body.key() == null || body.key().isBlank() ? null : body.key().strip();
        if (wanted != null && (!KEY.matcher(wanted).matches() || RESERVED_KEYS.contains(wanted))) {
            throw new ValidationException(Map.of("key", new MessageRef("title.key.invalid")));
        }
        TitleSpec spec = spec(body, null);
        requireUniqueName(spec.name(), null);
        if (wanted != null && titles.findByTitleKey(wanted).isPresent()) {
            throw TitleException.keyTaken();
        }
        long id = ids.nextId();
        Instant now = grants.now();
        TitleEntity t = TitleEntity.create(id, wanted == null ? "t" + Long.toString(id, 36) : wanted, spec, actor.operatorId(), now);
        try {
            titles.persist(t);
            titles.flush();
        } catch (DuplicateKeyException e) {
            throw TitleException.nameTaken();
        }
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.TITLE_CREATE, AuditResult.SUCCESS).target("TITLE", t.getTitleKey())
                .with("after", snapshot(t)));
        retro(actor, t, body);
        return view(t, 0);
    }

    /** 고치기 — {@code key} 가 없으면 본문의 {@code key}({@code PUT /api/admin/titles}) */
    @Transactional
    public AdminTitleView update(AdminActor actor, String pathKey, AdminTitleRequest body) {
        String key = pathKey != null ? pathKey : body == null ? null : body.key();
        TitleEntity t = titles.findWithLockByTitleKey(key == null ? "" : key.strip()).orElseThrow(TitleException::notFound);
        TitleSpec spec = spec(body, t);
        if (t.isBuiltin() && spec.method() != t.getMethod()) {
            throw TitleException.builtinLocked();
        }
        requireUniqueName(spec.name(), t.getId());
        Map<String, Object> before = snapshot(t);
        t.change(spec, actor.operatorId(), grants.now());
        try {
            titles.flush();
        } catch (DuplicateKeyException e) {
            throw TitleException.nameTaken();
        }
        Map<String, Object> after = snapshot(t);
        Map<String, Object> from = new LinkedHashMap<>();
        Map<String, Object> to = new LinkedHashMap<>();
        after.forEach((k, v) -> {
            if (!Objects.equals(before.get(k), v)) {
                from.put(k, before.get(k));
                to.put(k, v);
            }
        });
        if (!to.isEmpty()) {
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.TITLE_UPDATE, AuditResult.SUCCESS).target("TITLE", t.getTitleKey())
                    .with("before", from).with("after", to));
        }
        retro(actor, t, body);
        return view(t, queries.holders().getOrDefault(t.getId(), 0L));
    }

    /** 저장 시 소급 — 자동 칭호만, 그 칭호만 배치 「지금 실행」(끝나지 않은 요청이 있으면 넣지 않는다) */
    private void retro(AdminActor actor, TitleEntity t, AdminTitleRequest body) {
        if (body == null || Boolean.FALSE.equals(body.retroactive()) || t.getMethod() != TitleMethod.AUTO || !t.isEnabled()) {
            return;
        }
        if (!batchQueue.hasOpen(BatchCatalog.TITLE_AUTO_GRANT.key())) {
            batchQueue.enqueue(BatchCatalog.TITLE_AUTO_GRANT.key(), BatchRequestKind.NOW, Map.of(TitleAutoGrantJob.PARAM_TITLE, t.getTitleKey()), List.of(), null,
                    actor.operatorId());
        }
    }

    private void requireUniqueName(String name, Long selfId) {
        String norm = normalize(name);
        boolean taken = titles.findAll().stream().anyMatch(t -> !t.getId().equals(selfId) && normalize(t.getName()).equals(norm));
        if (taken) {
            throw TitleException.nameTaken();
        }
    }

    static String normalize(String name) {
        return name == null ? "" : name.replace(" ", "").toLowerCase(Locale.ROOT);
    }

    /** 요청 → 검사를 마친 값(칸 오류는 모아 400) */
    TitleSpec spec(AdminTitleRequest body, TitleEntity current) {
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        AdminTitleRequest b = body == null
                ? new AdminTitleRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null) : body;
        BoardCatalogSnapshot s = catalog.snapshot();

        String name = b.name() == null ? "" : b.name().strip().replaceAll("\\s+", " ");
        int length = name.replace(" ", "").length();
        if (length == 0) {
            errors.put("name", new MessageRef("title.name.required"));
        } else if (length > 6) {
            errors.put("name", new MessageRef("title.name.max"));
        } else if (s.grades().values().stream().anyMatch(g -> normalize(g.label()).equals(normalize(name)))) {
            errors.put("name", new MessageRef("title.name.grade"));
        }
        String nameEn = b.nameEn() == null || b.nameEn().isBlank() ? null : b.nameEn().strip();
        if (nameEn != null && nameEn.length() > 40) {
            errors.put("nameEn", new MessageRef("title.nameEn.max"));
        }
        if (b.icon() == null || !BuiltinTitles.ICONS.contains(b.icon())) {
            errors.put("icon", new MessageRef("title.icon.invalid"));
        }
        if (b.chip() == null || !BuiltinTitles.CHIPS.contains(b.chip())) {
            errors.put("chip", new MessageRef("title.chip.invalid"));
        }
        TitleMethod method = parse(TitleMethod.class, b.method());
        if (method == null) {
            if (current != null && current.isBuiltin() && (b.method() == null || b.method().isBlank())) {
                method = current.getMethod();
            } else {
                errors.put("method", new MessageRef("title.method.invalid"));
            }
        }
        TitleReviewer reviewer = b.reviewer() == null || b.reviewer().isBlank() ? TitleReviewer.OPERATOR : parse(TitleReviewer.class, b.reviewer());
        if (reviewer == null) {
            errors.put("reviewer", new MessageRef("title.reviewer.invalid"));
        }
        Long boardId = null;
        String keptSlug = null;
        if (b.boardId() != null && !b.boardId().isBlank()) {
            boardId = board(b.boardId(), s);
            if (boardId == null) {
                errors.put("boardId", new MessageRef("title.board.invalid"));
            }
        } else if (current != null && current.getBoardId() == null && current.getBoardSlug() != null) {
            // 기본 칭호의 처음 값(slug) — 그 게시판이 이제 있으면 ID 로 굳히고, 아직 없으면 slug 를 남긴다
            boardId = eligibility.linkedBoard(current);
            keptSlug = boardId == null ? current.getBoardSlug() : null;
        }
        if (reviewer == TitleReviewer.OPERATOR_AND_BOARD_MANAGER && boardId == null && keptSlug == null && !errors.containsKey("boardId")) {
            errors.put("boardId", new MessageRef("title.board.required"));
        }

        List<TitleCondition> conditions = new ArrayList<>();
        List<AdminTitleRequest.Condition> raw = b.conditions() == null ? List.of() : b.conditions();
        if (raw.size() > MAX_CONDITIONS) {
            errors.put("conditions", new MessageRef("title.conditions.max", String.valueOf(MAX_CONDITIONS)));
        } else {
            for (int i = 0; i < raw.size(); i++) {
                AdminTitleRequest.Condition c = raw.get(i);
                String path = "conditions[" + i + "]";
                TitleConditionType type = c == null ? null : TitleConditionType.parse(c.type()).orElse(null);
                if (type == null) {
                    errors.put(path + ".type", new MessageRef("title.condition.type.invalid"));
                    continue;
                }
                Integer value = null;
                Long condBoard = null;
                String condSlug = null;
                String gradeKey = null;
                if (type.counted()) {
                    if (c.value() == null || c.value() < 1 || c.value() > MAX_COUNT) {
                        errors.put(path + ".value", new MessageRef("title.condition.value.range", String.valueOf(MAX_COUNT)));
                    }
                    value = c.value();
                }
                if (type == TitleConditionType.POSTS && c.boardId() != null && !c.boardId().isBlank()) {
                    condBoard = board(c.boardId(), s);
                    if (condBoard == null) {
                        errors.put(path + ".boardId", new MessageRef("title.board.invalid"));
                    }
                } else if (type == TitleConditionType.POSTS && c.boardSlug() != null && !c.boardSlug().isBlank()) {
                    // 기본 칭호의 처음 값 — 그 게시판이 이제 있으면 ID 로 굳히고, 없으면 slug 그대로 둔다(설치 뒤 만들어지면 찾는다)
                    condSlug = c.boardSlug().strip();
                    condBoard = s.board(condSlug).map(com.community.board.dto.EffectiveBoard::id).orElse(null);
                    if (condBoard != null) {
                        condSlug = null;
                    }
                }
                if (type == TitleConditionType.GRADE_AT_LEAST) {
                    gradeKey = c.gradeKey() == null ? null : c.gradeKey().strip();
                    if (gradeKey == null || s.gradeLevel(gradeKey) == null) {
                        errors.put(path + ".gradeKey", new MessageRef("title.condition.grade.invalid"));
                    }
                }
                conditions.add(new TitleCondition(type, value, condBoard, condSlug, gradeKey));
            }
        }

        TitleMetric metric = null;
        Integer bronze = null;
        Integer silver = null;
        Integer gold = null;
        if (b.tierMetric() != null && !b.tierMetric().isBlank()) {
            metric = TitleMetric.parse(b.tierMetric()).orElse(null);
            if (metric == null) {
                errors.put("tierMetric", new MessageRef("title.tierMetric.invalid"));
            }
            bronze = b.tierBronze();
            silver = b.tierSilver();
            gold = b.tierGold();
            if (bronze == null || silver == null || gold == null || bronze < 1 || silver <= bronze || gold <= silver || gold > MAX_COUNT) {
                errors.put("tierBronze", new MessageRef("title.tiers.invalid", String.valueOf(MAX_COUNT)));
            }
        }
        boolean honorary = current != null && BuiltinTitles.HONORARY_WRITER.equals(current.getTitleKey());
        if (method == TitleMethod.AUTO && !honorary && metric == null && conditions.isEmpty() && !errors.containsKey("conditions")) {
            errors.put("conditions", new MessageRef("title.conditions.autoEmpty"));
        }
        int sortOrder = b.sortOrder() == null ? (current == null ? 100 : current.getSortOrder()) : b.sortOrder();
        if (sortOrder < 0 || sortOrder > 10_000) {
            errors.put("sortOrder", new MessageRef("title.sortOrder.range"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new TitleSpec(name, nameEn, b.icon(), b.chip(), method, reviewer, boardId, conditions, metric, bronze, silver, gold,
                Boolean.TRUE.equals(b.benefitTrialPriority()), Boolean.TRUE.equals(b.benefitMagazine()), Boolean.TRUE.equals(b.benefitAvatarFrame()),
                Boolean.TRUE.equals(b.dormantHide()), b.enabled() == null || b.enabled(), sortOrder, keptSlug);
    }

    private static Long board(String raw, BoardCatalogSnapshot s) {
        if (!raw.strip().matches("[0-9]{1,19}")) {
            return null;
        }
        long id = Long.parseLong(raw.strip());
        return s.board(id).isPresent() ? id : null;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 감사용 칸 사본 */
    private static Map<String, Object> snapshot(TitleEntity t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", t.getName());
        m.put("nameEn", t.getNameEn());
        m.put("icon", t.getIcon());
        m.put("chip", t.getChip());
        m.put("method", t.getMethod().name());
        m.put("reviewer", t.getReviewer().name());
        m.put("boardId", t.getBoardId() == null ? null : Long.toString(t.getBoardId()));
        m.put("conditions", t.getConditions());
        m.put("tierMetric", t.getTierMetric() == null ? null : t.getTierMetric().name());
        m.put("tiers", t.isTiered() ? t.getTierBronze() + "/" + t.getTierSilver() + "/" + t.getTierGold() : null);
        m.put("benefits", (t.isBenefitTrialPriority() ? "T" : "-") + (t.isBenefitMagazine() ? "M" : "-") + (t.isBenefitAvatarFrame() ? "A" : "-"));
        m.put("dormantHide", t.isDormantHide());
        m.put("enabled", t.isEnabled());
        m.put("sortOrder", t.getSortOrder());
        return m;
    }

    // ── 작가 칭호 설정 ─────────────────────────────────────────────────────

    public AdminTitleSettingsView settings() {
        TitleSettingEntity row = settingRows.findById(TitleSettingEntity.ID).orElse(null);
        TitleSettingsReader.Values v = settings.current();
        return new AdminTitleSettingsView(v.monthlyMax(), v.honoraryCount(), v.reapplyDays(), row == null ? null : row.getUpdatedAt(), 0);
    }

    @Transactional
    public AdminTitleSettingsView saveSettings(AdminActor actor, AdminTitleSettingsRequest body) {
        Map<String, MessageRef> errors = new LinkedHashMap<>();
        Integer monthly = body == null ? null : body.monthlyMax();
        Integer honorary = body == null ? null : body.honoraryCount();
        if (monthly == null || monthly < 1 || monthly > 20) {
            errors.put("monthlyMax", new MessageRef("title.settings.monthlyMax"));
        }
        if (honorary == null || honorary < 1 || honorary > 24) {
            errors.put("honoraryCount", new MessageRef("title.settings.honoraryCount"));
        }
        Integer reapply = body == null ? null : body.reapplyDays();
        if (reapply != null && (reapply < 1 || reapply > 365)) {
            errors.put("reapplyDays", new MessageRef("title.settings.reapplyDays"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        TitleSettingEntity row = settingRows.findWithLockById(TitleSettingEntity.ID).orElseThrow();
        int beforeMonthly = row.getMonthlyMax();
        int beforeHonorary = row.getHonoraryCount();
        int beforeReapply = row.getReapplyDays();
        int nextReapply = reapply == null ? beforeReapply : reapply;
        Instant now = grants.now();
        row.change(monthly, honorary, nextReapply, actor.operatorId(), now);
        settingRows.flush();
        int granted = honorary < beforeHonorary ? retroHonorary(honorary, now) : 0;
        Map<String, Object> from = new LinkedHashMap<>();
        Map<String, Object> to = new LinkedHashMap<>();
        if (beforeMonthly != monthly) {
            from.put("monthlyMax", beforeMonthly);
            to.put("monthlyMax", monthly);
        }
        if (beforeHonorary != honorary) {
            from.put("honoraryCount", beforeHonorary);
            to.put("honoraryCount", honorary);
        }
        if (beforeReapply != nextReapply) {
            from.put("reapplyDays", beforeReapply);
            to.put("reapplyDays", nextReapply);
        }
        if (!to.isEmpty()) {
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.TITLE_SETTINGS_UPDATE, AuditResult.SUCCESS).target("TITLE_SETTINGS", "1")
                    .with("before", from).with("after", to).with("honoraryGranted", granted));
        }
        return new AdminTitleSettingsView(monthly, honorary, nextReapply, now, granted);
    }

    /** 명예 작가 기준을 내렸다 — 새 기준을 채운 회원에게 바로 준다(같은 트랜잭션) */
    private int retroHonorary(int honoraryCount, Instant now) {
        TitleEntity monthly = titles.findByTitleKey(BuiltinTitles.MONTHLY_WRITER).orElse(null);
        if (monthly == null) {
            return 0;
        }
        int granted = 0;
        long after = 0;
        while (true) {
            List<Counted> page = queries.titleCountCandidates(monthly.getId(), after, honoraryCount, 500);
            for (Counted c : page) {
                if (grants.grantHonoraryIfDue(c.userId(), honoraryCount, now) != null) {
                    granted++;
                }
            }
            if (page.size() < 500) {
                return granted;
            }
            after = page.get(page.size() - 1).userId();
        }
    }
}
