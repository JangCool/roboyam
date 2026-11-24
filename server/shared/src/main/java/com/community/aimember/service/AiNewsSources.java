package com.community.aimember.service;

import com.community.aimember.entity.AiMemberProfileEntity;
import com.community.aimember.entity.AiNewsItemEntity;
import com.community.aimember.entity.AiNewsSourceEntity;
import com.community.aimember.entity.AiNewsSourceKind;
import com.community.aimember.llm.AiPromptConfig;
import com.community.aimember.repository.master.AiMemberQueries;
import com.community.aimember.repository.master.AiNewsItemRepository;
import com.community.aimember.repository.master.AiNewsSourceRepository;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.BoardSecurityValues;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.board.service.ContentRules;
import com.community.board.service.RichText;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostRepository;
import com.community.post.service.PostTags;
import com.community.site.dto.SiteValues;
import com.community.site.service.SiteSettingsService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * 뉴스 글 소재 찾기 · 쓰는 곳 · 말머리·태그 고르기(ai-news-reporter-contract · ai-members-contract 「뉴스 글·프롬프트 관리 (2026-11-11)」 · home-news-contract 「AI 회원 뉴스」).
 *
 * <ul>
 *   <li><b>소재 고르기</b>({@link #pick} — 2026-11-16): ① 수집한 뉴스 항목({@code ai_news_items} — 켜진 출처 · 최근 N일 · 아직 안 쓴 것, 계정 관심사·운영자 추가 지시의 낱말이
 *       제목·요약에 많이 든 것 먼저, 같으면 최신 {@value #PICK_FROM}개 중 무작위) → ② 없으면 사이트 회원의 뉴스 글(아래 {@link #find}). 외부 검색 API·제공사 검색 도구는 쓰지 않는다
 *       (수집은 키 없는 RSS — {@link AiNewsCollector}).</li>
 *   <li><b>말머리</b>({@link #topicPrefixes} · {@link #resolvePrefix}): 쓰는 게시판의 주제 말머리(공통 말머리 공지·질문·후기·잡담 · 꺼진 · 운영진·성인 전용 제외) 중 모델이 고른 이름,
 *       없는 이름이면 첫 주제 말머리. 등록 때 같은 이름으로 다시 찾는다({@link #prefixIdByName}).</li>
 *   <li><b>태그</b>({@link #filterTags}): 모델이 제안한 2~4개를 글 태그 규칙({@link PostTags} — 정리·모양·겹침)과 금지어(거부·가림 방식 모두 — 걸리면 버림)로 거른다. 최대 {@value #TAG_MAX}개.</li>
 * </ul>
 * 아래는 회원 뉴스 글 소재(②)의 규칙이다.
 *
 * <ul>
 *   <li>소재 = 뉴스 게시판({@link BoardFields#NEWS_SLUG})의 글(말머리 상관없이) + 말머리 이름에 키워드(기본 「뉴스」)가 든 글, 최근 N일(기본 3일) — 쓴 지 {@link #SETTLE} 지난 것.</li>
 *   <li>게시판: 켜진 일반 게시판 중 AI 가 쓸 수 없는 「항상 막힘」({@link AiMemberRules#blockedReason})·익명 게시판이 아닌 곳. 그 AI 계정이 쓰는 게시판을 먼저 보고, 없으면 다른 게시판.</li>
 *   <li>글 조건(사람 회원 글 · 공개·정상 · 비밀·19금·익명·소모임 아님 · 이미 소재로 쓴 글 아님)은 {@link AiMemberQueries#newsCandidates}.</li>
 *   <li>최신 {@value #PICK_FROM}개 중 하나를 무작위로 — 여러 AI 가 같은 바퀴에 같은 글을 고르지 않게.</li>
 *   <li><b>쓰는 곳</b>(사용자 2026-11-15): 뉴스 게시판 — 있고 켜져 있고 AI 글을 허용하고(「항상 막힘」 아님) 그 AI 계정이 쓸 수 있을 때({@link #newsTarget} —
 *       {@link AiContentWriter#canPost}, 공개 쓰기 경로와 같은 판정). <b>되돌림</b>: 그렇지 않으면 그 계정의 글 차례 게시판에 말머리 없이 쓴다(예전 방식).</li>
 *   <li><b>말머리</b>({@link #prefixFor} · {@link #pickPrefix}): 뉴스 게시판에 쓸 때만. 소재가 뉴스 게시판 글이고 그 말머리를 쓸 수 있으면 같은 말머리, 아니면 소재 말머리
 *       이름으로 고른다 — 「AI」 → AI, 「로봇」 → 로봇, 「게임」 → 게임(대소문자 무시, 이 차례). 그래도 없으면 말머리 없음. 꺼진 · 운영진 전용 · 성인 전용 말머리는 고르지 않는다.
 *       등록할 때 고른다(대기함에서 늦게 승인돼도 그때의 말머리 설정을 따른다 — 생성 줄에 따로 저장하지 않는다).</li>
 * </ul>
 */
@Component
public class AiNewsSources {

    private static final Logger log = LoggerFactory.getLogger(AiNewsSources.class);

    /** 올라온 뒤 이만큼은 기다린다(쓰자마자 소재로 삼지 않게 — 고치거나 지울 틈) */
    static final Duration SETTLE = Duration.ofMinutes(5);
    static final int PICK_FROM = 10;

    /** 소재 말머리 이름에 든 말(소문자로 비교) → 뉴스 게시판 말머리 이름 — 앞의 것부터 본다 */
    record NameRule(String contains, String prefixName) {
    }

    static final List<NameRule> PREFIX_BY_NAME = List.of(new NameRule("ai", "AI"), new NameRule("로봇", "로봇"), new NameRule("게임", "게임"));

    /** 뉴스 글 태그 최대(계약 「2~4개」) */
    public static final int TAG_MAX = 4;
    /** 수집 항목 후보를 한 번에 읽는 수(최신부터) */
    static final int ITEM_CANDIDATES = 60;
    /** 관심사 낱말에서 뺄 흔한 말 */
    private static final Set<String> STOP_WORDS = Set.of("위주", "위주로", "관련", "소식", "뉴스", "이야기", "중심", "중심으로", "좋아함", "관심", "그리고", "등");
    private static final Pattern WORD_SPLIT = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final AiMemberQueries queries;
    private final BoardCatalog catalog;
    private final SiteSettingsService site;
    private final PostRepository posts;
    private final AiContentWriter writer;
    private final AiNewsItemRepository items;
    private final AiNewsSourceRepository newsSources;

    public AiNewsSources(AiMemberQueries queries, BoardCatalog catalog, SiteSettingsService site, PostRepository posts, AiContentWriter writer, AiNewsItemRepository items,
                         AiNewsSourceRepository newsSources) {
        this.queries = queries;
        this.catalog = catalog;
        this.site = site;
        this.posts = posts;
        this.writer = writer;
        this.items = items;
        this.newsSources = newsSources;
    }

    /** 소재 한 건 — 제목·평문 본문(닉네임은 싣지 않는다) */
    public record Source(long postId, long boardId, String title, String contentText) {
    }

    /**
     * 뉴스 글 소재 — 수집 항목({@code itemId}) 또는 회원 뉴스 글({@code postId}) 중 하나.
     *
     * @param summary    수집 항목은 RSS 요약(짧다), 회원 글은 평문 본문
     * @param sourceName 원 언론사·채널 이름(회원 글이면 null)
     * @param link       원문 링크(수집 항목). 회원 글이면 null — 사이트 안 주소는 {@link #link} 로 만든다
     * @param youtubeVideoId 유튜브 채널 출처(YOUTUBE)의 항목이면 영상 ID(11자 — {@link #youtubeVideoId}), 아니면 null
     */
    public record Material(Long itemId, Long postId, String title, String summary, String sourceName, String link, Instant publishedAt, String youtubeVideoId) {

        public boolean collected() {
            return itemId != null;
        }

        /** 유튜브 영상 소재인가 */
        public boolean video() {
            return youtubeVideoId != null;
        }
    }

    private static final Pattern WATCH_V = Pattern.compile("(?:^|&)v=([A-Za-z0-9_-]{11})(?:&|$)");
    /** youtube.com 의 영상 경로(쇼츠·퍼가기·라이브) */
    private static final Pattern YOUTUBE_PATH = Pattern.compile("/(?:shorts|embed|live)/([A-Za-z0-9_-]{11})/?");
    /** youtu.be 의 짧은 주소 */
    private static final Pattern SHORT_PATH = Pattern.compile("/([A-Za-z0-9_-]{11})/?");

    /**
     * 유튜브 영상 주소 → 영상 ID(순수 함수). 채널 RSS 의 {@code https://www.youtube.com/watch?v=ID} 와 {@code /shorts/ID} · {@code /embed/ID} · {@code /live/ID} ·
     * {@code https://youtu.be/ID}. 호스트는 youtube.com(·www·m) · youtu.be 만. 아니면 null.
     */
    public static String youtubeVideoId(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        java.net.URI u;
        try {
            u = java.net.URI.create(link.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
        String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
        String path = u.getRawPath() == null ? "" : u.getRawPath();
        java.util.regex.Matcher m;
        if (host.equals("youtu.be")) {
            m = SHORT_PATH.matcher(path);
            return m.matches() ? m.group(1) : null;
        }
        if (!host.equals("youtube.com") && !host.equals("www.youtube.com") && !host.equals("m.youtube.com")) {
            return null;
        }
        if (path.equals("/watch")) {
            m = WATCH_V.matcher(u.getRawQuery() == null ? "" : u.getRawQuery());
            return m.find() ? m.group(1) : null;
        }
        m = YOUTUBE_PATH.matcher(path);
        return m.matches() ? m.group(1) : null;
    }

    /**
     * 뉴스 글 소재 고르기 — 수집 항목 먼저, 없으면 회원 뉴스 글({@link #find}). 트랜잭션 안에서 부른다(읽기만).
     *
     * @param taken 이번 바퀴에 다른 계정이 이미 고른 수집 항목(같은 항목을 두 번 쓰지 않게 — 고른 항목을 더한다)
     */
    public Optional<Material> pick(AiMemberProfileEntity a, AiPromptConfig c, Instant now, RandomGenerator random, Set<Long> taken) {
        Optional<AiNewsItemEntity> item = pickItem(a, c, now, random, taken);
        if (item.isPresent()) {
            AiNewsItemEntity i = item.get();
            taken.add(i.getId());
            // 유튜브 채널 출처의 항목이면 영상 ID(영상·짤 게시판에 플레이어로 — 「유튜브 영상 글」)
            boolean youtube = i.getSourceId() != null
                    && newsSources.findById(i.getSourceId()).map(src -> src.getKind() == AiNewsSourceKind.YOUTUBE).orElse(false);
            String videoId = youtube ? youtubeVideoId(i.getLink()) : null;
            return Optional.of(new Material(i.getId(), null, i.getTitle(), i.getSummary(), i.getSourceName(), i.getLink(), i.getPublishedAt(), videoId));
        }
        // 회원 글의 사이트 안 주소는 부르는 쪽이 트랜잭션 밖에서 만든다({@link #link} — 사이트 설정 읽기 실패가 바깥 트랜잭션을 되돌리지 않게)
        return find(a, c, now, random).map(s -> new Material(null, s.postId(), s.title(), s.contentText(), null, null, null, null));
    }

    /** 수집 항목 고르기 — 켜진 출처 · 최근 {@code newsDays}일 · 안 쓴 것. 관심사·추가 지시 낱말이 많이 든 것 먼저, 같으면 최신 {@value #PICK_FROM}개 중 무작위 */
    Optional<AiNewsItemEntity> pickItem(AiMemberProfileEntity a, AiPromptConfig c, Instant now, RandomGenerator random, Set<Long> taken) {
        List<Long> on = newsSources.findByEnabledTrueOrderBySortOrderAscIdAsc().stream().map(AiNewsSourceEntity::getId).toList();
        if (on.isEmpty()) {
            return Optional.empty();
        }
        List<AiNewsItemEntity> found = items.findByUsedAtIsNullAndFetchedAtAfterAndSourceIdInOrderByFetchedAtDescIdDesc(now.minus(Duration.ofDays(c.newsDays())), on,
                PageRequest.of(0, ITEM_CANDIDATES));
        List<AiNewsItemEntity> open = found.stream().filter(i -> !taken.contains(i.getId())).toList();
        if (open.isEmpty()) {
            return Optional.empty();
        }
        Set<String> words = interestWords(a);
        int best = 0;
        List<AiNewsItemEntity> top = new ArrayList<>();
        for (AiNewsItemEntity i : open) {
            int score = score(words, i.getTitle() + "\n" + (i.getSummary() == null ? "" : i.getSummary()));
            if (score > best) {
                best = score;
                top.clear();
            }
            if (score == best) {
                top.add(i);
            }
        }
        List<AiNewsItemEntity> pool = top.size() > PICK_FROM ? top.subList(0, PICK_FROM) : top;
        return Optional.of(pool.get(random.nextInt(pool.size())));
    }

    /** 계정 관심사 + 운영자 추가 지시의 낱말(2자 이상, 소문자, 흔한 말 제외) */
    static Set<String> interestWords(AiMemberProfileEntity a) {
        Set<String> out = new LinkedHashSet<>();
        for (String text : new String[]{a.getInterests(), a.getExtraPrompt()}) {
            if (text == null) {
                continue;
            }
            for (String w : WORD_SPLIT.split(text.toLowerCase(Locale.ROOT))) {
                if (w.codePointCount(0, w.length()) >= 2 && !STOP_WORDS.contains(w)) {
                    out.add(w);
                }
            }
        }
        return out;
    }

    /** 글에 든 낱말 수(대소문자 무시) */
    static int score(Set<String> words, String text) {
        if (words.isEmpty() || text == null) {
            return 0;
        }
        String hay = text.toLowerCase(Locale.ROOT);
        int n = 0;
        for (String w : words) {
            if (hay.contains(w)) {
                n++;
            }
        }
        return n;
    }

    /** 소재로 쓴 수집 항목 표시(생성 줄) — 트랜잭션 안에서 부른다. 항목이 지워졌으면 아무것도 하지 않는다 */
    public void markUsed(long itemId, long draftId, Instant now) {
        items.findById(itemId).ifPresent(i -> i.use(draftId, now));
    }

    /** 사이트 이름(없으면 null) — 뉴스 글 지시의 「{사이트}」 */
    public String siteName() {
        try {
            return site.current().map(SiteValues::name).filter(n -> !n.isBlank()).orElse(null);
        } catch (RuntimeException e) {
            log.warn("사이트 이름을 읽지 못했다 원인={}", e.getClass().getSimpleName());
            return null;
        }
    }

    /** 게시판의 주제 말머리 — 공통 말머리(공지·질문·후기·잡담) · 꺼진 · 운영진 전용 · 성인 전용 제외, 순서대로 */
    public static List<BoardPrefixInfo> topicPrefixes(EffectiveBoard b) {
        if (b == null || b.prefixes() == null) {
            return List.of();
        }
        return b.prefixes().stream().filter(p -> !p.common() && usable(p) && p.name() != null && !p.name().isBlank())
                .sorted(Comparator.comparingInt(BoardPrefixInfo::sortOrder).thenComparingLong(BoardPrefixInfo::id)).toList();
    }

    /** 모델이 고른 말머리 이름 → 그 게시판의 주제 말머리(대소문자·앞뒤 공백 무시). 없는 이름이면 첫 주제 말머리, 주제 말머리가 없으면 null */
    public static BoardPrefixInfo resolvePrefix(EffectiveBoard b, String answer) {
        List<BoardPrefixInfo> topics = topicPrefixes(b);
        if (topics.isEmpty()) {
            return null;
        }
        String want = answer == null ? "" : answer.strip();
        for (BoardPrefixInfo p : topics) {
            if (p.name().strip().equalsIgnoreCase(want)) {
                return p;
            }
        }
        return topics.get(0);
    }

    /** 등록 때 — 생성 줄의 말머리 이름 → 지금 그 게시판의 말머리 ID(이름이 바뀌었거나 꺼졌으면 첫 주제 말머리, 없으면 null) */
    public Long prefixIdByName(long boardId, String prefixName) {
        if (prefixName == null) {
            return null;
        }
        return catalog.snapshot().board(boardId).map(b -> resolvePrefix(b, prefixName)).map(BoardPrefixInfo::id).orElse(null);
    }

    /** 지금 보안 설정(금지어) — 태그 거르기용 */
    public BoardSecurityValues security() {
        return catalog.snapshot().security();
    }

    /**
     * 태그 거르기(순수 함수) — 하나씩 {@link PostTags#clean} · {@link PostTags#valid}(1~20자 · # , 줄바꿈 없음), 금지어가 든 것(거부 방식 {@link ContentRules#blockedWord} ·
     * 가림 방식 {@link ContentRules#mask} 가 바꾸는 것)은 버리고, 대소문자만 다른 겹침은 하나로, 최대 {@value #TAG_MAX}개.
     */
    public static List<String> filterTags(List<String> raw, BoardSecurityValues sec) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Map<String, String> byKey = new LinkedHashMap<>();
        for (String one : raw) {
            String t = PostTags.clean(one);
            if (!PostTags.valid(t)) {
                continue;
            }
            if (sec != null && (ContentRules.blockedWord(sec, t).isPresent() || !t.equals(ContentRules.mask(sec, t)))) {
                continue;
            }
            byKey.putIfAbsent(PostTags.key(t), t);
            if (byKey.size() >= TAG_MAX) {
                break;
            }
        }
        return List.copyOf(byKey.values());
    }

    public Optional<Source> find(AiMemberProfileEntity a, AiPromptConfig c, Instant now, RandomGenerator random) {
        BoardCatalogSnapshot snap = catalog.snapshot();
        String keyword = c.newsKeyword().toLowerCase(Locale.ROOT);
        Set<Long> prefixIds = new HashSet<>();
        Set<Long> newsBoards = new HashSet<>();
        List<Long> eligible = new ArrayList<>();
        for (EffectiveBoard b : snap.boards()) {
            if (!b.active() || !BoardFields.KIND_GENERAL.equals(b.kind()) || b.anonymous() || AiMemberRules.blockedReason(b) != null) {
                continue;
            }
            boolean any = false;
            if (BoardFields.NEWS_SLUG.equals(b.slug())) {
                newsBoards.add(b.id()); // 뉴스 게시판은 말머리와 상관없이 글 전부가 소재(home-news-contract)
                any = true;
            }
            for (BoardPrefixInfo p : b.prefixes()) {
                if (p.name() != null && p.name().toLowerCase(Locale.ROOT).contains(keyword)) {
                    prefixIds.add(p.id());
                    any = true;
                }
            }
            if (any) {
                eligible.add(b.id());
            }
        }
        if (prefixIds.isEmpty() && newsBoards.isEmpty()) {
            return Optional.empty();
        }
        Instant since = now.minus(Duration.ofDays(c.newsDays()));
        Instant until = now.minus(SETTLE);
        Set<Long> mine = new HashSet<>(a.getBoardIds());
        List<Long> own = eligible.stream().filter(mine::contains).toList();
        List<AiMemberQueries.PostCandidate> found = own.isEmpty() ? List.of() : queries.newsCandidates(prefixIds, newsBoards, own, since, until, now, PICK_FROM);
        if (found.isEmpty()) {
            found = queries.newsCandidates(prefixIds, newsBoards, eligible, since, until, now, PICK_FROM);
        }
        if (found.isEmpty()) {
            return Optional.empty();
        }
        AiMemberQueries.PostCandidate p = found.get(random.nextInt(found.size()));
        return Optional.of(new Source(p.postId(), p.boardId(), p.title(), p.contentText()));
    }

    /**
     * 뉴스 글을 쓸 게시판 — 뉴스 게시판({@link BoardFields#NEWS_SLUG})이 있고 이 AI 계정이 지금 쓸 수 있으면 그 게시판. 판정은 {@link AiContentWriter#canPost}
     * (등록 때 부르는 {@link AiContentWriter#post} 의 앞 판정과 같은 코드 — 켜짐 · 「항상 막힘」 아님 · 쓰기 판정 · 게시판 제재, 최소 쓰기 등급은 그 계정이 고른 게시판일 때만 면제).
     * 비면 부르는 쪽이 글 차례 게시판으로 되돌린다(말머리 없음).
     */
    public Optional<EffectiveBoard> newsTarget(AiMemberProfileEntity a, BoardCatalogSnapshot snap) {
        Optional<EffectiveBoard> news = snap.board(BoardFields.NEWS_SLUG).filter(b -> BoardFields.KIND_GENERAL.equals(b.kind()));
        if (news.isEmpty()) {
            return Optional.empty();
        }
        long id = news.get().id();
        return writer.canPost(a.getUserId(), id, a.getBoardIds().contains(id)) ? news : Optional.empty();
    }

    /**
     * 유튜브 영상 글을 쓸 게시판(「유튜브 영상 글」 2026-11-24) — 영상·짤 게시판({@link BoardFields#VIDEO_SLUG})이 일반 게시판이고 · 글 에디터가 영상 표식을 받는 고급
     * ({@link #playerAllowed})이고 · 이 AI 계정이 지금 쓸 수 있으면({@link AiContentWriter#canPost} — {@link #newsTarget} 과 같은 판정) 그 게시판. 비면 부르는 쪽이 뉴스 게시판으로 되돌린다.
     */
    public Optional<EffectiveBoard> videoTarget(AiMemberProfileEntity a, BoardCatalogSnapshot snap) {
        Optional<EffectiveBoard> video = snap.board(BoardFields.VIDEO_SLUG).filter(b -> BoardFields.KIND_GENERAL.equals(b.kind()) && playerAllowed(b));
        if (video.isEmpty()) {
            return Optional.empty();
        }
        long id = video.get().id();
        return writer.canPost(a.getUserId(), id, a.getBoardIds().contains(id)) ? video : Optional.empty();
    }

    /** 그 게시판 글에 영상 플레이어(YouTube 표식)를 넣을 수 있나 — 글 에디터 고급만(간단·평문은 표식을 지운다 — {@link RichText}) */
    public static boolean playerAllowed(EffectiveBoard b) {
        return b != null && RichText.level(b.postEditor()) == RichText.Level.ADVANCED;
    }

    /**
     * 뉴스 글의 말머리 — 쓰는 게시판이 뉴스 게시판일 때만 고른다(되돌림으로 글 차례 게시판에 쓰면 null). 소재 글이 없어졌거나 말머리가 없으면 null.
     *
     * @param targetBoardId 쓰는 게시판(생성 줄의 게시판)
     * @param sourcePostId  소재 글(뉴스 글이 아니면 null)
     */
    public Long prefixFor(long targetBoardId, Long sourcePostId) {
        if (sourcePostId == null) {
            return null;
        }
        BoardCatalogSnapshot snap = catalog.snapshot();
        EffectiveBoard target = snap.board(targetBoardId).filter(b -> BoardFields.NEWS_SLUG.equals(b.slug())).orElse(null);
        if (target == null) {
            return null;
        }
        PostEntity src = posts.findById(sourcePostId).orElse(null);
        if (src == null || src.getPrefixId() == null || src.getBoardId() == null) {
            return null;
        }
        Long srcBoard = src.getBoardId();
        Long srcPrefixId = src.getPrefixId();
        BoardPrefixInfo srcPrefix = snap.board(srcBoard).flatMap(b -> b.prefix(srcPrefixId)).orElse(null);
        return pickPrefix(target.prefixes(), srcBoard == target.id(), srcPrefix);
    }

    /**
     * 말머리 고르기(순수 함수). ① 소재가 뉴스 게시판 글이고 그 말머리를 쓸 수 있으면 같은 말머리 ② 소재 말머리 이름에 「AI」·「로봇」·「게임」(대소문자 무시, 이 차례)이
     * 들었으면 뉴스 게시판의 그 이름 말머리(쓸 수 있는 것만) ③ 없으면 null. 쓸 수 있는 말머리 = 켜짐 · 운영진 전용 아님 · 성인 전용 아님.
     *
     * @param newsPrefixes      뉴스 게시판의 말머리(꺼진 것 포함)
     * @param sourceOnNewsBoard 소재 글이 뉴스 게시판 글인가
     * @param sourcePrefix      소재 글의 말머리(없으면 null)
     */
    static Long pickPrefix(List<BoardPrefixInfo> newsPrefixes, boolean sourceOnNewsBoard, BoardPrefixInfo sourcePrefix) {
        if (sourcePrefix == null) {
            return null;
        }
        if (sourceOnNewsBoard && usable(sourcePrefix) && newsPrefixes.stream().anyMatch(p -> p.id() == sourcePrefix.id())) {
            return sourcePrefix.id();
        }
        String name = sourcePrefix.name() == null ? "" : sourcePrefix.name().toLowerCase(Locale.ROOT);
        for (NameRule rule : PREFIX_BY_NAME) {
            if (!name.contains(rule.contains())) {
                continue;
            }
            Optional<BoardPrefixInfo> match = newsPrefixes.stream()
                    .filter(p -> usable(p) && p.name() != null && p.name().strip().equalsIgnoreCase(rule.prefixName()))
                    .findFirst();
            if (match.isPresent()) {
                return match.get().id();
            }
        }
        return null;
    }

    /** AI 가 고를 수 있는 말머리 — 켜짐 · 운영진 전용 아님 · 성인 전용 아님(AI 글은 19금이 없다) */
    private static boolean usable(BoardPrefixInfo p) {
        return p.active() && !p.staffOnly() && !p.adultOnly();
    }

    /** 사이트 안 글 주소(절대 — 글 보기 화면은 http(s) 주소만 링크로 그린다). 공개 주소가 없으면 null */
    public String link(long postId) {
        try {
            String base = site.current().map(SiteValues::publicUrl).orElse(null);
            return base == null || base.isBlank() ? null : base + "/posts/" + postId;
        } catch (RuntimeException e) {
            log.warn("사이트 공개 주소를 읽지 못해 뉴스 글 링크를 뺀다 원인={}", e.getClass().getSimpleName());
            return null;
        }
    }
}
