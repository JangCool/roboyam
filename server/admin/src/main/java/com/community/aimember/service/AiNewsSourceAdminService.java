package com.community.aimember.service;

import com.community.aimember.dto.AiMemberRequests;
import com.community.aimember.dto.AiMemberViews;
import com.community.aimember.entity.AiMemberSettingsEntity;
import com.community.aimember.entity.AiNewsSourceEntity;
import com.community.aimember.entity.AiNewsSourceKind;
import com.community.aimember.exception.AiNewsSourceNotFoundException;
import com.community.aimember.repository.master.AiMemberSettingsRepository;
import com.community.aimember.repository.master.AiNewsItemRepository;
import com.community.aimember.repository.master.AiNewsSourceRepository;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.batch.core.BatchCatalog;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 회원 설정 「뉴스 출처」(V228 — ai-news-reporter-contract 「관리자 화면」) — 목록 · 더하기 · 고치기 · 지우기 · 지금 수집 · 관심 키워드(V230). 읽기 {@code ai-member.read}, 쓰기 {@code ai-member.manage}(컨트롤러).
 *
 * <ul>
 *   <li>칸 검사: {@code kind} RSS·YOUTUBE·KEYWORD · {@code name} 1~{@value AiNewsSourceEntity#NAME_MAX}자 · {@code address} 1~{@value AiNewsSourceEntity#ADDRESS_MAX}자 —
 *       RSS 는 http·https 주소, YOUTUBE 는 채널 ID 모양({@link AiNewsFeeds#CHANNEL_ID} — 「UC…」, 주소를 넣으면 거절), KEYWORD 는 아무 글자. 출처는 최대 {@value #SOURCES_MAX}개.</li>
 *   <li>감사: 따로 행위를 늘리지 않고 {@code AI_MEMBER_SETTINGS_UPDATE}(대상 {@value #AUDIT_TARGET}/출처 ID, {@code op} CREATE·UPDATE·DELETE·COLLECT · 관심 키워드는 대상 {@value #AUDIT_TARGET}/KEYWORDS, {@code op} KEYWORDS)로 남긴다.</li>
 *   <li>관심 키워드: 낱말마다 앞뒤 공백 정리(안쪽 공백은 하나로) · 쉼표로 나눔 · 빈 낱말 버림 · 중복(대소문자 무시) 제거 뒤 낱말당 1~{@value AiMemberSettingsEntity#NEWS_KEYWORD_MAX}자 ·
 *       최대 {@value AiMemberSettingsEntity#NEWS_KEYWORDS_MAX}개(400 {@code fields.keywords}). 빈 목록 = 거르지 않음.</li>
 *   <li>지금 수집: 트랜잭션 밖에서 {@link AiNewsCollector#collectAll}(배치와 같은 서비스 — 돌고 있으면 {@code busy}).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AiNewsSourceAdminService {

    static final String AUDIT_TARGET = "AI_NEWS_SOURCE";
    static final int SOURCES_MAX = 50;

    private final AiNewsSourceRepository sources;
    private final AiNewsItemRepository items;
    private final AiMemberSettingsRepository settings;
    private final AiNewsCollector collector;
    private final SnowflakeIdGenerator ids;
    private final AuditLogService audit;

    public AiNewsSourceAdminService(AiNewsSourceRepository sources, AiNewsItemRepository items, AiMemberSettingsRepository settings, AiNewsCollector collector,
                                    SnowflakeIdGenerator ids, AuditLogService audit) {
        this.sources = sources;
        this.items = items;
        this.settings = settings;
        this.collector = collector;
        this.ids = ids;
        this.audit = audit;
    }

    public AiMemberViews.NewsSourceList list() {
        List<AiMemberViews.NewsSource> rows = sources.findAllByOrderBySortOrderAscIdAsc().stream().map(AiNewsSourceAdminService::row).toList();
        return new AiMemberViews.NewsSourceList(rows, Arrays.stream(AiNewsSourceKind.values()).map(Enum::name).toList(),
                new AiMemberViews.NewsSourceLimits(AiNewsSourceEntity.NAME_MAX, AiNewsSourceEntity.ADDRESS_MAX, SOURCES_MAX, AiMemberSettingsEntity.NEWS_KEYWORD_MAX,
                        AiMemberSettingsEntity.NEWS_KEYWORDS_MAX), items.countByUsedAtIsNull(),
                BatchCatalog.AI_NEWS_COLLECT.key(), settings.findById(AiMemberSettingsEntity.ID).map(AiMemberSettingsEntity::getNewsCollectKeywords).orElse(List.of()));
    }

    /** 더하기 — 400 {@code VALIDATION}(kind · name · address · sources 개수) */
    @Transactional
    public AiMemberViews.NewsSourceList create(AdminActor actor, AiMemberRequests.NewsSourceSave body) {
        Checked c = check(body);
        List<AiNewsSourceEntity> all = sources.findAllByOrderBySortOrderAscIdAsc();
        if (all.size() >= SOURCES_MAX) {
            throw ValidationException.of("sources", new MessageRef("aiMember.newsSource.max", SOURCES_MAX));
        }
        int order = all.stream().mapToInt(AiNewsSourceEntity::getSortOrder).max().orElse(0) + 1;
        AiNewsSourceEntity e = AiNewsSourceEntity.create(ids.nextId(), c.kind(), c.name(), c.address(), c.enabled(), order, actor.operatorId(), now());
        sources.persist(e);
        record(actor, e.getId(), "CREATE", null, snapshot(e));
        return list();
    }

    /** 고치기 — 404 {@code AI_NEWS_SOURCE_NOT_FOUND} · 400 {@code VALIDATION} */
    @Transactional
    public AiMemberViews.NewsSourceList save(AdminActor actor, String id, AiMemberRequests.NewsSourceSave body) {
        AiNewsSourceEntity e = find(id);
        Checked c = check(body);
        Map<String, Object> before = snapshot(e);
        e.change(c.kind(), c.name(), c.address(), c.enabled(), actor.operatorId(), now());
        record(actor, e.getId(), "UPDATE", before, snapshot(e));
        return list();
    }

    /** 지우기 — 그 출처의 수집 항목도 지운다(FK CASCADE). 이미 쓴 생성 줄은 그대로. 404 {@code AI_NEWS_SOURCE_NOT_FOUND} */
    @Transactional
    public AiMemberViews.NewsSourceList delete(AdminActor actor, String id) {
        AiNewsSourceEntity e = find(id);
        Map<String, Object> before = snapshot(e);
        sources.delete(e);
        sources.flush();
        record(actor, e.getId(), "DELETE", before, null);
        return list();
    }

    /** 지금 수집 — 트랜잭션 밖(외부 HTTP). 결과와 수집 뒤 목록 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AiMemberViews.NewsCollectResult collect(AdminActor actor) {
        AiNewsCollector.Result r = collector.collectAll();
        if (!r.busy()) {
            audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.AI_MEMBER_SETTINGS_UPDATE, AuditResult.SUCCESS).target(AUDIT_TARGET, "ALL")
                    .with("op", "COLLECT").with("sources", r.sources()).with("failed", r.failed()).with("added", r.added()).with("deleted", r.deleted()));
        }
        return new AiMemberViews.NewsCollectResult(r.sources(), r.failed(), r.added(), r.deleted(), r.busy(), list());
    }

    /** 관심 키워드 저장(V230) — 400 {@code VALIDATION}(keywords). 바뀐 게 없어도 저장·감사한다 */
    @Transactional
    public AiMemberViews.NewsSourceList saveKeywords(AdminActor actor, AiMemberRequests.NewsKeywordsSave body) {
        List<String> next = checkKeywords(body == null ? null : body.keywords());
        AiMemberSettingsEntity row = settings.findWithLockById(AiMemberSettingsEntity.ID).orElseThrow(() -> new IllegalStateException("ai_member_settings 행이 없다(V183)"));
        List<String> before = row.getNewsCollectKeywords();
        row.saveNewsCollectKeywords(next);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.AI_MEMBER_SETTINGS_UPDATE, AuditResult.SUCCESS).target(AUDIT_TARGET, "KEYWORDS")
                .with("op", "KEYWORDS").with("before", before).with("after", next));
        return list();
    }

    // ── 검사 ──────────────────────────────────────────────────────────────

    /** 관심 키워드 정리·검사 — 목록이 없으면(null) 400 */
    static List<String> checkKeywords(List<String> raw) {
        if (raw == null) {
            throw ValidationException.of("keywords", new MessageRef("aiMember.newsKeywords.required"));
        }
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            for (String part : entry.split(",")) {
                String k = part.replaceAll("\\s+", " ").strip();
                if (k.isEmpty()) {
                    continue;
                }
                if (k.codePointCount(0, k.length()) > AiMemberSettingsEntity.NEWS_KEYWORD_MAX) {
                    throw ValidationException.of("keywords", new MessageRef("aiMember.newsKeywords.length", AiMemberSettingsEntity.NEWS_KEYWORD_MAX));
                }
                if (seen.add(k.toLowerCase(Locale.ROOT))) {
                    out.add(k);
                }
            }
        }
        if (out.size() > AiMemberSettingsEntity.NEWS_KEYWORDS_MAX) {
            throw ValidationException.of("keywords", new MessageRef("aiMember.newsKeywords.max", AiMemberSettingsEntity.NEWS_KEYWORDS_MAX));
        }
        return out;
    }

    private record Checked(AiNewsSourceKind kind, String name, String address, boolean enabled) {
    }

    private static Checked check(AiMemberRequests.NewsSourceSave body) {
        AiMemberRequests.NewsSourceSave b = body == null ? new AiMemberRequests.NewsSourceSave(null, null, null, null) : body;
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        AiNewsSourceKind kind = AiMemberAdminService.parseEnum(AiNewsSourceKind.class, b.kind());
        if (kind == null) {
            fields.put("kind", new MessageRef("aiMember.newsSource.kind"));
        }
        String name = b.name() == null ? "" : b.name().replaceAll("\\s+", " ").strip();
        if (name.isEmpty()) {
            fields.put("name", new MessageRef("aiMember.newsSource.name.required"));
        } else if (name.codePointCount(0, name.length()) > AiNewsSourceEntity.NAME_MAX) {
            fields.put("name", new MessageRef("aiMember.newsSource.name.max", AiNewsSourceEntity.NAME_MAX));
        }
        String address = b.address() == null ? "" : b.address().strip();
        if (address.isEmpty()) {
            fields.put("address", new MessageRef("aiMember.newsSource.address.required"));
        } else if (address.codePointCount(0, address.length()) > AiNewsSourceEntity.ADDRESS_MAX) {
            fields.put("address", new MessageRef("aiMember.newsSource.address.max", AiNewsSourceEntity.ADDRESS_MAX));
        } else if (kind == AiNewsSourceKind.RSS && !AiNewsFeeds.httpUrl(address)) {
            fields.put("address", new MessageRef("aiMember.newsSource.address.url"));
        } else if (kind == AiNewsSourceKind.YOUTUBE && !AiNewsFeeds.CHANNEL_ID.matcher(address).matches()) {
            fields.put("address", new MessageRef("aiMember.newsSource.address.channel"));
        } else if (kind == AiNewsSourceKind.KEYWORD) {
            address = address.replaceAll("\\s+", " ");
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return new Checked(kind, name, address, b.enabled() == null || b.enabled());
    }

    private AiNewsSourceEntity find(String id) {
        Long key = AiMemberAdminService.parseLong(id);
        return key == null ? throwNotFound() : sources.findById(key).orElseThrow(AiNewsSourceNotFoundException::new);
    }

    private static AiNewsSourceEntity throwNotFound() {
        throw new AiNewsSourceNotFoundException();
    }

    private static AiMemberViews.NewsSource row(AiNewsSourceEntity e) {
        return new AiMemberViews.NewsSource(Long.toString(e.getId()), e.getKind().name(), e.getName(), e.getAddress(), AiNewsFeeds.feedUrl(e.getKind(), e.getAddress()),
                e.isEnabled(), e.getLastFetchedAt(), e.getLastItemCount(), e.getLastError(), e.getLastErrorAt(), e.getUpdatedAt());
    }

    private static Map<String, Object> snapshot(AiNewsSourceEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", e.getKind().name());
        m.put("name", e.getName());
        m.put("address", e.getAddress());
        m.put("enabled", e.isEnabled());
        return m;
    }

    private void record(AdminActor actor, long id, String op, Map<String, Object> before, Map<String, Object> after) {
        AuditEvent ev = AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.AI_MEMBER_SETTINGS_UPDATE, AuditResult.SUCCESS).target(AUDIT_TARGET, Long.toString(id))
                .with("op", op);
        if (before != null) {
            ev = ev.with("before", before);
        }
        if (after != null) {
            ev = ev.with("after", after);
        }
        audit.record(ev);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
