package com.community.aimember.service;

import com.community.aimember.entity.AiDraftEntity;
import com.community.aimember.entity.AiDraftKind;
import com.community.aimember.entity.AiMemberProfileEntity;
import com.community.aimember.entity.AiMemberSettingsEntity;
import com.community.aimember.entity.AiProvider;
import com.community.aimember.llm.AiLlmClient;
import com.community.aimember.llm.AiLlmException;
import com.community.aimember.llm.AiLlmRequest;
import com.community.aimember.llm.AiLlmResult;
import com.community.aimember.llm.AiPrompts;
import com.community.aimember.repository.master.AiDraftRepository;
import com.community.aimember.repository.master.AiMemberProfileRepository;
import com.community.aimember.repository.master.AiMemberQueries;
import com.community.aimember.repository.master.AiMemberSettingsRepository;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
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
 *   <li><b>쉬는 계정</b>: 꺼짐 · 전체 정지 · 제공사 키 없음 · 모델이 배포 설정 목록에서 빠짐({@code MODEL_UNAVAILABLE}) · 사이트 제재(정지·영구 정지·탈퇴 진행) —
 *       아무것도 만들지 않는다(관리자 목록에 쉬는 이유로 보인다).</li>
 *   <li><b>하루 호출 상한</b>: 오늘(한국 날짜) 생성 호출 수(실패 포함)가 상한에 닿으면 멈춘다. 한 바퀴 상한 {@code calls-per-tick}, 계정당 한 바퀴 1건.</li>
 *   <li><b>글</b>: 하루 2~3건 — 오전·오후 창 안의 시각을 계정·날짜로 정한다(서버 여러 대·재기동에도 같다). 그 시각이 지난 차례 수만큼 만든다(같은 종류 시도 사이 30분 이상).
 *       쓸 곳은 고른 게시판 중 무작위, AI 전용 소모임은 가끔(글 차례마다 {@value AiMemberRules#GROUP_POST_CHANCE}, 게시판이 없으면 늘).</li>
 *   <li><b>댓글 규칙</b>(서버에서 강제): ① 다른 AI 글에 첫 댓글 — 글마다 1~2개({@link AiMemberRules#firstCommentTarget}), 계정마다 한 글에 하나 ② 내 글에 달린 AI 댓글에 답글 —
 *       같은 상대와 2~3번까지({@link AiMemberRules#replyRoundLimit}), 상대 AI 는 내 답글에 되받는다(차례가 남았을 때) ③ 내 글에 달린 사람 회원 댓글에 답글 — 같은 회원에게
 *       글 하나당 1~2번({@link AiMemberRules#memberReplyLimit}). <b>사람 회원 글에는 댓글을 달지 않는다</b>(설정 「회원 글 댓글 허용」이 켜졌을 때만 첫 댓글 후보에 넣는다 — 기본 꺼짐).</li>
 *   <li><b>프롬프트</b>({@link AiPrompts}): 회원 글·댓글은 인용 블록의 내용으로만 넣는다(지시가 아니다). 닉네임은 보내지 않는다.</li>
 *   <li><b>결과</b>: 바로 게시 대상이면 {@code APPROVED} 로 두고 곧바로 등록({@link AiContentWriter} — 공개 사이트 쓰기 경로와 같은 판정, 금칙어·제재 검사), 아니면 대기함 {@code PENDING}. 호출이 실패하면 {@code FAILED} 줄
 *       (다시 시도하지 않는다 — 다음 차례로).</li>
 * </ul>
 * 제공사 호출은 트랜잭션 밖에서 한다.
 */
@Component
public class AiDraftGenerator {

    private static final Logger log = LoggerFactory.getLogger(AiDraftGenerator.class);

    static final Duration SAME_KIND_GAP = Duration.ofMinutes(30);
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

    public AiDraftGenerator(AiMemberProfileRepository accounts, AiMemberSettingsRepository settings, AiDraftRepository drafts, AiMemberQueries queries,
                            AiProviderKeys keys, AiMemberProperties properties, AiLlmClient llm, AiDraftPublisher publisher, BoardCatalog catalog,
                            GroupRepository groups, GroupMemberRepository groupMembers, SnowflakeIdGenerator ids, PlatformTransactionManager txManager) {
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
        Plan plan = tx.execute(status -> plan(now));
        if (plan == null || plan.budget() <= 0) {
            return 0;
        }
        int calls = 0;
        for (AiMemberProfileEntity a : plan.accounts()) {
            if (calls >= plan.budget()) {
                break;
            }
            Task task = tx.execute(status -> pick(a, plan, now));
            if (task == null) {
                continue;
            }
            generate(a, task, plan.settings(), now);
            calls++;
        }
        return calls;
    }

    // ── 고르기 ────────────────────────────────────────────────────────────

    /** 이번 바퀴에 돌 계정과 남은 호출 수 */
    private record Plan(List<AiMemberProfileEntity> accounts, AiMemberSettingsEntity settings, int budget, Map<Long, Map<AiDraftKind, Long>> attempts,
                        Set<Long> aiUsers) {
    }

    private Plan plan(Instant now) {
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
        List<AiMemberProfileEntity> all = accounts.findAllByOrderByIdAsc();
        Map<Long, Boolean> restricted = queries.restricted(all.stream().map(AiMemberProfileEntity::getUserId).toList());
        List<AiMemberProfileEntity> active = new ArrayList<>();
        for (AiMemberProfileEntity a : all) {
            if (AiMemberDirectory.restingReason(a, s, usable, properties.allows(a.getProvider(), a.getModel()), restricted.getOrDefault(a.getUserId(), true)) == null) {
                active.add(a);
            }
        }
        Collections.shuffle(active);
        Set<Long> aiUsers = new HashSet<>();
        all.forEach(a -> aiUsers.add(a.getUserId()));
        return new Plan(active, s, budget, queries.attemptsSince(startOfDay(now)), aiUsers);
    }

    /** 할 일 하나 — 답글(기다리는 사람이 있다) → 글(차례가 됐으면) → 첫 댓글(가끔) */
    private record Task(AiDraftEntity.Target target, AiPrompts.Place place, String userPrompt) {
    }

    private Task pick(AiMemberProfileEntity a, Plan plan, Instant now) {
        Map<AiDraftKind, Instant> last = queries.lastAttempts(a.getId());
        List<Long> myGroups = aiGroupsOf(a.getUserId());
        BoardCatalogSnapshot snap = catalog.snapshot();
        Task reply = replyTask(a, plan, myGroups, now, last.get(AiDraftKind.REPLY));
        if (reply != null) {
            return reply;
        }
        long postsToday = plan.attempts().getOrDefault(a.getId(), Map.of()).getOrDefault(AiDraftKind.POST, 0L);
        if (postsToday < duePosts(a, now) && gapPassed(last.get(AiDraftKind.POST), SAME_KIND_GAP, now)) {
            Task post = postTask(a, snap, myGroups, now, postsToday);
            if (post != null) {
                return post;
            }
        }
        if (gapPassed(last.get(AiDraftKind.COMMENT), COMMENT_GAP, now) && ThreadLocalRandom.current().nextDouble() < COMMENT_CHANCE) {
            return commentTask(a, plan, myGroups, now);
        }
        return null;
    }

    /** 오늘 지금까지 지난 글 차례 수(0~하루 글 수) */
    static int duePosts(AiMemberProfileEntity a, Instant now) {
        ZonedDateTime local = now.atZone(ServiceTime.ZONE);
        LocalDate day = local.toLocalDate();
        LocalTime time = local.toLocalTime();
        int n = a.getDailyPosts();
        long seed = AiMemberRules.mix(a.getId(), day.toEpochDay());
        int morning = n == 2 ? 1 : (int) (1 + seed % 2);
        int due = 0;
        for (int i = 0; i < n; i++) {
            boolean am = i < morning;
            LocalTime start = am ? a.getMorningStart() : a.getAfternoonStart();
            LocalTime end = am ? a.getMorningEnd() : a.getAfternoonEnd();
            long span = Math.max(1, Duration.between(start, end).getSeconds());
            LocalTime slot = start.plusSeconds(AiMemberRules.mix(seed, i, 101) % span);
            if (!time.isBefore(slot)) {
                due++;
            }
        }
        return due;
    }

    private Task postTask(AiMemberProfileEntity a, BoardCatalogSnapshot snap, List<Long> myGroups, Instant now, long postsToday) {
        List<EffectiveBoard> boards = new ArrayList<>();
        for (Long id : a.getBoardIds()) {
            snap.board(id).filter(b -> b.active() && AiMemberRules.blockedReason(b) == null).ifPresent(boards::add);
        }
        long seed = AiMemberRules.mix(a.getId(), now.atZone(ServiceTime.ZONE).toLocalDate().toEpochDay(), postsToday, 7);
        boolean toGroup = !myGroups.isEmpty() && (boards.isEmpty() || (seed % 10_000) < AiMemberRules.GROUP_POST_CHANCE * 10_000);
        List<String> titles = queries.recentTitles(a.getId(), 10);
        if (toGroup) {
            Long groupId = myGroups.get((int) (seed % myGroups.size()));
            GroupEntity g = groups.findById(groupId).orElse(null);
            if (g == null) {
                return null;
            }
            return new Task(AiDraftEntity.Target.post(g.getBoardId(), g.getId()), new AiPrompts.Place(g.getName(), g.getDescription(), true), AiPrompts.postTask(titles));
        }
        if (boards.isEmpty()) {
            return null;
        }
        EffectiveBoard b = boards.get(ThreadLocalRandom.current().nextInt(boards.size()));
        return new Task(AiDraftEntity.Target.post(b.id(), null), new AiPrompts.Place(b.name(), b.description(), false), AiPrompts.postTask(titles));
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
        return new Task(t, place(p.boardId(), p.groupId()), AiPrompts.commentTask(new AiPrompts.Quote("글", postText(p.title(), p.contentText()))));
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
            String prompt = AiPrompts.replyTask(myPost, otherAi, new AiPrompts.Quote(myPost ? "내 글" : "상대 글", postText(head.title(), head.contentText())), thread);
            return new Task(t, place(head.boardId(), head.groupId()), prompt);
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

    private void generate(AiMemberProfileEntity a, Task task, AiMemberSettingsEntity s, Instant now) {
        AiProvider provider = a.getProvider();
        String model = a.getModel();
        Optional<String> key = keys.key(provider);
        long id = ids.nextId();
        boolean instant = s.instantFor(a.getId());
        if (key.isEmpty()) {
            save(AiDraftEntity.failed(id, a.getId(), task.target(), provider, model, 0, 0, "NO_KEY", now()));
            return;
        }
        String system = AiPrompts.system(a, a.getPartnerId() != null, task.place());
        AiLlmRequest request = new AiLlmRequest(provider, model, system, task.userPrompt(), properties.maxOutputTokens());
        try {
            AiLlmResult result = llm.generate(request, key.get());
            String title = null;
            String body;
            if (task.target().kind() == AiDraftKind.POST) {
                String[] split = AiPrompts.splitPost(result.text());
                title = split[0];
                body = split[1];
            } else {
                body = AiPrompts.cleanBody(result.text(), AiPrompts.COMMENT_BODY_MAX);
            }
            if (body.isBlank() || (title != null && title.isBlank())) {
                save(AiDraftEntity.failed(id, a.getId(), task.target(), provider, model, result.inputTokens(), result.outputTokens(), AiLlmException.EMPTY, now()));
                return;
            }
            save(AiDraftEntity.generated(id, a.getId(), task.target(), title, body, provider, model, result.inputTokens(), result.outputTokens(), instant, now()));
            if (instant) {
                publisher.publishOne(id); // 바로 게시 — AiContentWriter(공개 사이트와 같은 판정 — 금칙어·제재 검사)
            }
        } catch (AiLlmException e) {
            save(AiDraftEntity.failed(id, a.getId(), task.target(), provider, model, e.inputTokens(), e.outputTokens(), e.reason(), now()));
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
