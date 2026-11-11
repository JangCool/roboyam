package com.community.aimember.service;

import com.community.aimember.dto.AiMemberRequests;
import com.community.aimember.dto.AiMemberViews;
import com.community.aimember.entity.AiInstantScope;
import com.community.aimember.entity.AiMemberSettingsEntity;
import com.community.aimember.entity.AiProvider;
import com.community.aimember.repository.master.AiMemberProfileRepository;
import com.community.aimember.repository.master.AiMemberQueries;
import com.community.aimember.repository.master.AiMemberSettingsRepository;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 회원 설정(정책 6·12·13) — 바로 게시(범위 ALL | SELECTED) · 하루 호출 상한 · 회원 글 댓글 허용(기본 꺼짐) · 글 쓰는 때(V191 — 조용한 게시판 기준 일수 ·
 * 글이 잘 올라오는 게시판의 AI 글 간격 · 계정당 하루 최대 새 글) · 전체 정지(바로 반영) · 제공사 API 키.
 * 키는 암호문으로만 두고 응답에는 「설정됨」·바꾼 때·바꾼 사람만 준다. 감사에도 값을 싣지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class AiMemberSettingsAdminService {

    static final String AUDIT_TARGET = "AI_MEMBER_SETTINGS";
    static final int KEY_MIN = 8;
    static final int KEY_MAX = 500;

    private final AiMemberSettingsRepository settings;
    private final AiMemberProfileRepository accounts;
    private final AiMemberQueries queries;
    private final AiProviderKeys keys;
    private final AuditLogService audit;

    public AiMemberSettingsAdminService(AiMemberSettingsRepository settings, AiMemberProfileRepository accounts, AiMemberQueries queries, AiProviderKeys keys,
                                        AuditLogService audit) {
        this.settings = settings;
        this.accounts = accounts;
        this.queries = queries;
        this.keys = keys;
        this.audit = audit;
    }

    public AiMemberViews.Settings view() {
        return view(row());
    }

    @Transactional
    public AiMemberViews.Settings save(AdminActor actor, AiMemberRequests.SettingsSave body) {
        AiMemberRequests.SettingsSave b = body == null ? new AiMemberRequests.SettingsSave(null, null, null, null, null, null, null, null) : body;
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        boolean instant = Boolean.TRUE.equals(b.instantPublish());
        AiInstantScope scope = b.instantScope() == null ? AiInstantScope.ALL : AiMemberAdminService.parseEnum(AiInstantScope.class, b.instantScope());
        if (scope == null) {
            fields.put("instantScope", new MessageRef("aiMember.instantScope.unknown"));
        }
        Set<Long> known = new HashSet<>();
        accounts.findAll().forEach(a -> known.add(a.getId()));
        List<Long> picked = new ArrayList<>();
        for (String raw : b.instantAccountIds() == null ? List.<String>of() : new LinkedHashSet<>(b.instantAccountIds())) {
            Long id = AiMemberAdminService.parseLong(raw);
            if (id == null || !known.contains(id)) {
                fields.put("instantAccountIds", new MessageRef("aiMember.instantAccountIds.unknown"));
            } else {
                picked.add(id);
            }
        }
        if (instant && scope == AiInstantScope.SELECTED && picked.isEmpty() && !fields.containsKey("instantAccountIds")) {
            fields.put("instantAccountIds", new MessageRef("aiMember.instantAccountIds.required"));
        }
        int limit = b.dailyCallLimit() == null ? -1 : b.dailyCallLimit();
        if (limit < AiMemberRules.CALL_LIMIT_MIN || limit > AiMemberRules.CALL_LIMIT_MAX) {
            fields.put("dailyCallLimit", new MessageRef("aiMember.dailyCallLimit.range", AiMemberRules.CALL_LIMIT_MIN, AiMemberRules.CALL_LIMIT_MAX));
        }
        int quiet = range(b.quietDays(), AiMemberRules.QUIET_DAYS_MIN, AiMemberRules.QUIET_DAYS_MAX, "quietDays", "aiMember.quietDays.range", fields);
        int interval = range(b.busyIntervalDays(), AiMemberRules.BUSY_INTERVAL_MIN, AiMemberRules.BUSY_INTERVAL_MAX, "busyIntervalDays", "aiMember.busyIntervalDays.range", fields);
        int dailyPosts = range(b.dailyPostMax(), AiMemberRules.DAILY_POST_MAX_MIN, AiMemberRules.DAILY_POST_MAX_MAX, "dailyPostMax", "aiMember.dailyPostMax.range", fields);
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        AiMemberSettingsEntity s = lockRow();
        Map<String, Object> before = snapshot(s);
        s.save(instant, scope, picked, limit, Boolean.TRUE.equals(b.commentOnMemberPosts()), new AiMemberSettingsEntity.PostTiming(quiet, interval, dailyPosts),
                actor.operatorId(), now());
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.AI_MEMBER_SETTINGS_UPDATE, AuditResult.SUCCESS).target(AUDIT_TARGET, "1")
                .with("before", before).with("after", snapshot(s)));
        return view(s);
    }

    /** 정수 칸 범위 검사 — 빠졌거나 범위 밖이면 그 칸 오류({@code {0}~{1}}) */
    private static int range(Integer raw, int min, int max, String field, String key, Map<String, MessageRef> fields) {
        if (raw == null || raw < min || raw > max) {
            fields.put(field, new MessageRef(key, min, max));
            return min;
        }
        return raw;
    }

    /** 전체 정지 — 저장 줄을 거치지 않고 바로 반영(실행기는 바퀴마다 이 값을 읽는다) */
    @Transactional
    public AiMemberViews.Settings stop(AdminActor actor, Boolean stopped) {
        if (stopped == null) {
            throw ValidationException.of("stopped", new MessageRef("aiMember.stopped.required"));
        }
        AiMemberSettingsEntity s = lockRow();
        boolean from = s.isStopped();
        s.stop(stopped, actor.operatorId(), now());
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.AI_MEMBER_STOP, AuditResult.SUCCESS).target(AUDIT_TARGET, "1")
                .with("from", from).with("to", stopped));
        return view(s);
    }

    /**
     * 제공사 키 저장(쉬는 중이던 제공사는 상태가 OK 로 돌아와 계정들이 다시 돈다 — V189) — 앞뒤 공백을 지우고 {@value #KEY_MIN}~{@value #KEY_MAX}자 · 가운데 공백 없음(아니면 400 {@code fields.key}). 키 값은 응답·감사·로그에 없다.
     *
     * @throws CommonException 모르는 제공사(404 NOT_FOUND)
     */
    @Transactional
    public AiMemberViews.Settings setKey(AdminActor actor, String providerRaw, AiMemberRequests.Key body) {
        AiProvider provider = AiMemberAdminService.parseEnum(AiProvider.class, providerRaw);
        if (provider == null) {
            throw new CommonException(ErrorCode.NOT_FOUND);
        }
        String value = body == null || body.key() == null ? "" : body.key().strip();
        if (value.length() < KEY_MIN || value.length() > KEY_MAX || value.chars().anyMatch(Character::isWhitespace)) {
            throw ValidationException.of("key", new MessageRef("aiMember.key.invalid"));
        }
        AiProviderKeys.KeyState before = keys.states().get(provider);
        boolean replaced = before.configured();
        keys.save(provider, value, actor.operatorId(), now());
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.AI_PROVIDER_KEY_UPDATE, AuditResult.SUCCESS).target(AUDIT_TARGET, provider.name())
                .with("provider", provider.name()).with("replaced", replaced).with("statusBefore", before.status().name())); // 쉬는 중이던 제공사는 저장하면 OK 로 돌아온다
        return view(row());
    }

    private AiMemberViews.Settings view(AiMemberSettingsEntity s) {
        Map<AiProvider, AiProviderKeys.KeyState> states = keys.states();
        Set<Long> operatorIds = new HashSet<>();
        if (s.getStoppedBy() != null) {
            operatorIds.add(s.getStoppedBy());
        }
        states.values().stream().map(AiProviderKeys.KeyState::updatedBy).filter(java.util.Objects::nonNull).forEach(operatorIds::add);
        Map<Long, String> names = queries.operatorNames(operatorIds);
        List<AiMemberViews.KeyState> keyViews = new ArrayList<>();
        for (AiProvider p : AiProvider.values()) {
            AiProviderKeys.KeyState k = states.get(p);
            keyViews.add(new AiMemberViews.KeyState(p.name(), k.configured(), k.updatedAt(), k.updatedBy() == null ? null : names.get(k.updatedBy()), k.status().name(),
                    k.statusDetail(), k.statusAt()));
        }
        return new AiMemberViews.Settings(s.isStopped(), s.getStoppedAt(), s.getStoppedBy() == null ? null : names.get(s.getStoppedBy()), s.isInstantPublish(),
                s.getInstantScope().name(), s.getInstantAccountIds().stream().map(String::valueOf).toList(), s.getDailyCallLimit(),
                queries.callsSince(AiMemberAdminService.startOfToday()), s.isCommentOnMemberPosts(),
                new AiMemberViews.Range(AiMemberRules.FIRST_COMMENTS_MIN, AiMemberRules.FIRST_COMMENTS_MAX),
                new AiMemberViews.Range(AiMemberRules.REPLY_ROUNDS_MIN, AiMemberRules.REPLY_ROUNDS_MAX),
                new AiMemberViews.Range(AiMemberRules.MEMBER_REPLIES_MIN, AiMemberRules.MEMBER_REPLIES_MAX), keyViews, keys.cryptoConfigured(), postTiming(s));
    }

    private static AiMemberViews.PostTiming postTiming(AiMemberSettingsEntity s) {
        AiMemberSettingsEntity.PostTiming t = s.getPostTiming();
        return new AiMemberViews.PostTiming(t.quietDays(), t.busyIntervalDays(), t.dailyPostMax(),
                new AiMemberViews.Range(AiMemberRules.QUIET_DAYS_MIN, AiMemberRules.QUIET_DAYS_MAX),
                new AiMemberViews.Range(AiMemberRules.BUSY_INTERVAL_MIN, AiMemberRules.BUSY_INTERVAL_MAX),
                new AiMemberViews.Range(AiMemberRules.DAILY_POST_MAX_MIN, AiMemberRules.DAILY_POST_MAX_MAX));
    }

    private static Map<String, Object> snapshot(AiMemberSettingsEntity s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("instantPublish", s.isInstantPublish());
        m.put("instantScope", s.getInstantScope().name());
        m.put("instantAccountIds", s.getInstantAccountIds().stream().map(String::valueOf).toList());
        m.put("dailyCallLimit", s.getDailyCallLimit());
        m.put("commentOnMemberPosts", s.isCommentOnMemberPosts());
        AiMemberSettingsEntity.PostTiming t = s.getPostTiming();
        m.put("quietDays", t.quietDays());
        m.put("busyIntervalDays", t.busyIntervalDays());
        m.put("dailyPostMax", t.dailyPostMax());
        return m;
    }

    private AiMemberSettingsEntity row() {
        return settings.findById(AiMemberSettingsEntity.ID).orElseThrow(() -> new IllegalStateException("ai_member_settings 행이 없다(V183)"));
    }

    private AiMemberSettingsEntity lockRow() {
        return settings.findWithLockById(AiMemberSettingsEntity.ID).orElseThrow(() -> new IllegalStateException("ai_member_settings 행이 없다(V183)"));
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
