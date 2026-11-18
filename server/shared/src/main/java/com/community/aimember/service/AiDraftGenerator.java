package com.community.aimember.service;

import com.community.aimember.entity.AiDraftEntity;
import com.community.aimember.entity.AiDraftKind;
import com.community.aimember.entity.AiMemberProfileEntity;
import com.community.aimember.entity.AiMemberSettingsEntity;
import com.community.aimember.entity.AiProvider;
import com.community.aimember.entity.AiProviderKeyStatus;
import com.community.aimember.entity.AiVoice;
import com.community.aimember.llm.AiLlmClient;
import com.community.aimember.llm.AiLlmException;
import com.community.aimember.llm.AiLlmRequest;
import com.community.aimember.llm.AiLlmResult;
import com.community.aimember.llm.AiPromptConfig;
import com.community.aimember.llm.AiPrompts;
import com.community.aimember.repository.master.AiDraftRepository;
import com.community.aimember.repository.master.AiMemberProfileRepository;
import com.community.aimember.repository.master.AiMemberQueries;
import com.community.aimember.repository.master.AiMemberSettingsRepository;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupMemberStatus;
import com.community.group.entity.GroupStatus;
import com.community.group.repository.master.GroupMemberRepository;
import com.community.group.repository.master.GroupRepository;
import com.community.post.dto.PostSource;
import com.community.post.service.PostSources;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI 회원 할 일 고르기 + 생성(ai-members-contract 정책 4·5·6·8·12·13). 관리자 서버의 실행기({@code AiMemberScheduler})가 바퀴마다 부른다.
 *
 * <ul>
 *   <li><b>쉬는 계정</b>: 꺼짐 · 전체 정지 · 제공사 키 없음 · 제공사 쉬는 중(결제·크레딧 부족 · 키 오류 — V189 키 상태) · 모델이 모델 목록에서 꺼졌거나 없음({@code MODEL_UNAVAILABLE}) · 사이트 제재(정지·영구 정지·탈퇴 진행) —
 *       아무것도 만들지 않는다(관리자 목록에 쉬는 이유로 보인다).</li>
 *   <li><b>하루 호출 상한</b>: 오늘(한국 날짜) 생성 호출 수(실패 포함)가 상한에 닿으면 멈춘다. 한 바퀴 상한 {@code calls-per-tick}, 계정당 한 바퀴 1건.</li>
 *   <li><b>글 — 글 쓰는 때</b>(V191, 모든 계정 공통 설정 {@link AiMemberSettingsEntity.PostTiming} — 계정별 「하루 글 수」 대신): 바퀴마다 곳(고른 게시판 · 가입한 AI 전용 소모임)을 본다.
 *       ① 그곳에 {@code quietDays}일 동안 새 글(사람·AI 모두 — 삭제·숨김 제외)이 없으면 한 건 ② 있으면 그곳의 마지막 AI 회원 글이 {@code busyIntervalDays}일보다 오래됐을 때만 한 건.
 *       대기·승인 중인 새 글이 있는 곳, {@value AiMemberRules#PLACE_RETRY_MINUTES}분 안에 새 글을 시도한 곳(실패·버림 포함)은 건너뛴다. 쓰는 계정은 그곳을 고른 계정 중 지금 활동 창 안 ·
 *       쉬지 않음 · 오늘 새 글 시도가 {@code dailyPostMax} 미만인 계정에서 가장 오래 새 글을 안 쓴 계정(한 바퀴에 곳마다 한 건, 계정마다 한 곳). 질의는 곳별 최근 글 시각을 한 번에({@link AiMemberQueries#latestPosts}).
 *       <b>계정별 상한</b>(V223 — 기자 등): 계정에 하루 상한이 있으면 공통 값 대신 그 값이고, 그 계정은 ①② · 대기 중인 곳 · 곳 재시도 60분을 보지 않고 자기 간격(활동 창 길이 ÷ 상한,
 *       최소 {@value AiMemberRules#ACCOUNT_POST_GAP_MIN_MINUTES}분)마다 고른 곳 중 하나에 쓴다(보통 계정보다 먼저 곳을 잡는다).</li>
 *   <li><b>글 방식</b>(V191 {@link AiVoice}): 강아지·고양이 소리 계정은 뉴스 글·분위기·문단 나누기 없이 동물 소리 지시로 쓰고, 본문을 {@value AiPrompts#ANIMAL_BODY_MAX}자로 자른다.</li>
 *   <li><b>댓글 규칙</b>(서버에서 강제): ① 다른 AI 글에 첫 댓글 — 글마다 1~2개({@link AiMemberRules#firstCommentTarget}), 계정마다 한 글에 하나 ② 내 글에 달린 AI 댓글에 답글 —
 *       같은 상대와 2~3번까지({@link AiMemberRules#replyRoundLimit}), 상대 AI 는 내 답글에 되받는다(차례가 남았을 때) ③ 내 글에 달린 사람 회원 댓글에 답글 — 같은 회원에게
 *       글 하나당 1~2번({@link AiMemberRules#memberReplyLimit}). <b>사람 회원 글에는 댓글을 달지 않는다</b>(설정 「회원 글 댓글 허용」이 켜졌을 때만 첫 댓글 후보에 넣는다 — 기본 꺼짐).</li>
 *   <li><b>프롬프트</b>({@link AiPrompts}): 회원 글·댓글은 인용 블록의 내용으로만 넣는다(지시가 아니다). 닉네임은 보내지 않는다. 지시문·분위기·뉴스 글 비율·문단 나누기는
 *       관리자 「프롬프트」 설정({@link AiPromptSettings} — 바퀴마다 읽고, 못 읽으면 코드 기본값).</li>
 *   <li><b>뉴스 글</b>(AI 기자 — ai-news-reporter-contract 2026-11-16): 소재 = 수집한 뉴스 항목(키 없는 RSS — {@link AiNewsCollector}) → 없으면 사이트 회원의 뉴스 글({@link AiNewsSources#pick}).
 *       <b>뉴스 게시판 차례</b>에는 소재가 있을 때만 쓰고 없으면 건너뛴다(개인 글로 되돌리지 않음). <b>다른 게시판 차례</b>는 보통 계정만 비율(계정별 값 V223, 없으면 공통 설정 — 기본 10%)로
 *       시도해 뉴스 게시판(그 계정이 쓸 수 있을 때 — {@link AiNewsSources#newsTarget}, 아니면 글 차례 게시판)에 쓰고, 소재가 없으면 보통 새 글.
 *       답 형식(제목·말머리·태그·본문)을 나눠 말머리는 쓰는 게시판의 주제 말머리로 확인, 태그는 글 태그 규칙·금지어로 거른다. 출처(수집 항목 = 출처 이름 + 원문 링크 / 회원 글 소재 =
 *       원래 글 제목 + 사이트 안 링크)는 글의 출처 칸(V232 {@code sources})으로 싣고, 본문 끝에는 투자 말머리일 때만 투자 책임 안내를 붙인다(출처 칸을 쓸 수 없으면 예전 본문 꼬리).
 *       생성 줄에 소재(수집 항목 · {@code source_post_id})·말머리 이름·태그·출처를 남긴다.</li>
 *   <li><b>결과</b>: 바로 게시 대상이면 {@code APPROVED} 로 두고 곧바로 등록({@link AiContentWriter} — 공개 사이트 쓰기 경로와 같은 판정, 금칙어·제재 검사), 아니면 대기함 {@code PENDING}. 호출이 실패하면 {@code FAILED} 줄
 *       (다시 시도하지 않는다 — 다음 차례로). 결제·크레딧 부족 · 키 오류면 그 제공사를 쉬게 한다({@link AiProviderKeys#pause} — 상태가 바뀔 때만 관리자 알림 한 번, 키를 다시 저장하면 다시 돈다).</li>
 * </ul>
 * 제공사 호출은 트랜잭션 밖에서 한다.
 */
@Component
public class AiDraftGenerator {

    private static final Logger log = LoggerFactory.getLogger(AiDraftGenerator.class);

    static final Duration COMMENT_GAP = Duration.ofMinutes(20);
    static final Duration POST_LOOKBACK = Duration.ofHours(48);
    static final Duration REPLY_LOOKBACK = Duration.ofHours(72);
    /** 글·댓글이 올라온 뒤 이만큼은 기다린다(바로 달리지 않게) */
    static final Duration SETTLE = Duration.ofMinutes(5);
    static final double COMMENT_CHANCE = 0.5;
    /** 뉴스 글 태그가 하나도 남지 않았다(태그를 쓰는 게시판) — 번들 {@code aiMember.fail.NO_TAGS} */
    static final String NO_TAGS = "NO_TAGS";
    /** 뉴스 글 본문(모델이 쓴 부분)의 최소 한도 — 꼬리가 아주 길어도 이만큼은 둔다 */
    static final int NEWS_BODY_MIN = 600;

    private final AiMemberProfileRepository accounts;
    private final AiMemberSettingsRepository settings;
    private final AiDraftRepository drafts;
    private final AiMemberQueries queries;
    private final AiProviderKeys keys;
    private final AiMemberProperties properties;
    private final AiLlmClient llm;
    private final AiDraftPublisher publisher;
    private final BoardCatalog catalog;
    private final GroupRepository groups;
    private final GroupMemberRepository groupMembers;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;
    private final AiModelCatalog models;
    private final AiPromptSettings prompts;
    private final AiNewsSources news;
    private final AiNewsArticleReader articles;

    public AiDraftGenerator(AiMemberProfileRepository accounts, AiMemberSettingsRepository settings, AiDraftRepository drafts, AiMemberQueries queries,
                            AiProviderKeys keys, AiMemberProperties properties, AiLlmClient llm, AiDraftPublisher publisher, BoardCatalog catalog,
                            GroupRepository groups, GroupMemberRepository groupMembers, SnowflakeIdGenerator ids, PlatformTransactionManager txManager,
                            AiModelCatalog models, AiPromptSettings prompts, AiNewsSources news, AiNewsArticleReader articles) {
        this.articles = articles;
        this.models = models;
        this.prompts = prompts;
        this.news = news;
        this.accounts = accounts;
        this.settings = settings;
        this.drafts = drafts;
        this.queries = queries;
        this.keys = keys;
        this.properties = properties;
        this.llm = llm;
        this.publisher = publisher;
        this.catalog = catalog;
        this.groups = groups;
        this.groupMembers = groupMembers;
        this.ids = ids;
        TransactionTemplate t = new TransactionTemplate(txManager);
        t.setReadOnly(false);
        this.tx = t;
    }

    /** 한 바퀴 — 만든 수(실패 포함 = 호출 수) */
    public int runOnce() {
        Instant now = Instant.now();
        AiPromptConfig config = prompts.current(); // 트랜잭션 밖 — 못 읽으면 기본값
        String siteName = news.siteName(); // 트랜잭션 밖 — 뉴스 글 지시의 「{사이트}」
        Plan plan = tx.execute(status -> plan(now, config, siteName));
        if (plan == null || plan.budget() <= 0) {
            return 0;
        }
        int calls = 0;
        for (AiMemberProfileEntity a : plan.accounts()) {
            if (calls >= plan.budget()) {
                break;
            }
            if (plan.pausedNow().contains(a.getProvider())) {
                continue; // 이번 바퀴에 그 제공사를 쉬게 했다 — 같은 제공사 계정은 헛호출하지 않는다
            }
            Task task = tx.execute(status -> pick(a, plan, now));
            if (task == null) {
                continue;
            }
            generate(a, task, plan.settings(), plan.prompts(), plan.pausedNow());
            calls++;
        }
        return calls;
    }

    // ── 고르기 ────────────────────────────────────────────────────────────

    /**
     * 이번 바퀴에 돌 계정과 남은 호출 수 · 프롬프트 설정 · 이번 바퀴에 쉬게 한 제공사({@code pausedNow} — 바퀴 안에서만 쓰는 모음) ·
     * 새 글을 쓸 계정 → 곳({@code postSlots} — 글 쓰는 때 판정 결과, 곳마다 한 계정) · 이번 바퀴에 고른 뉴스 수집 항목({@code newsTaken}) · 사이트 이름
     */
    private record Plan(List<AiMemberProfileEntity> accounts, AiMemberSettingsEntity settings, AiPromptConfig prompts, int budget,
                        Map<Long, Map<AiDraftKind, Long>> attempts, Set<Long> aiUsers, Set<AiProvider> pausedNow, Map<Long, PostSlot> postSlots, Set<Long> newsTaken, String siteName) {
    }

    /** 새 글을 쓸 곳 — 게시판(소모임 아님) 또는 AI 전용 소모임 하나 */
    private record PostSlot(Long boardId, Long groupId) {
    }

    private Plan plan(Instant now, AiPromptConfig config, String siteName) {
        AiMemberSettingsEntity s = settings.findById(AiMemberSettingsEntity.ID).orElse(null);
        if (s == null || s.isStopped()) {
            return null;
        }
        long used = queries.callsSince(startOfDay(now));
        int budget = (int) Math.min(properties.callsPerTick(), Math.max(0, s.getDailyCallLimit() - used));
        if (budget <= 0) {
            return null;
        }
        Set<AiProvider> usable = keys.usable();
        Map<AiProvider, AiProviderKeyStatus> paused = keys.paused();
        AiModelCatalog.Enabled enabledModels = models.enabled();
        List<AiMemberProfileEntity> all = accounts.findAllByOrderByIdAsc();
        Map<Long, Boolean> restricted = queries.restricted(all.stream().map(AiMemberProfileEntity::getUserId).toList());
        List<AiMemberProfileEntity> active = new ArrayList<>();
        for (AiMemberProfileEntity a : all) {
            if (AiMemberDirectory.restingReason(a, s, usable, paused, enabledModels.allows(a.getProvider(), a.getModel()),
                    restricted.getOrDefault(a.getUserId(), true)) == null) {
                active.add(a);
            }
        }
        Collections.shuffle(active);
        Set<Long> aiUsers = new HashSet<>();
        all.forEach(a -> aiUsers.add(a.getUserId()));
        Map<Long, Map<AiDraftKind, Long>> attempts = queries.attemptsSince(startOfDay(now));
        return new Plan(active, s, config, budget, attempts, aiUsers, EnumSet.noneOf(AiProvider.class), postSlots(active, s.getPostTiming(), attempts, now),
                new HashSet<>(), siteName);
    }

    /**
     * 글 쓰는 때 판정(V191) — 이번 바퀴에 새 글을 쓸 계정 → 곳. 곳마다 한 계정, 계정마다 한 곳. 질의: 곳별 최근 글 · 바쁜 곳의 최근 AI 글 · 대기·최근 시도 곳 · 계정별 마지막 새 글 시도.
     *
     * @param active 쉬지 않는 계정(섞인 차례 — 같은 조건이면 이 차례로 고른다)
     */
    private Map<Long, PostSlot> postSlots(List<AiMemberProfileEntity> active, AiMemberSettingsEntity.PostTiming timing, Map<Long, Map<AiDraftKind, Long>> attempts,
                                          Instant now) {
        // 계정별 상한(V223)이 있으면 그 값, 없으면 공통 값 — 공통이 0 이어도 자기 상한이 있는 계정은 쓴다
        if (active.stream().noneMatch(a -> a.dailyPostMaxOr(timing.dailyPostMax()) > 0)) {
            return Map.of();
        }
        LocalTime local = now.atZone(ServiceTime.ZONE).toLocalTime();
        BoardCatalogSnapshot snap = catalog.snapshot();
        Map<Long, Set<PostSlot>> placesOf = new HashMap<>();
        Set<Long> boardIds = new HashSet<>();
        Set<Long> groupIds = new HashSet<>();
        List<AiMemberProfileEntity> candidates = new ArrayList<>();
        List<AiMemberProfileEntity> ownPaced = new ArrayList<>(); // 자기 상한이 있는 계정 — 게시판 「글 쓰는 때」 대신 자기 간격
        for (AiMemberProfileEntity a : active) {
            long postsToday = attempts.getOrDefault(a.getId(), Map.of()).getOrDefault(AiDraftKind.POST, 0L);
            if (!a.activeAt(local) || postsToday >= a.dailyPostMaxOr(timing.dailyPostMax())) {
                continue; // 활동 창 밖이거나 오늘 새 글을 다 썼다
            }
            Set<PostSlot> places = new HashSet<>();
            boolean own = a.getDailyPostMax() != null;
            for (Long id : a.getBoardIds()) {
                if (snap.board(id).filter(b -> b.active() && !BoardFields.KIND_GROUP.equals(b.kind()) && AiMemberRules.blockedReason(b) == null).isPresent()) {
                    places.add(new PostSlot(id, null));
                    if (!own) {
                        boardIds.add(id);
                    }
                }
            }
            for (Long g : aiGroupsOf(a.getUserId())) {
                places.add(new PostSlot(null, g));
                if (!own) {
                    groupIds.add(g);
                }
            }
            if (!places.isEmpty()) {
                placesOf.put(a.getId(), places);
                (own ? ownPaced : candidates).add(a);
            }
        }
        if (candidates.isEmpty() && ownPaced.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = new ArrayList<>();
        candidates.forEach(a -> ids.add(a.getId()));
        ownPaced.forEach(a -> ids.add(a.getId()));
        Map<Long, Instant> lastPost = queries.lastPostAttempts(ids);
        Map<Long, PostSlot> out = new HashMap<>();
        Set<PostSlot> taken = new HashSet<>();
        // 자기 상한 계정(V223, 기자 등): 마지막 새 글 시도에서 간격(활동 창 길이 ÷ 상한, 최소 15분)이 지났으면 고른 곳 중 하나에 — 한 바퀴에 곳마다 한 건
        for (AiMemberProfileEntity a : ownPaced) {
            int max = a.dailyPostMaxOr(timing.dailyPostMax());
            long gap = Math.max(AiMemberRules.ACCOUNT_POST_GAP_MIN_MINUTES, a.activeMinutes() / Math.max(1, max));
            Instant last = lastPost.get(a.getId());
            if (last != null && last.isAfter(now.minus(Duration.ofMinutes(gap)))) {
                continue;
            }
            List<PostSlot> mine = new ArrayList<>(placesOf.get(a.getId()));
            mine.removeAll(taken);
            if (!mine.isEmpty()) {
                PostSlot slot = mine.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(mine.size()));
                out.put(a.getId(), slot);
                taken.add(slot);
            }
        }
        if (candidates.isEmpty()) {
            return out;
        }
        // ① 조용한 곳 = quietDays 안에 새 글이 없다 ② 바쁜 곳(quietDays 안에 글이 있다)은 busyIntervalDays 안에 AI 회원 글이 없을 때만
        AiMemberQueries.LatestPosts recent = queries.latestPosts(boardIds, groupIds, now.minus(Duration.ofDays(timing.quietDays())), now, false);
        AiMemberQueries.LatestPosts recentAi = recent.boards().isEmpty() && recent.groups().isEmpty()
                ? new AiMemberQueries.LatestPosts(Map.of(), Map.of())
                : queries.latestPosts(recent.boards().keySet(), recent.groups().keySet(), now.minus(Duration.ofDays(timing.busyIntervalDays())), now, true);
        AiMemberQueries.BusyPlaces busy = queries.postBusyPlaces(now.minus(Duration.ofMinutes(AiMemberRules.PLACE_RETRY_MINUTES)));
        List<PostSlot> due = new ArrayList<>();
        for (Long b : boardIds) {
            // 조용한 곳은 최근 글이 없어 최근 AI 글 질의에도 빠진다 → 그대로 차례. 바쁜 곳은 최근 AI 글이 없을 때만
            if (!busy.boards().contains(b) && !recentAi.boards().containsKey(b)) {
                due.add(new PostSlot(b, null));
            }
        }
        for (Long g : groupIds) {
            if (!busy.groups().contains(g) && !recentAi.groups().containsKey(g)) {
                due.add(new PostSlot(null, g));
            }
        }
        due.removeAll(taken);
        if (due.isEmpty()) {
            return out;
        }
        Collections.shuffle(due);
        // 가장 오래 새 글을 안 쓴 계정 — 한 번도 안 썼으면 맨 앞, 같으면 섞인 차례(min 은 앞의 것을 고른다)
        Comparator<AiMemberProfileEntity> oldestFirst = Comparator.comparing(a -> lastPost.getOrDefault(a.getId(), Instant.EPOCH));
        for (PostSlot slot : due) {
            candidates.stream().filter(a -> !out.containsKey(a.getId()) && placesOf.get(a.getId()).contains(slot)).min(oldestFirst)
                    .ifPresent(a -> out.put(a.getId(), slot));
        }
        return out;
    }

    /**
     * 할 일 하나 — 답글(기다리는 사람이 있다) → 글(차례가 됐으면) → 첫 댓글(가끔). 뉴스 글이면 {@code news} = 소재(수집 항목 · 회원 뉴스 글), {@code board} = 쓰는 게시판
     * (말머리·태그를 고를 때 본다). 아니면 둘 다 null.
     */
    private record Task(AiDraftEntity.Target target, AiPrompts.Place place, AiPrompts.Ask ask, AiNewsSources.Material news, EffectiveBoard board) {

        Task(AiDraftEntity.Target target, AiPrompts.Place place, AiPrompts.Ask ask) {
            this(target, place, ask, null, null);
        }
    }

    private Task pick(AiMemberProfileEntity a, Plan plan, Instant now) {
        if (!a.activeAt(now.atZone(ServiceTime.ZONE).toLocalTime())) {
            return null; // 새 글·첫 댓글·답글 모두 활동 시간대 안에서만(사용자 2026-11-11 — 댓글도 그 시간에)
        }
        Map<AiDraftKind, Instant> last = queries.lastAttempts(a.getId());
        List<Long> myGroups = aiGroupsOf(a.getUserId());
        BoardCatalogSnapshot snap = catalog.snapshot();
        Task reply = replyTask(a, plan, myGroups, now, last.get(AiDraftKind.REPLY));
        if (reply != null) {
            return reply;
        }
        PostSlot slot = plan.postSlots().get(a.getId());
        if (slot != null) {
            Task post = postTask(a, slot, snap, now, plan.prompts(), plan.newsTaken(), plan.siteName()); // 글 쓰는 때 판정이 이 계정에 준 곳(V191)
            if (post != null) {
                return post;
            }
        }
        if (gapPassed(last.get(AiDraftKind.COMMENT), COMMENT_GAP, now) && ThreadLocalRandom.current().nextDouble() < COMMENT_CHANCE) {
            return commentTask(a, plan, myGroups, now);
        }
        return null;
    }

    /**
     * 판정이 준 곳에 새 글 — 소모임이면 소모임 글. 게시판이면:
     * <ul>
     *   <li><b>뉴스 게시판 차례</b>(ai-news-reporter-contract 2026-11-16): 뉴스 소재가 있을 때만 뉴스 글을 쓴다 — 소재가 없거나 동물 소리 계정이면 이 차례를 건너뛴다(개인 글로 되돌리지 않음).</li>
     *   <li><b>다른 게시판 차례</b>: 지금처럼 — 보통 계정만 뉴스 글 비율로 뉴스 글을 시도(뉴스 게시판에 쓸 수 있으면 거기, 아니면 이 게시판), 소재가 없으면 보통 새 글.</li>
     * </ul>
     */
    private Task postTask(AiMemberProfileEntity a, PostSlot slot, BoardCatalogSnapshot snap, Instant now, AiPromptConfig config, Set<Long> newsTaken, String siteName) {
        List<String> titles = queries.recentTitles(a.getId(), 10);
        if (slot.groupId() != null) {
            GroupEntity g = groups.findById(slot.groupId()).orElse(null);
            if (g == null) {
                return null;
            }
            return new Task(AiDraftEntity.Target.post(g.getBoardId(), g.getId()), new AiPrompts.Place(g.getName(), g.getDescription(), true), AiPrompts.Ask.post(titles));
        }
        EffectiveBoard b = snap.board(slot.boardId()).filter(x -> x.active() && AiMemberRules.blockedReason(x) == null).orElse(null);
        if (b == null) {
            return null;
        }
        AiPrompts.Place place = new AiPrompts.Place(b.name(), b.description(), false);
        if (BoardFields.NEWS_SLUG.equals(b.slug())) {
            // 뉴스 게시판 차례 — 소재가 있을 때만(동물 소리 계정은 뉴스 글을 쓰지 않으므로 건너뜀). 계정별 비율·하루 글 수는 차례를 정할 때 이미 반영됐다
            if (a.getVoice().animal()) {
                return null;
            }
            return news.pick(a, config, now, ThreadLocalRandom.current(), newsTaken).map(m -> newsTask(m, b, place, titles, siteName)).orElse(null);
        }
        // 다른 게시판 차례 — 뉴스 글을 비율로 시도(계정별 비율 V223 — 기자 계정은 높게, 없으면 공통 설정). 동물 소리 계정·소모임 글은 뉴스 글로 쓰지 않는다
        int newsRatio = a.newsRatioOr(config.newsRatio());
        if (a.getVoice() == AiVoice.NORMAL && newsRatio > 0 && ThreadLocalRandom.current().nextInt(100) < newsRatio) {
            Optional<AiNewsSources.Material> material = news.pick(a, config, now, ThreadLocalRandom.current(), newsTaken);
            if (material.isPresent()) {
                // 쓰는 곳 = 뉴스 게시판(사용자 2026-11-15 — 있고 켜져 있고 AI 글 허용 · 이 계정이 쓸 수 있을 때, 공개 쓰기 경로와 같은 판정). 되돌림: 이 글 차례 게시판(b)
                EffectiveBoard target = news.newsTarget(a, snap).orElse(b);
                AiPrompts.Place newsPlace = target.id() == b.id() ? place : new AiPrompts.Place(target.name(), target.description(), false);
                return newsTask(material.get(), target, newsPlace, titles, siteName);
            }
        }
        return new Task(AiDraftEntity.Target.post(b.id(), null), place, AiPrompts.Ask.post(titles));
    }

    /** 뉴스 글 할 일 — 인용 = 소재 제목·요약·출처(수집 항목) 또는 회원 글 제목·본문. 말머리 목록 = 쓰는 게시판의 주제 말머리 */
    private Task newsTask(AiNewsSources.Material m, EffectiveBoard target, AiPrompts.Place place, List<String> titles, String siteName) {
        AiDraftEntity.Target t = m.postId() != null ? AiDraftEntity.Target.newsPost(target.id(), m.postId()) : AiDraftEntity.Target.post(target.id(), null);
        List<String> prefixes = AiNewsSources.topicPrefixes(target).stream().map(p -> p.name().strip()).toList();
        AiPrompts.Ask ask = AiPrompts.Ask.news(new AiPrompts.Quote("뉴스 소재", materialText(m)), titles, new AiPrompts.NewsInfo(siteName, prefixes));
        return new Task(t, place, ask, m, target);
    }

    /** 인용 블록에 넣을 소재 글 — 제목 · 요약 · 출처(수집 항목) / 제목 · 본문 · 「사이트 회원 글」(회원 뉴스 글). 원문 링크는 넣지 않는다(서버가 따로 붙인다) */
    public static String materialText(AiNewsSources.Material m) {
        return materialText(m, null);
    }

    /** {@link #materialText(AiNewsSources.Material)} + 원문 본문 발췌(수집 항목의 원문을 읽었을 때 — {@link AiNewsArticleReader}, 2026-11-18) */
    public static String materialText(AiNewsSources.Material m, String articleText) {
        StringBuilder s = new StringBuilder("제목: ").append(m.title() == null ? "" : m.title()).append('\n');
        s.append("요약: ").append(m.summary() == null || m.summary().isBlank() ? "(요약 없음 — 제목만 근거로 짧게 쓴다)" : m.summary()).append('\n');
        s.append("출처: ").append(m.collected() ? (m.sourceName() == null ? "" : m.sourceName()) : "이 사이트 회원이 올린 뉴스 글");
        if (m.publishedAt() != null) {
            s.append('\n').append("발행: ").append(m.publishedAt().atZone(ServiceTime.ZONE).toLocalDate());
        }
        if (articleText != null && !articleText.isBlank()) {
            s.append('\n').append("본문 발췌(원문 기사 — 요약할 자료):\n").append(articleText.strip());
        }
        return s.toString();
    }

    /** ① 다른 AI 글의 첫 댓글 */
    private Task commentTask(AiMemberProfileEntity a, Plan plan, List<Long> myGroups, Instant now) {
        boolean includeMembers = plan.settings().isCommentOnMemberPosts();
        List<AiMemberQueries.PostCandidate> found = queries.recentPosts(a.getUserId(), includeMembers, now.minus(POST_LOOKBACK), now.minus(SETTLE), now, 50);
        Set<Long> boards = new HashSet<>(a.getBoardIds());
        Set<Long> groupSet = new HashSet<>(myGroups);
        List<AiMemberQueries.PostCandidate> mine = found.stream()
                .filter(p -> p.groupId() == null ? boards.contains(p.boardId()) : groupSet.contains(p.groupId())).toList();
        if (mine.isEmpty()) {
            return null;
        }
        Map<Long, List<AiDraftEntity>> onPost = byPost(queries.liveDraftsOn(mine.stream().map(AiMemberQueries.PostCandidate::postId).toList()));
        List<AiMemberQueries.PostCandidate> open = new ArrayList<>();
        for (AiMemberQueries.PostCandidate p : mine) {
            List<AiDraftEntity> ds = onPost.getOrDefault(p.postId(), List.of());
            long firsts = ds.stream().filter(d -> d.getKind() == AiDraftKind.COMMENT).count();
            boolean already = ds.stream().anyMatch(d -> d.getAiMemberId().equals(a.getId()));
            if (!already && firsts < AiMemberRules.firstCommentTarget(p.postId())) {
                open.add(p);
            }
        }
        if (open.isEmpty()) {
            return null;
        }
        AiMemberQueries.PostCandidate p = open.get(ThreadLocalRandom.current().nextInt(open.size()));
        boolean aiAuthor = plan.aiUsers().contains(p.authorId());
        AiDraftEntity.Target t = new AiDraftEntity.Target(AiDraftKind.COMMENT, p.boardId(), p.groupId(), p.postId(), null, null, p.authorId(), aiAuthor, null);
        return new Task(t, place(p.boardId(), p.groupId()), AiPrompts.Ask.comment(new AiPrompts.Quote("글", postText(p.title(), p.contentText()))));
    }

    /** ②③ 내 글에 달린 말에 답글 + 상대 글에서 나에게 돌아온 말에 되받기 */
    private Task replyTask(AiMemberProfileEntity a, Plan plan, List<Long> myGroups, Instant now, Instant lastReply) {
        if (!gapPassed(lastReply, Duration.ofMinutes(10), now)) {
            return null;
        }
        long me = a.getUserId();
        List<AiMemberQueries.PostCandidate> myPosts = queries.myRecentPosts(me, now.minus(REPLY_LOOKBACK), now, 30);
        Map<Long, AiMemberQueries.PostCandidate> postsById = new HashMap<>();
        myPosts.forEach(p -> postsById.put(p.postId(), p));
        List<Long> elsewhere = queries.commentedPostIds(a.getId(), now.minus(REPLY_LOOKBACK));
        Set<Long> postIds = new HashSet<>(postsById.keySet());
        postIds.addAll(elsewhere);
        if (postIds.isEmpty()) {
            return null;
        }
        Map<Long, AiMemberQueries.PostHead> heads = queries.postHeads(postIds);
        List<AiMemberQueries.CommentRow> rows = queries.liveComments(postIds, now);
        Map<Long, AiMemberQueries.CommentRow> byId = new HashMap<>();
        rows.forEach(c -> byId.put(c.id(), c));
        Map<Long, List<AiDraftEntity>> onPost = byPost(queries.liveDraftsOn(postIds));
        for (AiMemberQueries.CommentRow c : rows) {
            AiMemberQueries.PostHead head = heads.get(c.postId());
            if (head == null || c.authorId() == null || c.authorId() == me || c.createdAt().isAfter(now.minus(SETTLE))
                    || c.createdAt().isBefore(now.minus(POST_LOOKBACK))) {
                continue;
            }
            boolean myPost = head.authorId() != null && head.authorId() == me;
            Long target = addressee(c, byId, head);
            if (target == null || target != me) {
                continue; // 나에게 한 말이 아니다
            }
            List<AiDraftEntity> ds = onPost.getOrDefault(c.postId(), List.of());
            if (ds.stream().anyMatch(d -> d.getAiMemberId().equals(a.getId()) && Long.valueOf(c.id()).equals(d.getReplyToCommentId()))) {
                continue; // 이미 답했다(대기·승인 포함)
            }
            long other = c.authorId();
            boolean otherAi = plan.aiUsers().contains(other);
            if (!myPost) {
                // 상대 AI 글에서 글쓴이가 내 댓글에 답했다 — 글쓴이의 차례가 남았을 때만 되받는다(끝없는 핑퐁 방지)
                if (!otherAi || head.authorId() == null || head.authorId() != other) {
                    continue;
                }
                long authorRounds = ds.stream().filter(d -> d.getKind() == AiDraftKind.REPLY && Long.valueOf(me).equals(d.getCounterpartUserId())
                        && !d.getAiMemberId().equals(a.getId())).count();
                if (authorRounds >= AiMemberRules.replyRoundLimit(c.postId(), me, other)) {
                    continue;
                }
            } else if (otherAi) {
                long rounds = myRepliesTo(ds, a.getId(), other);
                if (rounds >= AiMemberRules.replyRoundLimit(c.postId(), me, other)) {
                    continue;
                }
            } else {
                long rounds = myRepliesTo(ds, a.getId(), other);
                if (rounds >= AiMemberRules.memberReplyLimit(c.postId(), other)) {
                    continue;
                }
            }
            int round = (int) myRepliesTo(ds, a.getId(), other) + 1;
            long parent = c.parentId() != null ? c.parentId() : c.id();
            AiDraftEntity.Target t = new AiDraftEntity.Target(AiDraftKind.REPLY, head.boardId(), head.groupId(), c.postId(), parent, c.id(), other, otherAi, round);
            List<AiPrompts.Quote> thread = thread(c, parent, rows, me);
            AiPrompts.Ask ask = AiPrompts.Ask.reply(myPost, otherAi, new AiPrompts.Quote(myPost ? "내 글" : "상대 글", postText(head.title(), head.contentText())), thread);
            return new Task(t, place(head.boardId(), head.groupId()), ask);
        }
        return null;
    }

    /** 그 댓글이 누구에게 한 말인가 — 원댓글이면 글쓴이, 답글이면 가리킨 댓글(없으면 원댓글)의 작성자 */
    private static Long addressee(AiMemberQueries.CommentRow c, Map<Long, AiMemberQueries.CommentRow> byId, AiMemberQueries.PostHead head) {
        if (c.parentId() == null) {
            return head.authorId();
        }
        AiMemberQueries.CommentRow to = byId.get(c.replyToId() != null ? c.replyToId() : c.parentId());
        return to == null ? null : to.authorId();
    }

    private static long myRepliesTo(List<AiDraftEntity> ds, long aiMemberId, long other) {
        return ds.stream().filter(d -> d.getKind() == AiDraftKind.REPLY && d.getAiMemberId().equals(aiMemberId) && Long.valueOf(other).equals(d.getCounterpartUserId()))
                .count();
    }

    /** 원댓글부터 그 댓글까지의 주고받기(최근 6개) — 역할은 「나」·「상대」·「다른 사람」만(닉네임 없음) */
    private static List<AiPrompts.Quote> thread(AiMemberQueries.CommentRow target, long parent, List<AiMemberQueries.CommentRow> rows, long me) {
        List<AiPrompts.Quote> out = new ArrayList<>();
        for (AiMemberQueries.CommentRow r : rows) {
            boolean inThread = r.id() == parent || (r.parentId() != null && r.parentId() == parent);
            if (!inThread || r.id() > target.id()) {
                continue;
            }
            String role = r.authorId() != null && r.authorId() == me ? "나" : r.authorId() != null && r.authorId().equals(target.authorId()) ? "상대" : "다른 사람";
            out.add(new AiPrompts.Quote(role, AiPrompts.plain(r.content(), r.format())));
        }
        return out.size() > 6 ? out.subList(out.size() - 6, out.size()) : out;
    }

    // ── 생성 ──────────────────────────────────────────────────────────────

    private void generate(AiMemberProfileEntity a, Task task, AiMemberSettingsEntity s, AiPromptConfig config, Set<AiProvider> pausedNow) {
        AiProvider provider = a.getProvider();
        String model = a.getModel();
        Optional<AiProviderKeys.LoadedKey> key = keys.load(provider);
        long id = ids.nextId();
        boolean instant = s.instantFor(a.getId());
        if (key.isEmpty()) {
            save(AiDraftEntity.failed(id, a.getId(), task.target(), provider, model, 0, 0, "NO_KEY", now()));
            return;
        }
        AiVoice voice = a.getVoice();
        AiPromptConfig.Mood mood = voice.animal() ? null : AiPrompts.pickMood(config, ThreadLocalRandom.current()); // 동물 소리 계정은 분위기를 넣지 않는다
        // 수집 항목 뉴스 글 — 원문 기사를 읽어 본문 발췌를 자료에 더한다(2026-11-18, 트랜잭션 밖 — 못 읽으면 요약만으로). 기사 사진은 가져오지 않는다
        AiNewsArticleReader.Article article = task.news() != null && task.news().collected() && !voice.animal()
                ? articles.read(task.news().link()) : AiNewsArticleReader.Article.EMPTY;
        AiPrompts.Ask ask = task.ask();
        if (article.text() != null && ask.kind() == AiPrompts.Kind.NEWS) {
            ask = AiPrompts.Ask.news(new AiPrompts.Quote("뉴스 소재", materialText(task.news(), article.text())), ask.recentTitles(), ask.news());
        }
        AiPrompts.Prompt prompt = AiPrompts.compose(config, a, a.getPartnerId() != null, task.place(), mood, ask);
        AiLlmRequest request = new AiLlmRequest(provider, model, prompt.system(), prompt.user(), properties.maxOutputTokens());
        Long sourcePostId = task.target().sourcePostId();
        try {
            AiLlmResult result = llm.generate(request, key.get().value());
            if (task.news() != null) {
                generateNews(a, task, config, result, id, instant);
                return;
            }
            String title = null;
            String body;
            if (task.target().kind() == AiDraftKind.POST) {
                String[] split = AiPrompts.splitPost(result.text(), config, sourcePostId == null ? AiPrompts.POST_BODY_MAX : AiPrompts.NEWS_BODY_MAX, voice);
                title = split[0];
                body = split[1];
            } else {
                body = voice.animal() ? AiPrompts.cleanAnimal(result.text()) : AiPrompts.cleanBody(result.text(), AiPrompts.COMMENT_BODY_MAX);
            }
            if (body.isBlank() || (title != null && title.isBlank())) {
                save(AiDraftEntity.failed(id, a.getId(), task.target(), provider, model, result.inputTokens(), result.outputTokens(), AiLlmException.EMPTY, now()));
                return;
            }
            if (AiPrompts.voiceBroken(voice, body, true) || (title != null && AiPrompts.voiceBroken(voice, title, false))) {
                // 동물 소리 계정이 사람 말을 섞었거나 괄호 해석이 없다 — 올리지 않는다(사용자 2026-11-11 「동물 소리는 필수」)
                save(AiDraftEntity.failed(id, a.getId(), task.target(), provider, model, result.inputTokens(), result.outputTokens(), AiPrompts.VOICE_BROKEN, now()));
                return;
            }
            save(AiDraftEntity.generated(id, a.getId(), task.target(), title, body, provider, model, result.inputTokens(), result.outputTokens(), instant, now()));
            if (instant) {
                publisher.publishOne(id); // 바로 게시 — AiContentWriter(공개 사이트와 같은 판정 — 금칙어·제재 검사)
            }
        } catch (AiLlmException e) {
            save(AiDraftEntity.failed(id, a.getId(), task.target(), provider, model, e.inputTokens(), e.outputTokens(), e.reason(), e.detail(), now()));
            if (e.pausesProvider()) {
                // 결제·크레딧 부족 · 키 오류 — 그 제공사를 쉬게 한다(상태가 바뀔 때만 관리자 알림 한 번). 이번 바퀴의 같은 제공사 계정도 건너뛴다
                pausedNow.add(provider);
                keys.pause(provider, e.reason(), e.detail(), key.get().keyUpdatedAt(), now());
            }
        }
    }

    /**
     * 뉴스 글 결과 정리(ai-news-reporter-contract 「글 쓰기」) — 답(제목·말머리·태그·본문)을 나누고, 말머리는 쓰는 게시판의 주제 말머리로 확인(없는 이름이면 첫 주제 말머리),
     * 태그는 글 태그 규칙·금지어로 거른다(그 게시판이 태그를 쓰면 1개 이상 남아야 한다 — 아니면 {@value #NO_TAGS} 실패).
     * <b>출처</b>(V232 — 「본문 꼬리」): 쓰는 게시판이 「출처 칸 쓰기」({@code sourcesEnabled})면 출처를 글의 출처 칸으로 싣는다 — 수집 항목 소재 = 출처 이름 + 원문 링크,
     * 회원 뉴스 글 소재 = 원래 글 제목(앞 40자) + 사이트 안 절대 주소. 본문에는 「출처: …」·AI 요약 안내·「참고: …」를 넣지 않고, 투자 말머리면 투자 책임 안내 한 줄만 끝에 붙인다.
     * 출처 칸을 쓸 수 없으면(게시판 옵션 꺼짐 · 링크가 http(s) 가 아님 · 사이트 공개 주소 없음) 예전처럼 본문 끝에 꼬리를 붙인다.
     * 생성 줄에 소재 항목 · 말머리 이름 · 태그 · 출처를 남기고, 수집 항목은 쓴 것으로 표시한다(같은 트랜잭션).
     */
    private void generateNews(AiMemberProfileEntity a, Task task, AiPromptConfig config, AiLlmResult result, long id, boolean instant) {
        AiNewsSources.Material m = task.news();
        EffectiveBoard board = task.board();
        // 출처(V232) — 회원 글은 사이트 공개 주소로 만든 절대 주소. 출처 칸을 쓸 수 없으면 예전 본문 꼬리(링크를 지우는 정리(cleanBody) 뒤에 붙인다)
        String memberLink = m.collected() || m.postId() == null ? null : news.link(m.postId());
        List<PostSource> sources = newsSources(board, m, memberLink);
        boolean tailInBody = sources.isEmpty();
        String reference = m.collected() ? null : AiPrompts.newsReference(m.title(), memberLink);
        String longest = !tailInBody ? AiPrompts.INVEST_NOTICE
                : m.collected() ? AiPrompts.newsTail(m.sourceName(), m.link(), true) : reference + "\n" + AiPrompts.INVEST_NOTICE;
        int bodyMax = Math.max(NEWS_BODY_MIN, Math.min(AiPrompts.NEWS_BODY_MAX, AiPrompts.POST_BODY_MAX - longest.length() - 2));
        AiPrompts.NewsAnswer answer = AiPrompts.splitNews(result.text(), config, bodyMax);
        if (answer.body().isBlank() || answer.title().isBlank()) {
            save(AiDraftEntity.failed(id, a.getId(), task.target(), a.getProvider(), a.getModel(), result.inputTokens(), result.outputTokens(), AiLlmException.EMPTY, now()));
            return;
        }
        BoardPrefixInfo prefix = AiNewsSources.resolvePrefix(board, answer.prefix());
        List<String> tags = AiNewsSources.filterTags(answer.tags(), catalog.snapshot().security());
        if (board != null && board.tagsEnabled() && tags.isEmpty()) {
            save(AiDraftEntity.failed(id, a.getId(), task.target(), a.getProvider(), a.getModel(), result.inputTokens(), result.outputTokens(), NO_TAGS, now()));
            return;
        }
        boolean invest = prefix != null && AiPrompts.INVEST_PREFIX.equals(prefix.name().strip());
        String tail = !tailInBody ? (invest ? AiPrompts.INVEST_NOTICE : null)
                : m.collected() ? AiPrompts.newsTail(m.sourceName(), m.link(), invest) : reference + (invest ? "\n" + AiPrompts.INVEST_NOTICE : "");
        String body = tail == null ? answer.body() : answer.body() + "\n\n" + tail;
        AiDraftEntity d = AiDraftEntity.generated(id, a.getId(), task.target(), answer.title(), body, a.getProvider(), a.getModel(), result.inputTokens(), result.outputTokens(),
                instant, now());
        d.news(m.itemId(), prefix == null ? null : prefix.name().strip(), board != null && board.tagsEnabled() ? tags : List.of(), sources);
        tx.executeWithoutResult(status -> {
            drafts.persist(d);
            if (m.itemId() != null) {
                news.markUsed(m.itemId(), id, now());
            }
        });
        log.info("AI 생성물 aiMemberId={} kind={} status={} provider={} model={} news=true", d.getAiMemberId(), d.getKind(), d.getStatus(), d.getProvider(), d.getModel());
        if (instant) {
            publisher.publishOne(id); // 바로 게시 — AiContentWriter(공개 사이트와 같은 판정 — 금칙어·제재 검사)
        }
    }

    /**
     * 뉴스 글의 출처 칸(V232) — 쓰는 게시판이 「출처 칸 쓰기」가 아니거나 링크가 http(s) 주소가 아니면 빈 목록(본문 꼬리로 되돌린다).
     * 수집 항목 = 출처 이름(없으면 이름 없음 — 화면이 도메인) + 원문 링크, 회원 뉴스 글 = 원래 글 제목 앞 {@value PostSources#NAME_MAX}자 + 사이트 안 절대 주소.
     */
    static List<PostSource> newsSources(EffectiveBoard board, AiNewsSources.Material m, String memberLink) {
        if (board == null || !board.sourcesEnabled()) {
            return List.of();
        }
        String name = m.collected() ? m.sourceName() : m.title();
        String url = m.collected() ? m.link() : memberLink;
        url = url == null ? null : url.strip();
        if (url == null || !PostSources.validUrl(url)) {
            return List.of();
        }
        return List.of(new PostSource(PostSources.nameOf(name), url));
    }

    private void save(AiDraftEntity d) {
        tx.executeWithoutResult(status -> drafts.persist(d));
        log.info("AI 생성물 aiMemberId={} kind={} status={} provider={} model={}", d.getAiMemberId(), d.getKind(), d.getStatus(), d.getProvider(), d.getModel());
    }

    // ── 도우미 ────────────────────────────────────────────────────────────

    /** 이 회원이 멤버인 열린 AI 전용 소모임 */
    private List<Long> aiGroupsOf(long userId) {
        Set<Long> aiGroups = new HashSet<>();
        groups.findByAiOnlyTrueAndStatusOrderByIdAsc(GroupStatus.ACTIVE).forEach(g -> aiGroups.add(g.getId()));
        List<Long> out = new ArrayList<>();
        for (GroupMemberEntity m : groupMembers.findByUserIdAndStatusIn(userId, List.of(GroupMemberStatus.ACTIVE))) {
            if (aiGroups.contains(m.getGroupId())) {
                out.add(m.getGroupId());
            }
        }
        for (GroupEntity g : groups.findByAiOnlyTrueAndStatusOrderByIdAsc(GroupStatus.ACTIVE)) {
            if (g.getLeaderId().equals(userId) && !out.contains(g.getId())) {
                out.add(g.getId());
            }
        }
        return out;
    }

    private AiPrompts.Place place(long boardId, Long groupId) {
        if (groupId != null) {
            return groups.findById(groupId).map(g -> new AiPrompts.Place(g.getName(), g.getDescription(), true)).orElse(null);
        }
        return catalog.snapshot().board(boardId).map(b -> new AiPrompts.Place(b.name(), b.description(), false)).orElse(null);
    }

    private static String postText(String title, String content) {
        return "제목: " + (title == null ? "" : title) + "\n" + (content == null ? "" : content);
    }

    private static Map<Long, List<AiDraftEntity>> byPost(List<AiDraftEntity> list) {
        Map<Long, List<AiDraftEntity>> out = new HashMap<>();
        list.forEach(d -> out.computeIfAbsent(d.getPostId(), k -> new ArrayList<>()).add(d));
        return out;
    }

    private static boolean gapPassed(Instant last, Duration gap, Instant now) {
        return last == null || !last.plus(gap).isAfter(now);
    }

    static Instant startOfDay(Instant now) {
        return now.atZone(ServiceTime.ZONE).toLocalDate().atStartOfDay(ServiceTime.ZONE).toInstant();
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
