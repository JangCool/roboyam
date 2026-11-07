package com.community.tips;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.board.entity.BoardEntity;
import com.community.board.repository.master.BoardRepository;
import com.community.board.service.BoardCatalog;
import com.community.commerce.CommerceApiTestBase;
import com.community.highlight.entity.HighlightEntity;
import com.community.highlight.repository.master.HighlightRepository;
import com.community.highlight.service.HighlightPicks;
import com.community.post.entity.ContentFormat;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 팁·강좌·사용기 묶음 API(docs/features/tips-contract.md 「결정」·「서버 구현 결과」)와 하이라이트 곳(V161)의 공개 읽기.
 * 헤드라인 = tips 곳 하이라이트(노출 제목·요약 반영, 다른 곳 하이라이트는 무관) → 최근 7일 추천순 → 더 오래된 글 추천순, 겹침 없음 · 공지·숨김·19금 빼기 · 게시판 거르기,
 * 많이 본 글 = 최근 30일 조회순 → 기간 밖, 최신 글 = 두 게시판 최신순 20개씩(공지 빼고, 숨긴 글은 자리만·요약 null), 요약 120자, 글 상세 {@code highlighted} 는 어느 곳이든,
 * 가젯의 곳 풀기(없는 곳 → home). 실제 PostgreSQL.
 * 묶음 게시판 slug 가 {@code tip}·{@code review} 고정이라 시험 DB 의 그 두 게시판을 쓰고(없으면 만든다), 시험마다 그 게시판의 글과 tips 곳 하이라이트를 치운다.
 */
class TipsHttpTest extends CommerceApiTestBase {

    @Autowired
    private WebApplicationContext web;
    @Autowired
    private BoardCatalog catalog;
    @Autowired
    private BoardRepository boards;
    @Autowired
    private PostRepository posts;
    @Autowired
    private HighlightRepository highlights;
    @Autowired
    private HighlightPicks picks;

    private MockMvc mvc;
    private long tip;
    private long review;
    private long author;
    private long operator;
    private Instant now;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(web).build();
        tip = bundleBoard("tip", "팁과강좌", 901);
        review = bundleBoard("review", "사용기", 902);
        // SQL 로 남김: 다른 시험이 남긴 묶음 게시판 글을 한꺼번에 지운 상태로 — 글 엔티티의 지우기는 한 건씩이고 부수 처리(포인트 등)가 붙는다
        jdbc.update("UPDATE posts SET deleted_at = now() WHERE board_id IN (?, ?) AND deleted_at IS NULL", tip, review);
        tx.executeWithoutResult(s -> highlights.deleteAll(highlights.findAllByPlaceOrderBySortOrderAscIdAsc("tips")));
        catalog.invalidate();
        author = newMember();
        String base36 = Long.toString(author, 36);
        memberFixture.profile(author, "t" + base36.substring(Math.max(0, base36.length() - 11)));
        operator = newOperator();
        now = Instant.now();
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    /** 묶음 게시판 — 있으면 켜서 쓰고, 없으면 만든다 */
    private long bundleBoard(String slug, String name, int order) {
        Long found = tx.execute(s -> boards.findBySlug(slug).map(b -> {
            b.changeActive(true);
            return b.getId();
        }).orElse(null));
        if (found != null) {
            return found;
        }
        long id = ids.nextId();
        boardFixture.board(id, slug, name, order);
        return id;
    }

    private long post(long board, String title, String content, boolean adult, int likes, int views, int daysAgo) {
        long id = ids.nextId();
        tx.executeWithoutResult(s -> posts.persist(PostEntity.create(id, board, null, author, null, null, null, title, ContentFormat.TEXT, content, null, adult, false,
                Instant.now())));
        // SQL 로 남김: 추천·조회 수는 갱신 금지 칸(updatable=false)이고 작성 시각을 앞당기는 도메인 메서드가 없다
        jdbc.update("UPDATE posts SET like_count = ?, view_count = ?, created_at = ? WHERE id = ?", likes, views, ts(now.minus(Duration.ofDays(daysAgo))), id);
        return id;
    }

    private long post(long board, String title, int likes, int views, int daysAgo) {
        return post(board, title, "본문 " + title, false, likes, views, daysAgo);
    }

    private void change(long postId, Consumer<PostEntity> change) {
        tx.executeWithoutResult(s -> change.accept(posts.findById(postId).orElseThrow()));
    }

    private long highlight(String place, long postId, String title, String summary, int order) {
        long id = ids.nextId();
        tx.executeWithoutResult(s -> highlights.persist(HighlightEntity.create(id, place, postId, title, summary, null, true, null, null, order, operator, now)));
        return id;
    }

    private String json(String url) throws Exception {
        return mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static List<String> ids(String json, String path) {
        return JsonPath.read(json, path + "[*].id");
    }

    private static String s(long id) {
        return Long.toString(id);
    }

    // ── 머리 몫 ─────────────────────────────────────────────────────────────

    @Test
    void 헤드라인은_tips_하이라이트_먼저_그다음_최근_7일_추천순_그다음_오래된_글이고_겹치지_않는다() throws Exception {
        long picked = post(review, "고른 글", 100, 0, 1);     // tips 하이라이트 — 추천이 가장 많아도 한 번만
        long a = post(tip, "추천 10", 10, 0, 1);
        long b = post(review, "추천 5", 5, 0, 2);
        long g = post(review, "추천 3", 3, 0, 3);
        long old = post(tip, "오래된 추천 50", 50, 0, 10);
        long older = post(tip, "더 오래된 추천 1", 1, 0, 20);   // 다섯 자리가 차서 빠진다
        long notice = post(tip, "공지", 99, 0, 1);
        change(notice, p -> p.pin(Instant.now()));
        long hidden = post(tip, "숨긴 글", 98, 0, 1);
        change(hidden, p -> p.staffHide(Instant.now()));
        long adult = post(tip, "19금", "본문", true, 97, 0, 1);
        long noticePick = post(tip, "고른 공지", 0, 0, 1);
        change(noticePick, p -> p.pin(Instant.now()));
        highlight("tips", picked, "운영자 제목", "운영자 요약", 1);
        highlight("tips", noticePick, null, null, 2);       // 공지는 하이라이트여도 뺀다
        highlight("tips", adult, null, null, 3);            // 19금도
        highlight("home", a, "홈 제목", "홈 요약", 1);        // 다른 곳 하이라이트는 무관

        String body = json("/api/tips");

        assertThat(ids(body, "$.data.headline")).containsExactly(s(picked), s(a), s(b), s(g), s(old)).doesNotContain(s(older));
        assertThat((List<String>) JsonPath.read(body, "$.data.boards[*].slug")).containsExactly("tip", "review");
        assertThat((String) JsonPath.read(body, "$.data.headline[0].title")).isEqualTo("운영자 제목");
        assertThat((String) JsonPath.read(body, "$.data.headline[0].excerpt")).isEqualTo("운영자 요약");
        assertThat((String) JsonPath.read(body, "$.data.headline[0].board.slug")).isEqualTo("review");
        assertThat((String) JsonPath.read(body, "$.data.headline[1].title")).as("홈 하이라이트 제목은 쓰지 않는다").isEqualTo("추천 10");
        assertThat((String) JsonPath.read(body, "$.data.headline[1].excerpt")).isEqualTo("본문 추천 10");
        assertThat((java.util.Map<String, Object>) JsonPath.read(body, "$.data.headline[0]")).as("태그 칸은 없다").doesNotContainKey("tags").containsKey("excerpt");
    }

    @Test
    void 게시판을_고르면_그_게시판_글만이고_칩은_묶음_전체() throws Exception {
        long picked = post(review, "사용기 고른 글", 0, 0, 1);
        long a = post(tip, "팁 추천", 4, 0, 1);
        long old = post(tip, "팁 오래된", 9, 0, 15);
        long r = post(review, "사용기 추천", 8, 0, 1);
        highlight("tips", picked, null, null, 1);

        String tipBody = json("/api/tips?board=tip");
        assertThat(ids(tipBody, "$.data.headline")).containsExactly(s(a), s(old));
        assertThat((List<String>) JsonPath.read(tipBody, "$.data.boards[*].slug")).containsExactly("tip", "review");

        String reviewBody = json("/api/tips?board=review");
        assertThat(ids(reviewBody, "$.data.headline")).containsExactly(s(picked), s(r));

        assertThat(ids(json("/api/tips?board=nope"), "$.data.headline")).as("모르는 게시판은 전체").containsExactly(s(picked), s(r), s(a), s(old));
    }

    @Test
    void 많이_본_글은_최근_30일_조회순_모자라면_기간_밖이고_공지_숨김_19금은_뺀다() throws Exception {
        long a = post(review, "조회 300", 0, 300, 1);
        long b = post(review, "조회 100", 0, 100, 29);
        long old = post(review, "기간 밖 조회 1000", 0, 1000, 40);
        long notice = post(review, "공지 조회", 0, 9999, 1);
        change(notice, p -> p.pin(Instant.now()));
        long hidden = post(review, "숨긴 조회", 0, 9999, 1);
        change(hidden, p -> p.staffHide(Instant.now()));
        post(review, "19금 조회", "본문", true, 0, 9999, 1);
        long other = post(tip, "다른 게시판 조회", 0, 5000, 1);

        assertThat(ids(json("/api/tips?board=review"), "$.data.mostViewed")).containsExactly(s(a), s(b), s(old));
        assertThat(ids(json("/api/tips"), "$.data.mostViewed")).containsExactly(s(other), s(a), s(b), s(old));
    }

    // ── 최신 글 ─────────────────────────────────────────────────────────────

    @Test
    void 최신_글은_두_게시판을_최신순으로_합쳐_20개씩_쪽을_나누고_공지는_빼고_숨긴_글은_자리만() throws Exception {
        long first = post(tip, "가장 먼저", 0, 0, 0);
        long hidden = post(review, "숨길 글", 0, 0, 0);
        change(hidden, p -> p.staffHide(Instant.now()));
        long notice = post(tip, "공지", 0, 0, 0);
        change(notice, p -> p.pin(Instant.now()));
        post(tip, "19금", "본문", true, 0, 0, 0);
        long last = 0;
        for (int i = 0; i < 20; i++) {
            last = post(i % 2 == 0 ? tip : review, "글 " + i, 0, 0, 0);
        }

        String page1 = json("/api/tips/posts");
        assertThat((Integer) JsonPath.read(page1, "$.data.totalCount")).as("공지·19금(비성인) 빼고, 숨긴 글 자리는 센다").isEqualTo(22);
        assertThat((Integer) JsonPath.read(page1, "$.data.totalPages")).isEqualTo(2);
        assertThat((Integer) JsonPath.read(page1, "$.data.page")).isEqualTo(1);
        List<String> items = ids(page1, "$.data.items");
        assertThat(items).hasSize(20).startsWith(s(last));
        assertThat((List<String>) JsonPath.read(page1, "$.data.items[*].board.slug")).contains("tip", "review");

        String page2 = json("/api/tips/posts?page=2");
        assertThat(ids(page2, "$.data.items")).containsExactly(s(hidden), s(first));
        assertThat((String) JsonPath.read(page2, "$.data.items[0].hiddenReason")).isNotNull();
        assertThat((Object) JsonPath.read(page2, "$.data.items[0].excerpt")).as("숨긴 글은 요약 없음").isNull();
        assertThat((String) JsonPath.read(page2, "$.data.items[1].excerpt")).isEqualTo("본문 가장 먼저");

        assertThat((Integer) JsonPath.read(json("/api/tips/posts?board=review&page=0"), "$.data.totalCount")).as("고른 게시판만 · 0쪽은 1쪽").isEqualTo(11);
        assertThat(ids(json("/api/tips/posts?page=9"), "$.data.items")).isEmpty();
    }

    @Test
    void 요약은_공백을_한_칸으로_줄이고_120자를_넘으면_말줄임() throws Exception {
        String longText = "  첫 줄\n\n둘째\t\t줄 " + "가".repeat(200);
        long p = post(tip, "긴 글", longText, false, 0, 0, 0);
        long blank = post(tip, "빈 글", " \n ", false, 0, 0, 0);

        String body = json("/api/tips/posts?board=tip");
        List<String> excerpts = JsonPath.read(body, "$.data.items[?(@.id == '" + p + "')].excerpt");
        String excerpt = excerpts.getFirst();
        assertThat(excerpt).startsWith("첫 줄 둘째 줄 가").endsWith("…");
        assertThat(excerpt.codePointCount(0, excerpt.length())).isEqualTo(121);
        List<Object> none = JsonPath.read(body, "$.data.items[?(@.id == '" + blank + "')].excerpt");
        assertThat(none).containsExactly((Object) null);
    }

    // ── 하이라이트 곳의 공개 읽기 ────────────────────────────────────────────

    @Test
    void 글_상세의_highlighted_는_어느_곳이든_노출_중이면_참이고_가젯의_없는_곳은_home() throws Exception {
        long onlyTips = post(tip, "팁에만", 0, 0, 0);
        long none = post(tip, "없음", 0, 0, 0);
        highlight("tips", onlyTips, null, null, 1);

        assertThat((Boolean) JsonPath.read(json("/api/posts/" + onlyTips), "$.data.highlighted")).isTrue();
        assertThat((Boolean) JsonPath.read(json("/api/posts/" + none), "$.data.highlighted")).isFalse();

        assertThat(picks.resolve("tips")).isEqualTo("tips");
        assertThat(picks.resolve("home")).isEqualTo("home");
        assertThat(picks.resolve("no-such-page-x1")).as("없는 가젯 페이지").isEqualTo("home");
        assertThat(picks.resolve("Bad!")).isEqualTo("home");
        assertThat(picks.resolve(null)).isEqualTo("home");
        assertThat(picks.showing("tips", catalog.snapshot(), Instant.now(), null, null, 6)).extracting(x -> x.post().id()).containsExactly(s(onlyTips));
        assertThat(picks.showing("home", catalog.snapshot(), Instant.now(), null, null, 6)).extracting(x -> x.post().id()).doesNotContain(s(onlyTips));
    }
}
