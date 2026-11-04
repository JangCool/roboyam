package com.community.analytics.collect;

import static com.community.analytics.collect.AnalyticsTestSupport.ALL_ON;
import static com.community.analytics.collect.AnalyticsTestSupport.IDS;
import static com.community.analytics.collect.AnalyticsTestSupport.INDEX;
import static com.community.analytics.collect.AnalyticsTestSupport.settings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.analytics.controller.VisitController;
import com.community.analytics.settings.StatsCollection;
import com.community.zconfiguration.crypto.BlindIndex;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 방문 신호 받기(site-analytics-contract 「수집·쓰기」·「API」) — 수집 게이트(설정·봇·형식), 켜진 항목만 모으기, 이동(새로 고침 제외), 유입(세션 첫 페이지만·분류).
 * DB 없이 수집기의 메모리 모음({@link VisitBuffer})을 본다.
 */
class VisitCollectorGateTest {

    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
    private static final String HOST = "roboyam.test";

    private final AtomicReference<StatsCollection> setting = new AtomicReference<>(ALL_ON);
    private final VisitCollector collector = new VisitCollector(settings(setting), INDEX, IDS);

    private static String body(String path, String prevPath, String referrer, String utmSource) {
        return "{\"visitorId\":\"" + UUID.randomUUID() + "\",\"sessionId\":\"" + UUID.randomUUID() + "\",\"path\":\"" + path + "\",\"kind\":\"BOARD\","
                + "\"prevPath\":" + (prevPath == null ? "null" : "\"" + prevPath + "\"") + ","
                + "\"referrer\":" + (referrer == null ? "null" : "\"" + referrer + "\"") + ","
                + "\"utm\":" + (utmSource == null ? "null" : "{\"source\":\"" + utmSource + "\",\"medium\":\"cpc\",\"campaign\":\"fall\"}") + ","
                + "\"newVisitor\":true}";
    }

    private static String body(String path) {
        return body(path, null, null, null);
    }

    // ── 5. 수집 게이트 ───────────────────────────────────────────────────────

    @Test
    void 설정이_꺼져_있으면_아무것도_모으지_않는다() {
        setting.set(StatsCollection.OFF);
        assertThat(collector.accept(body("/board/free"), UA, HOST)).isFalse();
        // 방문 수집 전체(enabled)가 꺼지면 하위 칸이 다 켜져 있어도 아무것도 모으지 않는다
        setting.set(new StatsCollection(false, true, true, 90, true, true, true, true));
        assertThat(collector.accept(body("/board/free"), UA, HOST)).isFalse();
        assertThat(collector.buffer().drain().isEmpty()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Googlebot/2.1 (+http://www.google.com/bot.html)", "curl/8.4.0", "python-requests/2.31", "Mozilla/5.0 HeadlessChrome/120.0",
            "Java/21.0.1", "   "})
    void 봇과_빈_User_Agent_는_세지_않는다(String ua) {
        assertThat(collector.accept(body("/board/free"), ua, HOST)).isFalse();
        assertThat(collector.accept(body("/board/free"), null, HOST)).isFalse();
        assertThat(collector.buffer().drain().isEmpty()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-json", "[1,2]", "{}",
            "{\"visitorId\":\"zz\",\"sessionId\":\"9b2b0b5e-6f3c-4c55-9d9a-0c2f2b1f0a11\",\"path\":\"/\",\"kind\":\"HOME\"}",
            "{\"visitorId\":\"9b2b0b5e-6f3c-4c55-9d9a-0c2f2b1f0a11\",\"sessionId\":\"9b2b0b5e-6f3c-4c55-9d9a-0c2f2b1f0a11\",\"path\":\"board\",\"kind\":\"HOME\"}",
            "{\"visitorId\":\"9b2b0b5e-6f3c-4c55-9d9a-0c2f2b1f0a11\",\"sessionId\":\"9b2b0b5e-6f3c-4c55-9d9a-0c2f2b1f0a11\",\"path\":\"//evil.example\",\"kind\":\"HOME\"}",
            "{\"visitorId\":\"9b2b0b5e-6f3c-4c55-9d9a-0c2f2b1f0a11\",\"sessionId\":\"9b2b0b5e-6f3c-4c55-9d9a-0c2f2b1f0a11\",\"path\":\"/a b\",\"kind\":\"HOME\"}",
            "{\"visitorId\":\"9b2b0b5e-6f3c-4c55-9d9a-0c2f2b1f0a11\",\"sessionId\":\"9b2b0b5e-6f3c-4c55-9d9a-0c2f2b1f0a11\",\"path\":\"/\",\"kind\":\"ADMIN\"}",
            "{\"visitorId\":\"9b2b0b5e-6f3c-4c55-9d9a-0c2f2b1f0a11\",\"sessionId\":null,\"path\":\"/\",\"kind\":\"HOME\"}"})
    void 형식이_틀린_신호는_조용히_버린다(String raw) {
        assertThat(collector.accept(raw, UA, HOST)).isFalse();
        assertThat(collector.buffer().drain().isEmpty()).isTrue();
    }

    @Test
    void 상한보다_긴_본문과_해시_키가_없는_서버는_버린다() {
        String longBody = body("/" + "a".repeat(VisitCollector.BODY_MAX));
        assertThat(collector.accept(longBody, UA, HOST)).isFalse();
        VisitCollector noKey = new VisitCollector(settings(ALL_ON), new BlindIndex(""), IDS);
        assertThat(noKey.accept(body("/"), UA, HOST)).isFalse();
        assertThat(collector.buffer().drain().isEmpty()).isTrue();
        assertThat(noKey.buffer().drain().isEmpty()).isTrue();
    }

    @Test
    void 경로의_쿼리와_조각은_떼고_저장한다() {
        assertThat(collector.accept(body("/board/free/1?page=2&q=secret#c3"), UA, HOST)).isTrue();
        VisitBuffer.Batch b = collector.buffer().drain();
        assertThat(b.raw()).hasSize(1);
        assertThat(b.raw().peekFirst().path()).isEqualTo("/board/free/1");
        assertThat(b.pages().keySet()).extracting(VisitBuffer.PageKey::path).containsExactly("/board/free/1");
    }

    @Test
    void 공개_신호_API_는_무엇이든_204_이다() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new VisitController(collector)).build();
        mvc.perform(post("/api/visits").contentType(MediaType.TEXT_PLAIN).content("garbage").header("User-Agent", UA)).andExpect(status().isNoContent());
        mvc.perform(post("/api/visits").contentType(MediaType.APPLICATION_JSON).content(body("/")).header("User-Agent", "Googlebot")).andExpect(status().isNoContent());
        assertThat(collector.buffer().drain().isEmpty()).isTrue();
        mvc.perform(post("/api/visits").contentType(MediaType.TEXT_PLAIN).content(body("/")).header("User-Agent", UA)).andExpect(status().isNoContent());
        assertThat(collector.buffer().drain().visitors()).hasSize(1);
    }

    // ── 5. 켜진 항목만 ───────────────────────────────────────────────────────

    @Test
    void 방문자를_끄고_원본만_켜면_원본과_페이지별_조회만_모은다() {
        setting.set(new StatsCollection(true, false, true, 90, false, false, false, false));
        assertThat(collector.accept(body("/post/1", "/board/free", "https://www.google.com/search?q=x", null), UA, HOST)).isTrue();
        VisitBuffer.Batch b = collector.buffer().drain();
        assertThat(b.visitors()).isEmpty();
        assertThat(b.pageViews()).isEmpty();
        assertThat(b.active()).isEmpty();
        assertThat(b.raw()).hasSize(1);
        assertThat(b.pages()).hasSize(1);
        assertThat(b.referrers()).isEmpty();
        assertThat(b.flows()).isEmpty();
    }

    @Test
    void 방문자만_켜면_방문자_페이지뷰_지금_접속만_모은다() {
        setting.set(new StatsCollection(true, true, false, 90, false, false, false, false));
        assertThat(collector.accept(body("/", null, "https://www.google.com/", null), UA, HOST)).isTrue();
        VisitBuffer.Batch b = collector.buffer().drain();
        assertThat(b.visitors()).hasSize(1);
        assertThat(b.pageViews().values()).containsExactly(1L);
        assertThat(b.active()).hasSize(1);
        assertThat(b.raw()).isEmpty();
        assertThat(b.pages()).isEmpty();
        assertThat(b.referrers()).isEmpty();
        assertThat(b.flows()).isEmpty();
    }

    @Test
    void 원본에는_원본_UUID_를_남기지_않고_해시만_남긴다() {
        String visitor = UUID.randomUUID().toString();
        String raw = "{\"visitorId\":\"" + visitor + "\",\"sessionId\":\"" + UUID.randomUUID() + "\",\"path\":\"/\",\"kind\":\"HOME\",\"prevPath\":null,\"newVisitor\":false}";
        assertThat(collector.accept(raw, UA, HOST)).isTrue();
        VisitBuffer.RawView view = collector.buffer().drain().raw().peekFirst();
        assertThat(view.visitorHash()).hasSize(64).doesNotContain(visitor).isEqualTo(INDEX.hash(VisitCollector.VISITOR, visitor));
        assertThat(view.newVisitor()).isFalse();
        assertThat(view.device()).isEqualTo("DESKTOP");
    }

    // ── 4. 이동 흐름 ─────────────────────────────────────────────────────────

    @Test
    void 이동은_직전에서_이번으로_새로_고침과_세션_첫_페이지는_이동이_아니다() {
        collector.accept(body("/board/free", "/", null, null), UA, HOST);
        collector.accept(body("/board/free", "/", null, null), UA, HOST);
        collector.accept(body("/board/free", "/board/free", null, null), UA, HOST); // 새로 고침
        collector.accept(body("/", null, null, null), UA, HOST); // 세션 첫 페이지
        VisitBuffer.Batch b = collector.buffer().drain();
        assertThat(b.flows()).hasSize(1);
        assertThat(b.flows().entrySet().iterator().next()).satisfies(e -> {
            assertThat(e.getKey().from()).isEqualTo("/");
            assertThat(e.getKey().to()).isEqualTo("/board/free");
            assertThat(e.getValue()).isEqualTo(2L);
        });
        // 새로 고침도 페이지뷰로는 센다(계약 — 페이지뷰는 본 횟수)
        assertThat(b.pageViews().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(4);
    }

    // ── 4. 유입 분류 ─────────────────────────────────────────────────────────

    @Test
    void 유입은_세션_첫_페이지만_방문_1로_센다() {
        collector.accept(body("/", null, "https://search.naver.com/search.naver?query=x", null), UA, HOST);
        collector.accept(body("/post/1", "/", "https://search.naver.com/search.naver?query=x", null), UA, HOST); // 세션 안 두 번째 — 유입 아님
        VisitBuffer.Batch b = collector.buffer().drain();
        assertThat(b.referrers()).hasSize(1);
        assertThat(b.referrers().values()).containsExactly(1L);
        assertThat(b.referrers().keySet().iterator().next()).isEqualTo(new VisitBuffer.ReferrerKey(b.referrers().keySet().iterator().next().day(), "SEARCH",
                "search.naver.com"));
        // 원본에도 세션 첫 페이지만 유입이 붙는다
        assertThat(b.raw()).extracting(VisitBuffer.RawView::referrerGroup).containsExactly("SEARCH", null);
    }

    @Test
    void utm_이_있으면_주소와_상관없이_캠페인() {
        collector.accept(body("/", null, "https://www.facebook.com/", "newsletter"), UA, HOST);
        VisitBuffer.Batch b = collector.buffer().drain();
        assertThat(b.referrers().keySet()).extracting(VisitBuffer.ReferrerKey::group, VisitBuffer.ReferrerKey::domain)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("CAMPAIGN", ""));
        assertThat(b.raw().peekFirst().utmSource()).isEqualTo("newsletter");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.google.com/search?q=x|SEARCH|www.google.com",
            "https://www.google.co.kr/|SEARCH|www.google.co.kr",
            "https://search.naver.com/search.naver|SEARCH|search.naver.com",
            "https://m.search.daum.net/search|SEARCH|m.search.daum.net",
            "https://www.bing.com/|SEARCH|www.bing.com",
            "https://blog.naver.com/abc/123|SOCIAL|blog.naver.com",
            "https://m.blog.naver.com/abc|SOCIAL|m.blog.naver.com",
            "https://cafe.naver.com/x|SOCIAL|cafe.naver.com",
            "https://m.facebook.com/|SOCIAL|m.facebook.com",
            "https://t.co/abc|SOCIAL|t.co",
            "https://www.youtube.com/watch?v=1|SOCIAL|www.youtube.com",
            "https://other.example.org/page|OTHER_SITE|other.example.org",
            "HTTPS://WWW.Example.COM./x|OTHER_SITE|www.example.com",
            "|DIRECT|",
            "javascript:alert(1)|DIRECT|",
            "ftp://files.example.com/|DIRECT|",
            "https://roboyam.test/board|DIRECT|"})
    void 유입_주소를_검색_SNS_직접_다른_사이트로_가른다(String line) {
        String[] p = line.split("\\|", -1);
        ReferrerClassifier.Referrer r = ReferrerClassifier.classify(p[0].isEmpty() ? null : p[0], false, HOST);
        assertThat(r.group()).isEqualTo(p[1]);
        assertThat(r.domain()).isEqualTo(p[2].isEmpty() ? null : p[2]);
    }
}
