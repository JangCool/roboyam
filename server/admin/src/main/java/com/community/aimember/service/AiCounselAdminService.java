package com.community.aimember.service;

import com.community.aimember.dto.AiCounselAdminDtos;
import com.community.aimember.dto.AiMemberViews;
import com.community.aimember.entity.AiMemberProfileEntity;
import com.community.aimember.entity.AiMemberSettingsEntity;
import com.community.aimember.entity.AiProvider;
import com.community.aimember.entity.AiProviderKeyStatus;
import com.community.aimember.repository.master.AiMemberProfileRepository;
import com.community.aimember.repository.master.AiMemberQueries;
import com.community.aimember.repository.master.AiMemberSettingsRepository;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.EffectiveBoard;
import com.community.board.entity.BoardPrefixEntity;
import com.community.board.repository.master.BoardPrefixRepository;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.cache.service.CacheRefresher;
import com.community.cache.service.CacheSaveSource;
import com.community.counsel.repository.master.CounselQueries;
import com.community.counsel.service.CounselRules;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 연애상담소 설정(ai-love-counsel-contract — 관리자 화면 없이 API 로 켠다). 상담소 말머리({@code board_prefixes.counsel}) · 상담사({@code ai_member_profiles.counselor}·성향) ·
 * 하루 상한(회원당 · 사이트). 읽기 {@code ai-member.read}, 저장 {@code ai-member.manage}. 저장은 감사 {@code AI_COUNSEL_SETTINGS_UPDATE}(대상 {@code AI_MEMBER_SETTINGS}/{@code COUNSEL})
 * 하나와 게시판 목록 캐시 갱신(말머리 응답의 {@code counsel} — 다른 서버도 몇 초 안에)을 남긴다.
 */
@Service
@Transactional(readOnly = true)
public class AiCounselAdminService {

    static final String AUDIT_TARGET_ID = "COUNSEL";

    private final AiMemberSettingsRepository settings;
    private final AiMemberProfileRepository accounts;
    private final AiMemberQueries queries;
    private final AiProviderKeys keys;
    private final AiModelCatalog models;
    private final BoardCatalog catalog;
    private final BoardPrefixRepository prefixes;
    private final CounselQueries counsel;
    private final AuditLogService audit;
    private final CacheRefresher caches;

    public AiCounselAdminService(AiMemberSettingsRepository settings, AiMemberProfileRepository accounts, AiMemberQueries queries, AiProviderKeys keys,
                                 AiModelCatalog models, BoardCatalog catalog, BoardPrefixRepository prefixes, CounselQueries counsel, AuditLogService audit,
                                 CacheRefresher caches) {
        this.settings = settings;
        this.accounts = accounts;
        this.queries = queries;
        this.keys = keys;
        this.models = models;
        this.catalog = catalog;
        this.prefixes = prefixes;
        this.counsel = counsel;
        this.audit = audit;
        this.caches = caches;
    }

    public AiCounselAdminDtos.View view() {
        return view(row(), catalog.snapshot(), accounts.findAllByOrderByIdAsc());
    }

    /**
     * 저장 — 400 {@code VALIDATION}: {@code memberDailyLimit}(0~10) · {@code siteDailyLimit}(0~5000) · {@code counselPrefixIds}(없는 말머리 · 공통 말머리 · AI 가 쓸 수 없는 게시판) ·
     * {@code counselors}(5명 넘음) · {@code counselors[i].aiMemberId}(없거나 겹침) · {@code counselors[i].style}(1~20자).
     */
    @Transactional
    public AiCounselAdminDtos.View save(AdminActor actor, AiCounselAdminDtos.Save body) {
        AiCounselAdminDtos.Save b = body == null ? new AiCounselAdminDtos.Save(null, null, null, null) : body;
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        int member = range(b.memberDailyLimit(), 0, CounselRules.MEMBER_DAILY_MAX, "memberDailyLimit", fields);
        int site = range(b.siteDailyLimit(), 0, CounselRules.SITE_DAILY_MAX, "siteDailyLimit", fields);
        BoardCatalogSnapshot snap = catalog.snapshot();
        Map<Long, BoardPrefixInfo> candidates = candidates(snap);
        Set<Long> wantedPrefixes = new LinkedHashSet<>();
        for (String raw : b.counselPrefixIds() == null ? List.<String>of() : b.counselPrefixIds()) {
            Long id = AiMemberAdminService.parseLong(raw);
            if (id == null || !candidates.containsKey(id)) {
                fields.putIfAbsent("counselPrefixIds", new MessageRef("aiCounsel.prefix.invalid"));
            } else {
                wantedPrefixes.add(id);
            }
        }
        List<AiMemberProfileEntity> all = accounts.findAllByOrderByIdAsc();
        Map<Long, AiMemberProfileEntity> byId = new HashMap<>();
        all.forEach(a -> byId.put(a.getId(), a));
        List<AiCounselAdminDtos.CounselorSave> picked = b.counselors() == null ? List.of() : b.counselors();
        if (picked.size() > CounselRules.COUNSELORS_MAX) {
            fields.put("counselors", new MessageRef("aiCounsel.counselors.max", CounselRules.COUNSELORS_MAX));
        }
        Map<Long, String> wantedCounselors = new LinkedHashMap<>();
        for (int i = 0; i < picked.size(); i++) {
            AiCounselAdminDtos.CounselorSave c = picked.get(i);
            Long id = c == null ? null : AiMemberAdminService.parseLong(c.aiMemberId());
            if (id == null || !byId.containsKey(id) || wantedCounselors.containsKey(id)) {
                fields.put("counselors[" + i + "].aiMemberId", new MessageRef("aiCounsel.counselor.invalid"));
                continue;
            }
            String style = c.style() == null ? "" : c.style().strip();
            if (style.isEmpty() || style.codePointCount(0, style.length()) > CounselRules.STYLE_MAX) {
                fields.put("counselors[" + i + "].style", new MessageRef("aiCounsel.style.length", CounselRules.STYLE_MAX));
                continue;
            }
            wantedCounselors.put(id, style);
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        AiMemberSettingsEntity s = settings.findWithLockById(AiMemberSettingsEntity.ID).orElseThrow(() -> new IllegalStateException("ai_member_settings 행이 없다(V183)"));
        Map<String, Object> before = snapshot(s, snap, all);

        s.saveCounselLimits(new AiMemberSettingsEntity.CounselLimits(member, site), actor.operatorId(), now);
        boolean prefixChanged = false;
        for (BoardPrefixEntity p : prefixes.findAll()) {
            boolean want = wantedPrefixes.contains(p.getId());
            if (p.isCounsel() != want && (want || !p.isCommon())) {
                p.markCounsel(want, now);
                prefixChanged = true;
            }
        }
        for (AiMemberProfileEntity a : all) {
            String style = wantedCounselors.get(a.getId());
            boolean want = style != null;
            if (a.isCounselor() != want || (want && !style.equals(a.getCounselStyle()))) {
                a.counsel(want, style, actor.operatorId(), now);
            }
        }
        accounts.flush();
        prefixes.flush();
        if (prefixChanged) {
            caches.onSave(CacheSaveSource.BOARD_SAVE, null, actor.operatorId()); // 말머리 응답의 counsel — 커밋 직전 세대 번호(다른 서버도 몇 초 안에)
        }
        catalog.invalidate();
        BoardCatalogSnapshot after = catalog.snapshot();
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.AI_COUNSEL_SETTINGS_UPDATE, AuditResult.SUCCESS)
                .target(AiMemberSettingsAdminService.AUDIT_TARGET, AUDIT_TARGET_ID).with("before", before).with("after", snapshot(s, after, all)));
        return view(s, after, all);
    }

    // ── 도우미 ────────────────────────────────────────────────────────────

    /** 상담소로 고를 수 있는 말머리 — AI 가 쓸 수 있는 일반 게시판(켜짐 여부와 상관없이 · 소모임 게시판 아님 · 「항상 막힘」 아님)의 고유 말머리 */
    private static Map<Long, BoardPrefixInfo> candidates(BoardCatalogSnapshot snap) {
        Map<Long, BoardPrefixInfo> out = new LinkedHashMap<>();
        for (EffectiveBoard b : snap.boards()) {
            if (BoardFields.KIND_GROUP.equals(b.kind()) || AiMemberRules.blockedReason(b) != null) {
                continue;
            }
            for (BoardPrefixInfo p : b.prefixes()) {
                if (!p.common()) {
                    out.put(p.id(), p);
                }
            }
        }
        return out;
    }

    private AiCounselAdminDtos.View view(AiMemberSettingsEntity s, BoardCatalogSnapshot snap, List<AiMemberProfileEntity> all) {
        List<AiCounselAdminDtos.Prefix> prefixViews = new ArrayList<>();
        for (EffectiveBoard b : snap.boards()) {
            if (BoardFields.KIND_GROUP.equals(b.kind()) || AiMemberRules.blockedReason(b) != null) {
                continue;
            }
            for (BoardPrefixInfo p : b.prefixes()) {
                if (!p.common()) {
                    prefixViews.add(new AiCounselAdminDtos.Prefix(Long.toString(p.id()), Long.toString(b.id()), b.slug(), b.name(), p.name(), p.active(), p.counsel()));
                }
            }
        }
        Set<AiProvider> usable = keys.usable();
        Map<AiProvider, AiProviderKeyStatus> paused = keys.paused();
        AiModelCatalog.Enabled enabled = models.enabled();
        List<Long> userIds = all.stream().map(AiMemberProfileEntity::getUserId).toList();
        Map<Long, Boolean> restricted = all.isEmpty() ? Map.of() : queries.restricted(userIds);
        Map<Long, String> nicknames = all.isEmpty() ? Map.of() : queries.nicknames(userIds);
        List<AiCounselAdminDtos.Counselor> counselorViews = new ArrayList<>();
        for (AiMemberProfileEntity a : all) {
            String resting = AiMemberDirectory.restingReason(a, s, usable, paused, enabled.allows(a.getProvider(), a.getModel()), restricted.getOrDefault(a.getUserId(), true));
            counselorViews.add(new AiCounselAdminDtos.Counselor(Long.toString(a.getId()), Long.toString(a.getUserId()), nicknames.getOrDefault(a.getUserId(), ""),
                    a.getProvider().name(), a.getModel(), a.isEnabled(), a.isCounselor(), a.getCounselStyle(), resting));
        }
        AiMemberSettingsEntity.CounselLimits limits = s.getCounselLimits();
        long today = counsel.postsSince(CounselRules.counselPrefixIds(snap), null, CounselRules.startOfDay(Instant.now()), null);
        return new AiCounselAdminDtos.View(limits.memberDaily(), limits.siteDaily(), new AiMemberViews.Range(0, CounselRules.MEMBER_DAILY_MAX),
                new AiMemberViews.Range(0, CounselRules.SITE_DAILY_MAX), today, prefixViews, counselorViews, CounselRules.COUNSELORS_MAX, CounselRules.STYLE_MAX);
    }

    private static Map<String, Object> snapshot(AiMemberSettingsEntity s, BoardCatalogSnapshot snap, List<AiMemberProfileEntity> all) {
        Map<String, Object> m = new LinkedHashMap<>();
        AiMemberSettingsEntity.CounselLimits limits = s.getCounselLimits();
        m.put("memberDailyLimit", limits.memberDaily());
        m.put("siteDailyLimit", limits.siteDaily());
        m.put("counselPrefixIds", CounselRules.counselPrefixIds(snap).stream().sorted().map(String::valueOf).toList());
        List<String> counselors = new ArrayList<>();
        for (AiMemberProfileEntity a : all) {
            if (a.isCounselor()) {
                counselors.add(a.getId() + ":" + (a.getCounselStyle() == null ? "" : a.getCounselStyle()));
            }
        }
        m.put("counselors", counselors);
        return m;
    }

    private static int range(Integer raw, int min, int max, String field, Map<String, MessageRef> fields) {
        if (raw == null || raw < min || raw > max) {
            fields.put(field, new MessageRef("aiCounsel.limit.range", min, max));
            return min;
        }
        return raw;
    }

    private AiMemberSettingsEntity row() {
        return settings.findById(AiMemberSettingsEntity.ID).orElseThrow(() -> new IllegalStateException("ai_member_settings 행이 없다(V183)"));
    }
}
