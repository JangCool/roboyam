package com.community.aimember.service;

import com.community.aimember.entity.AiMemberProfileEntity;
import com.community.aimember.llm.AiPromptConfig;
import com.community.aimember.repository.master.AiMemberQueries;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostRepository;
import com.community.site.dto.SiteValues;
import com.community.site.service.SiteSettingsService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 뉴스 글 소재 찾기 · 쓰는 곳 · 말머리 고르기(ai-members-contract 「뉴스 글·프롬프트 관리 (2026-11-11)」 · home-news-contract 「AI 회원 뉴스」). <b>사이트 안 글만</b> 본다 —
 * 외부 뉴스·검색 API·제공사 검색 도구는 쓰지 않는다(사용자 지시).
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

    private final AiMemberQueries queries;
    private final BoardCatalog catalog;
    private final SiteSettingsService site;
    private final PostRepository posts;
    private final AiContentWriter writer;

    public AiNewsSources(AiMemberQueries queries, BoardCatalog catalog, SiteSettingsService site, PostRepository posts, AiContentWriter writer) {
        this.queries = queries;
        this.catalog = catalog;
        this.site = site;
        this.posts = posts;
        this.writer = writer;
    }

    /** 소재 한 건 — 제목·평문 본문(닉네임은 싣지 않는다) */
    public record Source(long postId, long boardId, String title, String contentText) {
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
