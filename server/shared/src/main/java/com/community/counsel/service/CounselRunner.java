package com.community.counsel.service;

import com.community.aimember.entity.AiDraftEntity;
import com.community.aimember.entity.AiDraftKind;
import com.community.aimember.entity.AiDraftStatus;
import com.community.aimember.entity.AiMemberProfileEntity;
import com.community.aimember.entity.AiMemberSettingsEntity;
import com.community.aimember.entity.AiProvider;
import com.community.aimember.entity.AiProviderKeyStatus;
import com.community.aimember.llm.AiCounselPrompts;
import com.community.aimember.llm.AiLlmClient;
import com.community.aimember.llm.AiLlmException;
import com.community.aimember.llm.AiLlmRequest;
import com.community.aimember.llm.AiLlmResult;
import com.community.aimember.llm.AiPiiMasker;
import com.community.aimember.llm.AiPrompts;
import com.community.aimember.repository.master.AiDraftRepository;
import com.community.aimember.repository.master.AiMemberProfileRepository;
import com.community.aimember.repository.master.AiMemberQueries;
import com.community.aimember.repository.master.AiMemberSettingsRepository;
import com.community.aimember.service.AiDraftPublisher;
import com.community.aimember.service.AiMemberDirectory;
import com.community.aimember.service.AiModelCatalog;
import com.community.aimember.service.AiProviderKeys;
import com.community.board.service.BoardCatalog;
import com.community.counsel.entity.CounselPostEntity;
import com.community.counsel.entity.CounselState;
import com.community.counsel.repository.master.CounselPostRepository;
import com.community.counsel.repository.master.CounselQueries;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI 연애상담소 실행기의 한 바퀴(ai-love-counsel-contract 「결정」) — 관리자 서버의 AI 회원 실행기({@code AiMemberScheduler}, 1분 · 잠금 {@code ai-member-runner})가 바퀴마다
 * 보통 AI 회원 차례 <b>앞에</b> 부른다(api 서버에는 배치가 없다). 활동 시간대·「글 쓰는 때」·하루 호출 상한({@code daily_call_limit})·한 바퀴 호출 상한({@code calls-per-tick})과
 * 상관없이 돈다 — 양은 상담소 하루 상한(회원당 · 사이트, 글쓰기에서 막는다)이 정한다. 전체 정지 중에는 아무것도 하지 않는다.
 *
 * <ol>
 *   <li><b>새 사연 판정</b>: 「AI 상담소」 말머리 새 글({@value #TRIAGE_BATCH}개씩 — 24시간 안, 회원 글, 판정 줄 없음) → 1차 낱말 목록({@link CounselRules#riskWord})에 걸리면
 *       {@code RISK}(WORD) + 관리자 알림 {@code AI_COUNSEL_RISK}. 아니면 사이트 하루 상한 안에서 {@code CHECKING} 줄.</li>
 *   <li><b>AI 분류</b>(2차, 생성 전): 쉬지 않는 첫 상담사의 모델로 RISK·SAFE 한 낱말 — RISK 면 {@code RISK}(AI) + 알림, SAFE 면 {@code OPEN}(답할 상담사 = 지금 쉬지 않는 상담사).
 *       실패하면 다음 바퀴에 다시, {@value CounselRules#RISK_CHECK_MAX}번 실패하면 낱말 목록만으로 통과. 분류 호출은 생성 줄을 남기지 않는다(로그만).</li>
 *   <li><b>상담 답</b>: {@code OPEN} 줄의 상담사마다 원댓글 하나 — 이미 답(댓글)이나 대기·승인·게시 생성 줄이 있으면 건너뛰고, 실패가 {@value CounselRules#ANSWER_ATTEMPTS}번이면 그만둔다.
 *       결과에 위험 낱말·개인정보 모양이 있으면 검수 대기함({@code PENDING}), 아니면 바로 게시({@code APPROVED} → {@link AiDraftPublisher#publishOne} — 공개 사이트와 같은 쓰기 판정).
 *       키 없음·결제 부족·키 오류는 보통 AI 회원과 같다(실패 줄 + {@link AiProviderKeys#pause}). 모두 답했거나 글 + {@link CounselRules#GIVE_UP} 이 지나면 줄을 끝낸다.</li>
 * </ol>
 * 제공사 호출은 트랜잭션 밖에서 한다. 한 바퀴 호출 상한 {@value #CALLS_PER_TICK}(사연 셋 남짓).
 */
@Component
public class CounselRunner {

    private static final Logger log = LoggerFactory.getLogger(CounselRunner.class);

    static final int TRIAGE_BATCH = 10;
    static final int OPEN_BATCH = 20;
    static final int CALLS_PER_TICK = 12;
    static final String SERVER_EMPTY = AiLlmException.EMPTY;

    private final CounselQueries queries;
    private final CounselPostRepository rows;
    private final AiMemberProfileRepository accounts;
    private final AiMemberSettingsRepository settings;
    private final AiMemberQueries aiQueries;
    private final AiProviderKeys keys;
    private final AiModelCatalog models;
    private final AiLlmClient llm;
    private final AiDraftRepository drafts;
    private final AiDraftPublisher publisher;
    private final BoardCatalog catalog;
    private final PostRepository posts;
    private final NotificationPublisher notifications;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;

    public CounselRunner(CounselQueries queries, CounselPostRepository rows, AiMemberProfileRepository accounts, AiMemberSettingsRepository settings,
                         AiMemberQueries aiQueries, AiProviderKeys keys, AiModelCatalog models, AiLlmClient llm, AiDraftRepository drafts, AiDraftPublisher publisher,
                         BoardCatalog catalog, PostRepository posts, NotificationPublisher notifications, SnowflakeIdGenerator ids, PlatformTransactionManager txManager) {
        this.queries = queries;
        this.rows = rows;
        this.accounts = accounts;
        this.settings = settings;
        this.aiQueries = aiQueries;
        this.keys = keys;
        this.models = models;
        this.llm = llm;
        this.drafts = drafts;
        this.publisher = publisher;
        this.catalog = catalog;
        this.posts = posts;
        this.notifications = notifications;
        this.ids = ids;
        this.tx = new TransactionTemplate(txManager);
    }

    /** 이번 바퀴의 상담사 — 상담사 계정 전부(차례) · 그중 지금 쉬지 않는 계정 · 이번 바퀴에 쉬게 한 제공사 */
    private record Ctx(AiMemberSettingsEntity settings, Map<Long, AiMemberProfileEntity> counselors, List<AiMemberProfileEntity> active, Set<AiProvider> pausedNow,
                       Set<Long> prefixIds) {

        boolean usable(AiMemberProfileEntity a) {
            return active.contains(a) && !pausedNow.contains(a.getProvider());
        }
    }

    /** 한 바퀴 — 제공사 호출 수(실패 포함) */
    public int runOnce() {
        Instant now = Instant.now();
        Ctx ctx = tx.execute(status -> load());
        if (ctx == null) {
            return 0;
        }
        triage(ctx, now);
        List<CounselPostEntity> open = tx.execute(status -> queries.openRows(OPEN_BATCH));
        int calls = 0;
        for (CounselPostEntity row : open == null ? List.<CounselPostEntity>of() : open) {
            if (calls >= CALLS_PER_TICK) {
                break;
            }
            calls += row.getState() == CounselState.CHECKING ? classify(ctx, row.getPostId()) : answer(ctx, row.getPostId(), CALLS_PER_TICK - calls);
        }
        return calls;
    }

    private Ctx load() {
        AiMemberSettingsEntity s = settings.findById(AiMemberSettingsEntity.ID).orElse(null);
        if (s == null || s.isStopped()) {
            return null;
        }
        List<AiMemberProfileEntity> all = accounts.findByCounselorTrueOrderByIdAsc();
        Map<Long, AiMemberProfileEntity> byId = new LinkedHashMap<>();
        all.forEach(a -> byId.put(a.getId(), a));
        Set<AiProvider> usable = keys.usable();
        Map<AiProvider, AiProviderKeyStatus> paused = keys.paused();
        AiModelCatalog.Enabled enabled = models.enabled();
        Map<Long, Boolean> restricted = all.isEmpty() ? Map.of() : aiQueries.restricted(all.stream().map(AiMemberProfileEntity::getUserId).toList());
        List<AiMemberProfileEntity> active = new ArrayList<>();
        for (AiMemberProfileEntity a : all) {
            if (AiMemberDirectory.restingReason(a, s, usable, paused, enabled.allows(a.getProvider(), a.getModel()), restricted.getOrDefault(a.getUserId(), true)) == null) {
                active.add(a);
            }
        }
        return new Ctx(s, byId, active, EnumSet.noneOf(AiProvider.class), CounselRules.counselPrefixIds(catalog.snapshot()));
    }

    // ── 1. 새 사연 판정 ─────────────────────────────────────────────────────

    private void triage(Ctx ctx, Instant now) {
        if (ctx.prefixIds().isEmpty()) {
            return;
        }
        List<CounselQueries.NewPost> fresh = tx.execute(status -> queries.untriaged(ctx.prefixIds(), now.minus(CounselRules.TRIAGE_LOOKBACK), now, now, TRIAGE_BATCH));
        for (CounselQueries.NewPost p : fresh == null ? List.<CounselQueries.NewPost>of() : fresh) {
            Optional<String> word = CounselRules.riskWord(p.title(), p.contentText());
            if (word.isPresent()) {
                tx.executeWithoutResult(status -> {
                    if (rows.findById(p.postId()).isEmpty()) {
                        rows.persist(CounselPostEntity.risk(p.postId(), p.boardId(), p.authorId(), p.createdAt(), CounselPostEntity.RISK_WORD, now()));
                        notifyRisk(p.postId());
                    }
                });
                log.info("AI 상담 사연을 위험으로 판정했다 postId={} 까닭=WORD", p.postId());
                continue;
            }
            Boolean full = tx.execute(status -> {
                int cap = ctx.settings().getCounselLimits().siteDaily();
                if (queries.answeringSince(CounselRules.startOfDay(now)) >= cap) {
                    return true; // 사이트 하루 상한 — 글쓰기에서 먼저 막지만, 고치기·동시 쓰기로 넘친 것은 다음 날까지 기다린다
                }
                if (rows.findById(p.postId()).isEmpty()) {
                    rows.persist(CounselPostEntity.checking(p.postId(), p.boardId(), p.authorId(), p.createdAt(), now()));
                }
                return false;
            });
            if (Boolean.TRUE.equals(full)) {
                log.info("AI 상담 사이트 하루 상한이라 새 사연 판정을 멈췄다");
                return;
            }
        }
    }

    // ── 2. AI 분류 ─────────────────────────────────────────────────────────

    /** @return 호출 수(0 또는 1) */
    private int classify(Ctx ctx, long postId) {
        PostEntity post = tx.execute(status -> posts.findById(postId).orElse(null));
        Instant now = Instant.now();
        if (post == null || post.isGoneAt(now)) {
            tx.executeWithoutResult(status -> rows.findWithLockByPostId(postId).ifPresent(r -> r.close(now())));
            return 0;
        }
        AiMemberProfileEntity classifier = ctx.active().stream().filter(ctx::usable).findFirst().orElse(null);
        if (classifier == null) {
            // 쉬지 않는 상담사가 없다 — 분류할 모델이 없으니 기다린다(시한이 지나면 답할 상담사 없이 끝낸다)
            if (now.isAfter(post.getCreatedAt().plus(CounselRules.GIVE_UP))) {
                tx.executeWithoutResult(status -> rows.findWithLockByPostId(postId).filter(r -> r.getState() == CounselState.CHECKING)
                        .ifPresent(r -> r.open(List.of(), false, now())));
            }
            return 0;
        }
        Optional<AiProviderKeys.LoadedKey> key = keys.load(classifier.getProvider());
        if (key.isEmpty()) {
            ctx.pausedNow().add(classifier.getProvider());
            return 0;
        }
        AiPrompts.Prompt prompt = AiCounselPrompts.classify(post.getTitle(), post.plainContent());
        Boolean risk;
        try {
            AiLlmResult result = llm.generate(new AiLlmRequest(classifier.getProvider(), classifier.getModel(), prompt.system(), prompt.user(),
                    AiCounselPrompts.CLASSIFY_MAX_TOKENS), key.get().value());
            risk = AiCounselPrompts.riskAnswer(result.text());
            log.info("AI 상담 사연 분류 postId={} provider={} model={} 결과={} in={} out={}", postId, classifier.getProvider(), classifier.getModel(),
                    risk == null ? "UNKNOWN" : risk ? "RISK" : "SAFE", result.inputTokens(), result.outputTokens());
        } catch (AiLlmException e) {
            risk = null;
            log.info("AI 상담 사연 분류 실패 postId={} provider={} 까닭={}", postId, classifier.getProvider(), e.reason());
            if (e.pausesProvider()) {
                ctx.pausedNow().add(classifier.getProvider());
                keys.pause(classifier.getProvider(), e.reason(), e.detail(), key.get().keyUpdatedAt(), now());
            }
        }
        Boolean verdict = risk;
        List<Long> answering = ctx.active().stream().filter(ctx::usable).map(AiMemberProfileEntity::getId).toList();
        tx.executeWithoutResult(status -> rows.findWithLockByPostId(postId).filter(r -> r.getState() == CounselState.CHECKING).ifPresent(r -> {
            if (Boolean.TRUE.equals(verdict)) {
                r.markRisk(CounselPostEntity.RISK_AI, now());
                notifyRisk(postId);
            } else if (Boolean.FALSE.equals(verdict) || r.getRiskChecks() + 1 >= CounselRules.RISK_CHECK_MAX) {
                r.open(answering, true, now()); // 통과(또는 분류가 끝내 안 돼 낱말 목록만으로 통과)
            } else {
                r.checkFailed();
            }
        }));
        return 1;
    }

    // ── 3. 상담 답 ─────────────────────────────────────────────────────────

    /** @return 호출 수 */
    private int answer(Ctx ctx, long postId, int budget) {
        CounselPostEntity row = tx.execute(status -> rows.findById(postId).orElse(null));
        PostEntity post = tx.execute(status -> posts.findById(postId).orElse(null));
        Instant now = Instant.now();
        if (row == null || row.getState() != CounselState.OPEN) {
            return 0;
        }
        if (post == null || post.isGoneAt(now) || post.isHiddenAt(now)) {
            tx.executeWithoutResult(status -> rows.findWithLockByPostId(postId).ifPresent(r -> r.close(now())));
            return 0;
        }
        List<Long> counselorIds = row.getCounselorIds();
        List<Long> userIds = counselorIds.stream().map(ctx.counselors()::get).filter(java.util.Objects::nonNull).map(AiMemberProfileEntity::getUserId).toList();
        Map<Long, List<CounselQueries.Answer>> answers = tx.execute(status -> queries.answers(List.of(postId), userIds));
        List<AiDraftEntity> made = tx.execute(status -> queries.drafts(postId, counselorIds));
        Map<Long, List<AiDraftEntity>> byCounselor = new HashMap<>();
        (made == null ? List.<AiDraftEntity>of() : made).forEach(d -> byCounselor.computeIfAbsent(d.getAiMemberId(), k -> new ArrayList<>()).add(d));
        Set<Long> answeredUsers = new java.util.HashSet<>();
        (answers == null ? Map.<Long, List<CounselQueries.Answer>>of() : answers).getOrDefault(postId, List.of()).forEach(a -> answeredUsers.add(a.authorId()));
        boolean late = now.isAfter(post.getCreatedAt().plus(CounselRules.GIVE_UP));
        boolean allDone = true;
        int calls = 0;
        for (Long id : counselorIds) {
            AiMemberProfileEntity a = ctx.counselors().get(id);
            if (a == null || !a.isCounselor()) {
                continue; // 상담사에서 빠졌거나 지운 계정
            }
            List<AiDraftEntity> mine = byCounselor.getOrDefault(id, List.of());
            // 대기·승인·게시 = 답이 있거나 오는 중, 버림 = 운영자가 검수에서 버렸다(다시 만들지 않는다)
            boolean live = mine.stream().anyMatch(d -> d.getStatus() == AiDraftStatus.PENDING || d.getStatus() == AiDraftStatus.APPROVED
                    || d.getStatus() == AiDraftStatus.PUBLISHED || d.getStatus() == AiDraftStatus.DISCARDED);
            long failed = mine.stream().filter(d -> d.getStatus() == AiDraftStatus.FAILED).count();
            if (answeredUsers.contains(a.getUserId()) || live || failed >= CounselRules.ANSWER_ATTEMPTS) {
                continue;
            }
            if (late) {
                continue; // 시한이 지났다 — 더 부르지 않는다
            }
            allDone = false;
            if (calls >= budget || !ctx.usable(a)) {
                continue; // 다음 바퀴(쉬는 상담사는 깨어나면)
            }
            generate(ctx, a, row, post);
            calls++;
        }
        if (allDone || late) {
            tx.executeWithoutResult(status -> rows.findWithLockByPostId(postId).ifPresent(r -> r.close(now())));
        }
        return calls;
    }

    private void generate(Ctx ctx, AiMemberProfileEntity a, CounselPostEntity row, PostEntity post) {
        AiProvider provider = a.getProvider();
        String model = a.getModel();
        AiDraftEntity.Target target = new AiDraftEntity.Target(AiDraftKind.COMMENT, row.getBoardId(), null, row.getPostId(), null, null, row.getAuthorId(), false, null);
        long id = ids.nextId();
        Optional<AiProviderKeys.LoadedKey> key = keys.load(provider);
        if (key.isEmpty()) {
            save(AiDraftEntity.failed(id, a.getId(), target, provider, model, 0, 0, "NO_KEY", now()));
            ctx.pausedNow().add(provider);
            return;
        }
        AiPrompts.Prompt prompt = AiCounselPrompts.answer(a, post.getTitle(), post.plainContent());
        try {
            AiLlmResult result = llm.generate(new AiLlmRequest(provider, model, prompt.system(), prompt.user(), AiCounselPrompts.ANSWER_MAX_TOKENS), key.get().value());
            String body = AiPrompts.cleanBody(result.text(), AiCounselPrompts.ANSWER_BODY_MAX);
            if (body.isBlank()) {
                save(AiDraftEntity.failed(id, a.getId(), target, provider, model, result.inputTokens(), result.outputTokens(), SERVER_EMPTY, now()));
                return;
            }
            // 바로 게시 — 다만 답에 위험 낱말(위기 안내 포함)·개인정보 모양이 있으면 운영자 검수(대기함)로(ai-love-counsel-contract 「정책」 게시 방식)
            boolean review = CounselRules.riskWord(body).isPresent() || !AiPiiMasker.mask(body).equals(body);
            save(AiDraftEntity.generated(id, a.getId(), target, null, body, provider, model, result.inputTokens(), result.outputTokens(), !review, now()));
            if (!review) {
                publisher.publishOne(id);
            }
        } catch (AiLlmException e) {
            save(AiDraftEntity.failed(id, a.getId(), target, provider, model, e.inputTokens(), e.outputTokens(), e.reason(), e.detail(), now()));
            if (e.pausesProvider()) {
                ctx.pausedNow().add(provider);
                keys.pause(provider, e.reason(), e.detail(), key.get().keyUpdatedAt(), now());
            }
        }
    }

    private void save(AiDraftEntity d) {
        tx.executeWithoutResult(status -> drafts.persist(d));
        log.info("AI 상담 답 aiMemberId={} postId={} status={} provider={} model={}", d.getAiMemberId(), d.getPostId(), d.getStatus(), d.getProvider(), d.getModel());
    }

    /** 위험 사연 — 관리자 알림(게시글 관리 권한자). 트랜잭션 안에서(줄과 함께) */
    private void notifyRisk(long postId) {
        notifications.publish(AdminNotificationEvent.AI_COUNSEL_RISK, NotificationContext.empty().link("postId", Long.toString(postId)).dedupe(Long.toString(postId)));
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
