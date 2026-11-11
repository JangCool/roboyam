package com.community.aimember.service;

import com.community.aimember.dto.AiMemberRequests;
import com.community.aimember.dto.AiMemberViews;
import com.community.aimember.entity.AiMemberProfileEntity;
import com.community.aimember.entity.AiPromptSettingsEntity;
import com.community.aimember.exception.AiMemberNotFoundException;
import com.community.aimember.llm.AiPromptConfig;
import com.community.aimember.llm.AiPrompts;
import com.community.aimember.repository.master.AiMemberProfileRepository;
import com.community.aimember.repository.master.AiMemberQueries;
import com.community.aimember.repository.master.AiPromptSettingsRepository;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 회원 프롬프트 관리(관리자 「AI 회원 › 프롬프트」 — ai-members-contract 「뉴스 글·프롬프트 관리 (2026-11-11)」, 표 {@code ai_prompt_settings} V190).
 *
 * <ul>
 *   <li>지시문 일곱 칸(공통·새 글·뉴스 글·첫 댓글·답글 · 강아지 소리·고양이 소리 — V191) — 비었거나 기본값과 같으면 NULL(= 코드 기본값 {@link AiPrompts}). 분위기 목록도 기본과 같으면 NULL.</li>
 *   <li>고정 규칙·출력 형식·인용 블록·최근 제목은 고칠 수 없다(서버가 늘 붙인다 — 화면에는 고정 규칙만 읽기 전용으로).</li>
 *   <li>저장은 감사 {@code AI_PROMPT_UPDATE}(대상 {@code AI_MEMBER_SETTINGS}·{@code PROMPTS}) — 감사 상세 한도(4000자) 때문에 지시문은 「기본값 / 직접(글자 수)」만 싣는다.</li>
 *   <li>미리 보기는 저장 전 화면 값으로 실제 보낼 메시지를 만든다(제공사 호출 없음 · 감사 없음). 뉴스 글은 지금 소재 후보가 있으면 그것으로, 없으면 「소재 없음」.</li>
 * </ul>
 * 실행기는 바퀴마다 이 표를 다시 읽으므로 저장하면 다음 바퀴부터 쓰인다.
 */
@Service
@Transactional(readOnly = true)
public class AiPromptAdminService {

    static final String AUDIT_TARGET_ID = "PROMPTS";
    /** 미리 보기의 예시 글(첫 댓글·답글) — 실제로는 다른 AI 회원의 글·댓글 본문이 들어간다 */
    static final String SAMPLE_POST = "제목: (예시) 오늘 처음으로 로봇 청소기가 문턱을 넘었어요\n미리 보기용 예시 글이에요. 실제로는 다른 AI 회원이 쓴 글의 제목과 본문이 여기에 들어가요.";
    static final String SAMPLE_COMMENT = "(예시) 저도 비슷한 경험이 있어요! 문턱 높이가 몇 cm 였나요?";

    private final AiPromptSettingsRepository settings;
    private final AiMemberProfileRepository accounts;
    private final AiMemberQueries queries;
    private final AiNewsSources news;
    private final BoardCatalog catalog;
    private final AuditLogService audit;

    public AiPromptAdminService(AiPromptSettingsRepository settings, AiMemberProfileRepository accounts, AiMemberQueries queries, AiNewsSources news,
                                BoardCatalog catalog, AuditLogService audit) {
        this.settings = settings;
        this.accounts = accounts;
        this.queries = queries;
        this.news = news;
        this.catalog = catalog;
        this.audit = audit;
    }

    public AiMemberViews.PromptSettings view() {
        return view(row());
    }

    /** 저장 — 400 {@code VALIDATION}(common · post · news · comment · reply · dog · cat · moodChance · moods · moods[i].name · moods[i].prompt · newsRatio · newsKeyword · newsDays · paragraphSentences) */
    @Transactional
    public AiMemberViews.PromptSettings save(AdminActor actor, AiMemberRequests.PromptSave body) {
        AiPromptSettingsEntity.Values values = validate(body);
        AiPromptSettingsEntity s = settings.findWithLockById(AiPromptSettingsEntity.ID)
                .orElseThrow(() -> new IllegalStateException("ai_prompt_settings 행이 없다(V190)"));
        Map<String, Object> before = summary(s.values());
        s.save(values, actor.operatorId(), now());
        Map<String, Object> after = summary(s.values());
        List<String> changed = before.keySet().stream().filter(k -> !Objects.equals(before.get(k), after.get(k))).toList();
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.AI_PROMPT_UPDATE, AuditResult.SUCCESS)
                .target(AiMemberSettingsAdminService.AUDIT_TARGET, AUDIT_TARGET_ID).with("changed", changed).with("before", before).with("after", after));
        return view(s);
    }

    /**
     * 미리 보기 — 404 {@code AI_MEMBER_NOT_FOUND} · 400 {@code VALIDATION}(aiMemberId · kind · mood · 저장과 같은 칸 {@code settings.*}).
     */
    public AiMemberViews.PromptPreview preview(AiMemberRequests.PromptPreview body) {
        AiMemberRequests.PromptPreview b = body == null ? new AiMemberRequests.PromptPreview(null, null, null, null) : body;
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        Long accountId = AiMemberAdminService.parseLong(b.aiMemberId());
        if (accountId == null) {
            fields.put("aiMemberId", new MessageRef("aiMember.preview.aiMemberId"));
        }
        AiPrompts.Kind kind = AiMemberAdminService.parseEnum(AiPrompts.Kind.class, b.kind());
        if (kind == null) {
            fields.put("kind", new MessageRef("aiMember.preview.kind"));
        }
        AiPromptSettingsEntity.Values values;
        try {
            values = validate(b.settings() == null ? request(row().values()) : b.settings());
        } catch (ValidationException e) {
            e.getFields().forEach((k, v) -> fields.put("settings." + k, v));
            values = null;
        }
        AiPromptConfig config = values == null ? null : config(values);
        AiPromptConfig.Mood mood = null;
        if (config != null && b.mood() != null) {
            if (b.mood() < 0 || b.mood() >= config.moods().size()) {
                fields.put("mood", new MessageRef("aiMember.preview.mood"));
            } else {
                mood = config.moods().get(b.mood());
            }
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        AiMemberProfileEntity a = accounts.findById(accountId).orElseThrow(AiMemberNotFoundException::new);
        String voice = a.getVoice().name();
        if (a.getVoice().animal()) {
            mood = null; // 동물 소리 계정은 분위기를 넣지 않는다(실행기와 같다)
        }
        AiPrompts.Place place = placeOf(a);
        List<String> titles = queries.recentTitles(a.getId(), 10);
        AiPrompts.Ask ask;
        AiMemberViews.PromptSource source = null;
        boolean sample = false;
        switch (kind) {
            case POST -> ask = AiPrompts.Ask.post(titles);
            case NEWS -> {
                if (a.getVoice().animal()) {
                    // 동물 소리 계정은 뉴스 글을 쓰지 않는다 — 실행기처럼 새 글로 만든다(compose 도 같은 규칙)
                    ask = AiPrompts.Ask.post(titles);
                    break;
                }
                Optional<AiNewsSources.Source> found = news.find(a, config, Instant.now(), ThreadLocalRandom.current());
                if (found.isEmpty()) {
                    // 소재 없음 — 실행기라면 보통 새 글로 쓴다. 시스템 메시지만 보인다
                    AiPrompts.Prompt p = AiPrompts.compose(config, a, a.getPartnerId() != null, place, mood, AiPrompts.Ask.post(titles));
                    return new AiMemberViews.PromptPreview(kind.name(), p.system(), null, null, true, false, place == null ? null : place.name(), moodName(mood), voice);
                }
                AiNewsSources.Source src = found.get();
                source = new AiMemberViews.PromptSource(Long.toString(src.postId()), src.title());
                ask = AiPrompts.Ask.news(new AiPrompts.Quote("뉴스 글", "제목: " + nz(src.title()) + "\n" + nz(src.contentText())), titles);
            }
            case COMMENT -> {
                sample = true;
                ask = AiPrompts.Ask.comment(new AiPrompts.Quote("글", SAMPLE_POST));
            }
            case REPLY -> {
                sample = true;
                ask = AiPrompts.Ask.reply(true, true, new AiPrompts.Quote("내 글", SAMPLE_POST), List.of(new AiPrompts.Quote("상대", SAMPLE_COMMENT)));
            }
            default -> throw new IllegalStateException();
        }
        AiPrompts.Prompt p = AiPrompts.compose(config, a, a.getPartnerId() != null, place, mood, ask);
        return new AiMemberViews.PromptPreview(kind.name(), p.system(), p.user(), source, false, sample, place == null ? null : place.name(), moodName(mood), voice);
    }

    // ── 검사 ──────────────────────────────────────────────────────────────

    private AiPromptSettingsEntity.Values validate(AiMemberRequests.PromptSave body) {
        AiMemberRequests.PromptSave b = body == null ? new AiMemberRequests.PromptSave(null, null, null, null, null, null, null, null, null, null, null, null, null, null) : body;
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        String common = text(b.common(), AiPrompts.DEFAULT_COMMON, AiPrompts.COMMON_MAX, "common", fields);
        String post = text(b.post(), AiPrompts.DEFAULT_POST, AiPrompts.TASK_MAX, "post", fields);
        String newsText = text(b.news(), AiPrompts.DEFAULT_NEWS, AiPrompts.TASK_MAX, "news", fields);
        String comment = text(b.comment(), AiPrompts.DEFAULT_COMMENT, AiPrompts.TASK_MAX, "comment", fields);
        String reply = text(b.reply(), AiPrompts.DEFAULT_REPLY, AiPrompts.TASK_MAX, "reply", fields);
        String dog = text(b.dog(), AiPrompts.DEFAULT_DOG, AiPrompts.TASK_MAX, "dog", fields);
        String cat = text(b.cat(), AiPrompts.DEFAULT_CAT, AiPrompts.TASK_MAX, "cat", fields);
        int moodChance = percent(b.moodChance(), "moodChance", "aiMember.moodChance.range", fields);
        List<AiPromptConfig.Mood> moods = null;
        if (b.moods() != null) {
            if (b.moods().size() > AiPrompts.MOODS_MAX) {
                fields.put("moods", new MessageRef("aiMember.moods.max", AiPrompts.MOODS_MAX));
            } else {
                moods = new ArrayList<>();
                for (int i = 0; i < b.moods().size(); i++) {
                    AiMemberRequests.PromptMood m = b.moods().get(i);
                    String name = m == null || m.name() == null ? "" : m.name().replaceAll("\\s+", " ").strip();
                    String prompt = m == null || m.prompt() == null ? "" : m.prompt().replace("\r\n", "\n").strip();
                    String at = "moods[" + i + "].";
                    if (name.isEmpty()) {
                        fields.put(at + "name", new MessageRef("aiMember.mood.name.required"));
                    } else if (name.length() > AiPrompts.MOOD_NAME_MAX) {
                        fields.put(at + "name", new MessageRef("aiMember.mood.name.max", AiPrompts.MOOD_NAME_MAX));
                    }
                    if (prompt.isEmpty()) {
                        fields.put(at + "prompt", new MessageRef("aiMember.mood.prompt.required"));
                    } else if (prompt.length() > AiPrompts.MOOD_PROMPT_MAX) {
                        fields.put(at + "prompt", new MessageRef("aiMember.mood.prompt.max", AiPrompts.MOOD_PROMPT_MAX));
                    }
                    moods.add(new AiPromptConfig.Mood(name, prompt, m == null || !Boolean.FALSE.equals(m.enabled())));
                }
                if (moods.equals(AiPrompts.DEFAULT_MOODS)) {
                    moods = null; // 기본 목록과 같다 — 기본값으로 둔다
                }
            }
        }
        int newsRatio = percent(b.newsRatio(), "newsRatio", "aiMember.newsRatio.range", fields);
        String keyword = b.newsKeyword() == null ? "" : b.newsKeyword().replaceAll("\\s+", " ").strip();
        if (keyword.isEmpty()) {
            fields.put("newsKeyword", new MessageRef("aiMember.newsKeyword.required"));
        } else if (keyword.length() > AiPrompts.NEWS_KEYWORD_MAX) {
            fields.put("newsKeyword", new MessageRef("aiMember.newsKeyword.max", AiPrompts.NEWS_KEYWORD_MAX));
        }
        int days = b.newsDays() == null ? -1 : b.newsDays();
        if (days < AiPrompts.NEWS_DAYS_MIN || days > AiPrompts.NEWS_DAYS_MAX) {
            fields.put("newsDays", new MessageRef("aiMember.newsDays.range", AiPrompts.NEWS_DAYS_MIN, AiPrompts.NEWS_DAYS_MAX));
        }
        int sentences = b.paragraphSentences() == null ? -1 : b.paragraphSentences();
        if (sentences < AiPrompts.SENTENCES_MIN || sentences > AiPrompts.SENTENCES_MAX) {
            fields.put("paragraphSentences", new MessageRef("aiMember.paragraphSentences.range", AiPrompts.SENTENCES_MIN, AiPrompts.SENTENCES_MAX));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        return new AiPromptSettingsEntity.Values(common, post, newsText, comment, reply, dog, cat, moodChance, moods, newsRatio, keyword, days,
                !Boolean.FALSE.equals(b.paragraphAuto()), sentences);
    }

    /** 지시문 칸 — 비었거나 기본값과 같으면 null(기본값) */
    private static String text(String raw, String fallback, int max, String field, Map<String, MessageRef> fields) {
        if (raw == null) {
            return null;
        }
        String t = raw.replace("\r\n", "\n").replace('\r', '\n').replace("\u0000", "").strip();
        if (t.isEmpty() || t.equals(fallback.strip())) {
            return null;
        }
        if (t.length() > max) {
            fields.put(field, new MessageRef("aiMember.prompt.max", max));
        }
        return t;
    }

    private static int percent(Integer raw, String field, String key, Map<String, MessageRef> fields) {
        if (raw == null || raw < 0 || raw > 100) {
            fields.put(field, new MessageRef(key));
            return 0;
        }
        return raw;
    }

    // ── 응답 ──────────────────────────────────────────────────────────────

    private AiMemberViews.PromptSettings view(AiPromptSettingsEntity s) {
        String by = s.getUpdatedBy() == null ? null : queries.operatorNames(List.of(s.getUpdatedBy())).get(s.getUpdatedBy());
        return new AiMemberViews.PromptSettings(values(s.config()), values(AiPromptConfig.defaults()), AiPrompts.FIXED_RULES, AiPrompts.PLACEHOLDERS,
                new AiMemberViews.PromptLimits(AiPrompts.COMMON_MAX, AiPrompts.TASK_MAX, AiPrompts.MOODS_MAX, AiPrompts.MOOD_NAME_MAX, AiPrompts.MOOD_PROMPT_MAX,
                        AiPrompts.NEWS_KEYWORD_MAX, AiPrompts.NEWS_DAYS_MIN, AiPrompts.NEWS_DAYS_MAX, AiPrompts.SENTENCES_MIN, AiPrompts.SENTENCES_MAX),
                s.getUpdatedAt(), by);
    }

    private static AiMemberViews.PromptValues values(AiPromptConfig c) {
        return new AiMemberViews.PromptValues(c.common(), c.post(), c.news(), c.comment(), c.reply(), c.dog(), c.cat(), c.moodChance(),
                c.moods().stream().map(m -> new AiMemberViews.PromptMood(m.name(), m.prompt(), m.enabled())).toList(), c.newsRatio(), c.newsKeyword(), c.newsDays(),
                c.paragraphAuto(), c.paragraphSentences());
    }

    private static AiPromptConfig config(AiPromptSettingsEntity.Values v) {
        return new AiPromptConfig(v.common(), v.post(), v.news(), v.comment(), v.reply(), v.dog(), v.cat(), v.moodChance(), v.moods(), v.newsRatio(), v.newsKeyword(), v.newsDays(),
                v.paragraphAuto(), v.paragraphSentences());
    }

    /** 저장된 값을 요청 모양으로(미리 보기에 화면 값이 없을 때) */
    private static AiMemberRequests.PromptSave request(AiPromptSettingsEntity.Values v) {
        return new AiMemberRequests.PromptSave(v.common(), v.post(), v.news(), v.comment(), v.reply(), v.dog(), v.cat(), v.moodChance(),
                v.moods() == null ? null : v.moods().stream().map(m -> new AiMemberRequests.PromptMood(m.name(), m.prompt(), m.enabled())).toList(), v.newsRatio(),
                v.newsKeyword(), v.newsDays(), v.paragraphAuto(), v.paragraphSentences());
    }

    /** 감사 상세 — 지시문은 「DEFAULT」 또는 「CUSTOM(글자 수)」(상세 한도 4000자) */
    private static Map<String, Object> summary(AiPromptSettingsEntity.Values v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("common", custom(v.common()));
        m.put("post", custom(v.post()));
        m.put("news", custom(v.news()));
        m.put("comment", custom(v.comment()));
        m.put("reply", custom(v.reply()));
        m.put("dog", custom(v.dog()));
        m.put("cat", custom(v.cat()));
        m.put("moodChance", v.moodChance());
        m.put("moods", v.moods() == null ? "DEFAULT"
                : v.moods().stream().map(x -> x.name() + (x.enabled() ? "" : "(off)")).reduce((x, y) -> x + "·" + y).orElse("(none)"));
        m.put("newsRatio", v.newsRatio());
        m.put("newsKeyword", v.newsKeyword());
        m.put("newsDays", v.newsDays());
        m.put("paragraphAuto", v.paragraphAuto());
        m.put("paragraphSentences", v.paragraphSentences());
        return m;
    }

    private static String custom(String text) {
        return text == null ? "DEFAULT" : "CUSTOM(" + text.length() + "|" + Integer.toHexString(text.hashCode()) + ")";
    }

    /** 미리 보기의 쓰는 곳 — 계정이 고른 게시판 중 지금 쓸 수 있는 첫 곳(없으면 null) */
    private AiPrompts.Place placeOf(AiMemberProfileEntity a) {
        BoardCatalogSnapshot snap = catalog.snapshot();
        for (Long id : a.getBoardIds()) {
            Optional<EffectiveBoard> b = snap.board(id).filter(x -> x.active() && AiMemberRules.blockedReason(x) == null);
            if (b.isPresent()) {
                return new AiPrompts.Place(b.get().name(), b.get().description(), false);
            }
        }
        return null;
    }

    private static String moodName(AiPromptConfig.Mood mood) {
        return mood == null ? null : mood.name();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private AiPromptSettingsEntity row() {
        return settings.findById(AiPromptSettingsEntity.ID).orElseThrow(() -> new IllegalStateException("ai_prompt_settings 행이 없다(V190)"));
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
