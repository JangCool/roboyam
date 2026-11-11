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
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.group.entity.GroupEntity;
import com.community.group.entity.GroupMemberEntity;
import com.community.group.entity.GroupMemberStatus;
import com.community.group.entity.GroupStatus;
import com.community.group.repository.master.GroupMemberRepository;
import com.community.group.repository.master.GroupRepository;
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
 *       쉬지 않음 · 오늘 새 글 시도가 {@code dailyPostMax} 미만인 계정에서 가장 오래 새 글을 안 쓴 계정(한 바퀴에 곳마다 한 건, 계정마다 한 곳). 질의는 곳별 최근 글 시각을 한 번에({@link AiMemberQueries#latestPosts}).</li>
 *   <li><b>글 방식</b>(V191 {@link AiVoice}): 강아지·고양이 소리 계정은 뉴스 글·분위기·문단 나누기 없이 동물 소리 지시로 쓰고, 본문을 {@value AiPrompts#ANIMAL_BODY_MAX}자로 자른다.</li>
 *   <li><b>댓글 규칙</b>(서버에서 강제): ① 다른 AI 글에 첫 댓글 — 글마다 1~2개({@link AiMemberRules#firstCommentTarget}), 계정마다 한 글에 하나 ② 내 글에 달린 AI 댓글에 답글 —
 *       같은 상대와 2~3번까지({@link AiMemberRules#replyRoundLimit}), 상대 AI 는 내 답글에 되받는다(차례가 남았을 때) ③ 내 글에 달린 사람 회원 댓글에 답글 — 같은 회원에게
 *       글 하나당 1~2번({@link AiMemberRules#memberReplyLimit}). <b>사람 회원 글에는 댓글을 달지 않는다</b>(설정 「회원 글 댓글 허용」이 켜졌을 때만 첫 댓글 후보에 넣는다 — 기본 꺼짐).</li>
 *   <li><b>프롬프트</b>({@link AiPrompts}): 회원 글·댓글은 인용 블록의 내용으로만 넣는다(지시가 아니다). 닉네임은 보내지 않는다. 지시문·분위기·뉴스 글 비율·문단 나누기는
 *       관리자 「프롬프트」 설정({@link AiPromptSettings} — 바퀴마다 읽고, 못 읽으면 코드 기본값).</li>
 *   <li><b>뉴스 글</b>: 보통 계정의 게시판 새 글 차례에서 설정 비율(기본 10%)로 사이트 회원이 올린 「뉴스」 말머리 글을 소재로 쓴다({@link AiNewsSources} — 외부 API 없음). 소재가 없으면 보통 새 글.
 *       본문 끝에 빈 줄 + 「참고: 「원래 글 제목」」 + 사이트 안 링크를 서버가 붙이고(링크를 지우는 정리 뒤에), 생성 줄에 소재 글({@code source_post_id})을 남겨 다시 쓰지 않는다.</li>
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

    public AiDraftGenerator(AiMemberProfileRepository accounts, AiMemberSettingsRepository settings, AiDraftRepository drafts, AiMemberQueries queries,
                            AiProviderKeys keys, AiMemberProperties properties, AiLlmClient llm, AiDraftPublisher publisher, BoardCatalog catalog,
                            GroupRepository groups, GroupMemberRepository groupMembers, SnowflakeIdGenerator ids, PlatformTransactionManager txManager,
                            AiModelCatalog models, AiPromptSettings prompts, AiNewsSources news) {
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
        Plan plan = tx.execute(status -> plan(now, config));
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
     * 새 글을 쓸 계정 → 곳({@code postSlots} — 글 쓰는 때 판정 결과, 곳마다 한 계정)
     */
    private record Plan(List<AiMemberProfileEntity> accounts, AiMemberSettingsEntity settings, AiPromptConfig prompts, int budget,
                        Map<Long, Map<AiDraftKind, Long>> attempts, Set<Long> aiUsers, Set<AiProvider> pausedNow, Map<Long, PostSlot> postSlots) {
    }

    /** 새 글을 쓸 곳 — 게시판(소모임 아님) 또는 AI 전용 소모임 하나 */
    private record PostSlot(Long boardId, Long groupId) {
    }

    private Plan plan(Instant now, AiPromptConfig config) {
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
        return new Plan(active, s, config, budget, attempts, aiUsers, EnumSet.noneOf(AiProvider.class), postSlots(active, s.getPostTiming(), attempts, now));
    }

    /**
     * 글 쓰는 때 판정(V191) — 이번 바퀴에 새 글을 쓸 계정 → 곳. 곳마다 한 계정, 계정마다 한 곳. 질의: 곳별 최근 글 · 바쁜 곳의 최근 AI 글 · 대기·최근 시도 곳 · 계정별 마지막 새 글 시도.
     *
     * @param active 쉬지 않는 계정(섞인 차례 — 같은 조건이면 이 차례로 고른다)
     */
    private Map<Long, PostSlot> postSlots(List<AiMemberProfileEntity> active, AiMemberSettingsEntity.PostTiming timing, Map<Long, Map<AiDraftKind, Long>> attempts,
                                          Instant now) {
        if (timing.dailyPostMax() <= 0) {
            return Map.of();
        }
        LocalTime local = now.atZone(ServiceTime.ZONE).toLocalTime();
        BoardCatalogSnapshot snap = catalog.snapshot();
        Map<Long, Set<PostSlot>> placesOf = new HashMap<>();
        Set<Long> boardIds = new HashSet<>();
        Set<Long> groupIds = new HashSet<>();
        List<AiMemberProfileEntity> candidates = new ArrayList<>();
        for (AiMemberProfileEntity a : active) {
            long postsToday = attempts.getOrDefault(a.getId(), Map.of()).getOrDefault(AiDraftKind.POST, 0L);
            if (!a.activeAt(local) || postsToday >= timing.dailyPostMax()) {
                continue; // 활동 창 밖이거나 오늘 새 글을 다 썼다
            }
            Set<PostSlot> places = new HashSet<>();
            for (Long id : a.getBoardIds()) {
                if (snap.board(id).filter(b -> b.active() && !BoardFields.KIND_GROUP.equals(b.kind()) && AiMemberRules.blockedReason(b) == null).isPresent()) {
                    places.add(new PostSlot(id, null));
                    boardIds.add(id);
                }
            }
            for (Long g : aiGroupsOf(a.getUserId())) {
                places.add(new PostSlot(null, g));
                groupIds.add(g);
            }
            if (!places.isEmpty()) {
                placesOf.put(a.getId(), places);
                candidates.add(a);
            }
        }
        if (candidates.isEmpty()) {
            return Map.of();
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
        if (due.isEmpty()) {
            return Map.of();
        }
        Collections.shuffle(due);
        Map<Long, Instant> lastPost = queries.lastPostAttempts(candidates.stream().map(AiMemberProfileEntity::getId).toList());
        // 가장 오래 새 글을 안 쓴 계정 — 한 번도 안 썼으면 맨 앞, 같으면 섞인 차례(min 은 앞의 것을 고른다)
        Comparator<AiMemberProfileEntity> oldestFirst = Comparator.comparing(a -> lastPost.getOrDefault(a.getId(), Instant.EPOCH));
        Map<Long, PostSlot> out = new HashMap<>();
        for (PostSlot slot : due) {
            candidates.stream().filter(a -> !out.containsKey(a.getId()) && placesOf.get(a.getId()).contains(slot)).min(oldestFirst)
                    .ifPresent(a -> out.put(a.getId(), slot));
        }
        return out;
    }

    /** 할 일 하나 — 답글(기다리는 사람이 있다) → 글(차례가 됐으면) → 첫 댓글(가끔). {@code sourceTitle} = 뉴스 글의 원래 글 제목(아니면 null) */
    private record Task(AiDraftEntity.Target target, AiPrompts.Place place, AiPrompts.Ask ask, String sourceTitle) {

        Task(AiDraftEntity.Target target, AiPrompts.Place place, AiPrompts.Ask ask) {
            this(target, place, ask, null);
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
            Task post = postTask(a, slot, snap, now, plan.prompts()); // 글 쓰는 때 판정이 이 계정에 준 곳(V191)
            if (post != null) {
                return post;
            }
        }
        if (gapPassed(last.get(AiDraftKind.COMMENT), COMMENT_GAP, now) && ThreadLocalRandom.current().nextDouble() < COMMENT_CHANCE) {
            return commentTask(a, plan, myGroups, now);
        }
        return null;
    }

    /** 판정이 준 곳에 새 글 — 소모임이면 소모임 글, 게시판이면 보통 계정만 뉴스 글을 설정 비율로 시도(소재가 없으면 보통 새 글) */
    private Task postTask(AiMemberProfileEntity a, PostSlot slot, BoardCatalogSnapshot snap, Instant now, AiPromptConfig config) {
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
        // 뉴스 글 — 보통 계정의 게시판 글 차례에서 설정 비율로 시도(동물 소리 계정·소모임 글은 뉴스 글로 쓰지 않는다)
        if (a.getVoice() == AiVoice.NORMAL && config.newsRatio() > 0 && ThreadLocalRandom.current().nextInt(100) < config.newsRatio()) {
            Optional<AiNewsSources.Source> source = news.find(a, config, now, ThreadLocalRandom.current());
            if (source.isPresent()) {
                AiNewsSources.Source src = source.get();
                return new Task(AiDraftEntity.Target.newsPost(b.id(), src.postId()), place,
                        AiPrompts.Ask.news(new AiPrompts.Quote("뉴스 글", postText(src.title(), src.contentText())), titles), src.title());
            }
        }
        return new Task(AiDraftEntity.Target.post(b.id(), null), place, AiPrompts.Ask.post(titles));
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
        AiPrompts.Prompt prompt = AiPrompts.compose(config, a, a.getPartnerId() != null, task.place(), mood, task.ask());
        AiLlmRequest request = new AiLlmRequest(provider, model, prompt.system(), prompt.user(), properties.maxOutputTokens());
        Long sourcePostId = task.target().sourcePostId();
        try {
            AiLlmResult result = llm.generate(request, key.get().value());
            String title = null;
            String body;
            if (task.target().kind() == AiDraftKind.POST) {
                String[] split = AiPrompts.splitPost(result.text(), config, sourcePostId == null ? AiPrompts.POST_BODY_MAX : AiPrompts.NEWS_BODY_MAX, voice);
                title = split[0];
                body = split[1];
                if (sourcePostId != null && !body.isBlank()) {
                    // 원래 글 표시 — 링크를 지우는 정리(cleanBody) 뒤에 붙인다. 글 보기는 http(s) 주소만 링크로 그리므로 사이트 공개 주소로 만든 절대 주소
                    body = body + "\n\n" + AiPrompts.newsReference(task.sourceTitle(), news.link(sourcePostId));
                }
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
