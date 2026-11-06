package com.community.analytics.collect;

import static com.community.analytics.collect.AnalyticsTestSupport.IDS;
import static com.community.analytics.collect.AnalyticsTestSupport.INDEX;
import static com.community.analytics.collect.AnalyticsTestSupport.settings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.analytics.settings.StatsCollection;
import com.community.search.controller.SearchController;
import com.community.search.dto.response.SearchResponse;
import com.community.search.service.SearchService;
import com.community.site.config.AdminPreviewFrame;
import com.community.site.config.SiteHeadFilter;
import com.community.site.dto.PublicSiteSnapshot;
import com.community.site.dto.RenderedIndex;
import com.community.site.dto.response.SiteAnalyticsView;
import com.community.site.service.IndexHtmlRenderer;
import com.community.site.service.PublicSiteService;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 방문자 통계 2단계(site-analytics-contract 「2단계」)의 수집 규칙 — DB 없이 수집기의 메모리 모음({@link VisitBuffer})을 본다.
 * 환경(세션 첫 페이지·허용 목록·화면 구간·기기) · 검색어(첫 쪽만·다듬기·거르기) · 봇(종류·화면 HTML 요청 하나 = 1) · 무시한 신호 수 · 공개 사이트 스위치({@code analytics.environment}).
 */
class VisitStage2CollectTest {

    private static final String WIN_CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
    private static final String MAC_SAFARI = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15";
    private static final String IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
    private static final String IPAD_OLD = "Mozilla/5.0 (iPad; CPU OS 12_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/12.1 Mobile/15E148 Safari/604.1";
    private static final String ANDROID_PHONE = "Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/24.0 Chrome/117.0 Mobile Safari/537.36";
    private static final String ANDROID_TABLET = "Mozilla/5.0 (Linux; Android 13; SM-X710) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    private static final String HOST = "roboyam.test";

    /** 2단계 세 칸까지 모두 켬 */
    static final StatsCollection ALL2 = new StatsCollection(true, true, true, 90, true, true, true, true, true, false, false);

    private final AtomicReference<StatsCollection> setting = new AtomicReference<>(ALL2);
    private final VisitCollector collector = new VisitCollector(settings(setting), INDEX, IDS);

    private static String signal(String prevPath, String env) {
        return "{\"visitorId\":\"" + UUID.randomUUID() + "\",\"sessionId\":\"" + UUID.randomUUID() + "\",\"path\":\"/board/free\",\"kind\":\"BOARD\","
                + "\"prevPath\":" + (prevPath == null ? "null" : "\"" + prevPath + "\"") + ",\"newVisitor\":false" + (env == null ? "" : ",\"env\":" + env) + "}";
    }

    private static String env(String browser, String os, String screen) {
        return "{\"browser\":\"" + browser + "\",\"os\":\"" + os + "\",\"screen\":\"" + screen + "\"}";
    }

    private static LocalDate today() {
        return ServiceTime.dateOf(Instant.now());
    }

    /** 한 묶음의 환경 칸 하나만 있어야 한다 — 그 키 */
    private static VisitBuffer.EnvKey onlyEnv(VisitBuffer.Batch b) {
        assertThat(b.env()).hasSize(1);
        Map.Entry<VisitBuffer.EnvKey, Long> e = b.env().entrySet().iterator().next();
        assertThat(e.getValue()).isEqualTo(1);
        return e.getKey();
    }

    // ── 1. 환경 ─────────────────────────────────────────────────────────────

    @Test
    void 세션_첫_페이지이고_환경이_켜졌을_때만_환경_1() {
        assertThat(collector.accept(signal(null, env("CHROME", "WINDOWS", "W1440")), WIN_CHROME, HOST)).isTrue();
        VisitBuffer.EnvKey k = onlyEnv(collector.buffer().drain());
        assertThat(k).isEqualTo(new VisitBuffer.EnvKey(k.day(), "DESKTOP", "CHROME", "WINDOWS", "W1440"));

        // 둘째 페이지(직전 경로 있음) — env 를 실어 보내도 세지 않는다
        assertThat(collector.accept(signal("/", env("CHROME", "WINDOWS", "W1440")), WIN_CHROME, HOST)).isTrue();
        VisitBuffer.Batch second = collector.buffer().drain();
        assertThat(second.env()).isEmpty();
        assertThat(second.visitors()).hasSize(1);

        // 환경만 꺼짐 — 나머지는 그대로 센다
        setting.set(new StatsCollection(true, true, true, 90, true, true, false, true, true, false, false));
        assertThat(collector.accept(signal(null, env("CHROME", "WINDOWS", "W1440")), WIN_CHROME, HOST)).isTrue();
        VisitBuffer.Batch off = collector.buffer().drain();
        assertThat(off.env()).isEmpty();
        assertThat(off.visitors()).hasSize(1);

        // 방문 수집 전체가 꺼지면 환경이 켜져 있어도 아무것도
        setting.set(new StatsCollection(false, true, true, 90, true, true, true, true, true, false, false));
        assertThat(collector.accept(signal(null, env("CHROME", "WINDOWS", "W1440")), WIN_CHROME, HOST)).isFalse();
        assertThat(collector.buffer().drain().isEmpty()).isTrue();
    }

    @Test
    void 환경만_켜도_신호를_받아_환경만_센다() {
        setting.set(new StatsCollection(true, false, false, 90, false, false, true, false, false, false, false));
        assertThat(collector.accept(signal(null, env("SAFARI", "IOS", "W480")), IPHONE, HOST)).isTrue();
        VisitBuffer.Batch b = collector.buffer().drain();
        assertThat(onlyEnv(b).device()).isEqualTo("MOBILE");
        assertThat(b.visitors()).isEmpty();
        assertThat(b.raw()).isEmpty();
        assertThat(b.referrers()).isEmpty();
        assertThat(b.flows()).isEmpty();
    }

    @Test
    void 허용_목록_밖의_브라우저와_OS_는_OTHER() {
        collector.accept(signal(null, env("OPERA", "BEOS", "W768")), WIN_CHROME, HOST);
        VisitBuffer.EnvKey k = onlyEnv(collector.buffer().drain());
        assertThat(k.browser()).isEqualTo("OTHER");
        assertThat(k.os()).isEqualTo("OTHER");
        assertThat(k.screen()).isEqualTo("W768");
        // 아주 긴 값도 OTHER(잘라서 허용 목록에 맞춰지지 않는다)
        collector.accept(signal(null, env("CHROMEXXXXXXXXXXXXXXXX", "WINDOWS95", "WIDE")), WIN_CHROME, HOST);
        VisitBuffer.EnvKey k2 = onlyEnv(collector.buffer().drain());
        assertThat(k2.browser()).isEqualTo("OTHER");
        assertThat(k2.os()).isEqualTo("OTHER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"browser\":\"CHROME\",\"os\":\"WINDOWS\",\"screen\":\"W2000\"}", "{\"browser\":\"CHROME\",\"os\":\"WINDOWS\"}",
            "{\"browser\":\"CHROME\",\"os\":\"WINDOWS\",\"screen\":1440}", "\"CHROME/WINDOWS/W1440\"", "[\"CHROME\"]", "null"})
    void 화면_구간이_목록_밖이거나_env_가_객체가_아니면_환경만_버리고_신호는_받는다(String env) {
        assertThat(collector.accept(signal(null, env), WIN_CHROME, HOST)).isTrue();
        VisitBuffer.Batch b = collector.buffer().drain();
        assertThat(b.env()).isEmpty();
        assertThat(b.visitors()).hasSize(1);
        assertThat(b.raw()).hasSize(1);
        assertThat(b.ignoredInvalid()).as("형식 오류로 세지 않는다 — 신호는 받았다").isZero();
    }

    @Test
    void 공개_신호_API_는_환경이_틀려도_204() throws Exception {
        org.springframework.test.web.servlet.MockMvc mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new com.community.analytics.controller.VisitController(collector)).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/visits")
                        .contentType(org.springframework.http.MediaType.TEXT_PLAIN).content(signal(null, env("CHROME", "WINDOWS", "W9999"))).header("User-Agent", WIN_CHROME))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());
        VisitBuffer.Batch b = collector.buffer().drain();
        assertThat(b.env()).isEmpty();
        assertThat(b.visitors()).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36|WINDOWS|DESKTOP",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15|MACOS|DESKTOP",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15|IOS|TABLET",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1|IOS|MOBILE",
            "Mozilla/5.0 (iPad; CPU OS 12_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/12.1 Mobile/15E148 Safari/604.1|IOS|TABLET",
            "Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/24.0 Chrome/117.0 Mobile Safari/537.36|ANDROID|MOBILE",
            "Mozilla/5.0 (Linux; Android 13; SM-X710) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36|ANDROID|TABLET",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36|LINUX|DESKTOP"})
    void 기기는_서버가_UA_로_가르고_맥_UA_에_iOS_면_태블릿(String ua, String os, String device) {
        assertThat(VisitCollector.device(ua, os)).isEqualTo(device);
        collector.accept(signal(null, env("SAFARI", os, "W1024")), ua, HOST);
        assertThat(onlyEnv(collector.buffer().drain()).device()).isEqualTo(device);
    }

    @Test
    void 브라우저가_보낸_기기_값은_믿지_않는다() {
        collector.accept("{\"visitorId\":\"" + UUID.randomUUID() + "\",\"sessionId\":\"" + UUID.randomUUID()
                + "\",\"path\":\"/\",\"kind\":\"HOME\",\"prevPath\":null,\"env\":{\"browser\":\"CHROME\",\"os\":\"WINDOWS\",\"screen\":\"W1440\",\"device\":\"MOBILE\"}}", WIN_CHROME, HOST);
        assertThat(onlyEnv(collector.buffer().drain()).device()).isEqualTo("DESKTOP");
    }

    // ── 3·4. 봇 · 무시한 신호 ─────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)|GOOGLE",
            "Mozilla/5.0 (Linux; Android 6.0.1; Nexus 5X Build/MMB29P) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 (compatible; Googlebot/2.1)|GOOGLE",
            "AdsBot-Google (+http://www.google.com/adsbot.html)|GOOGLE",
            "Mediapartners-Google|GOOGLE",
            "Mozilla/5.0 (compatible; Google-InspectionTool/1.0)|GOOGLE",
            "Mozilla/5.0 (compatible; Yeti/1.1; +https://naver.me/spd)|NAVER",
            "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)|BING",
            "Mozilla/5.0 (compatible; Daum/4.1; +http://cs.daum.net/faq/15/4118.html?faqId=28966)|DAUM",
            "Mozilla/5.0 (compatible; Daumoa/4.0)|DAUM",
            "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)|OTHER_CRAWLER",
            "Twitterbot/1.0|OTHER_CRAWLER",
            "Mozilla/5.0 (compatible; YandexBot/3.0; +http://yandex.com/bots)|OTHER_CRAWLER",
            "Mozilla/5.0 (compatible; AhrefsBot/7.0; +http://ahrefs.com/robot/)|OTHER_CRAWLER",
            "curl/8.4.0|TOOL",
            "Wget/1.21.4|TOOL",
            "python-requests/2.31.0|TOOL",
            "Go-http-client/1.1|TOOL",
            "okhttp/4.12.0|TOOL",
            "axios/1.6.0|TOOL",
            "Java/21.0.1|TOOL",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/120.0 Safari/537.36|TOOL",
            "Mozilla/5.0 (Windows NT 10.0) Chrome-Lighthouse|TOOL"})
    void 봇_종류를_가른다(String ua, String kind) {
        assertThat(BotClassifier.kind(ua)).isEqualTo(kind);
        assertThat(BotClassifier.isBot(ua)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {WIN_CHROME, MAC_SAFARI, IPHONE, IPAD_OLD, ANDROID_PHONE, ANDROID_TABLET,
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Whale/3.24.223.18 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36 Edg/120.0",
            "Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 NAVER(inapp; search; 2000; 12.0.1)",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 DaumApps/7.0.0",
            "Mozilla/5.0 (Windows NT 10.0; rv:121.0) Gecko/20100101 Firefox/121.0"})
    void 사람_브라우저는_봇이_아니다(String ua) {
        assertThat(BotClassifier.kind(ua)).isNull();
        assertThat(BotClassifier.isBot(ua)).isFalse();
    }

    @Test
    void 빈_UA_는_TOOL_이고_긴_UA_는_앞_512자만_본다() {
        assertThat(BotClassifier.kind(null)).isEqualTo("TOOL");
        assertThat(BotClassifier.kind("   ")).isEqualTo("TOOL");
        assertThat(BotClassifier.kind("Mozilla/5.0 " + "x".repeat(600) + " Googlebot")).isNull();
        assertThat(BotClassifier.kind("Googlebot " + "x".repeat(600))).isEqualTo("GOOGLE");
    }

    @Test
    void 방문_신호에서_네이버_Yeti_와_다음은_봇으로_빠지고_무시한_봇_신호로_센다() {
        assertThat(collector.accept(signal(null, null), "Mozilla/5.0 (compatible; Yeti/1.1; +https://naver.me/spd)", HOST)).isFalse();
        assertThat(collector.accept(signal(null, null), "Mozilla/5.0 (compatible; Daum/4.1; +http://cs.daum.net/faq/15/4118.html?faqId=28966)", HOST)).isFalse();
        assertThat(collector.accept(signal(null, null), "Googlebot/2.1", HOST)).isFalse();
        assertThat(collector.accept(signal(null, null), null, HOST)).isFalse();
        assertThat(collector.accept("not-json", WIN_CHROME, HOST)).isFalse();
        assertThat(collector.accept("{}", WIN_CHROME, HOST)).isFalse();
        assertThat(collector.accept("x".repeat(VisitCollector.BODY_MAX + 1), WIN_CHROME, HOST)).isFalse();
        VisitBuffer.Batch b = collector.buffer().drain();
        assertThat(b.isEmpty()).isTrue();
        assertThat(b.ignoredBot()).isEqualTo(4);
        assertThat(b.ignoredInvalid()).isEqualTo(3);
        VisitBuffer.Batch next = collector.buffer().drain();
        assertThat(next.ignoredBot()).as("넘긴 뒤 0 부터").isZero();
        assertThat(next.ignoredInvalid()).isZero();
    }

    @Test
    void 방문_수집이_꺼져_있으면_무시한_신호도_세지_않는다() {
        // 검색어·봇만 켬 — 방문 신호를 하나도 쓰지 않는다(anyVisitOn false)
        setting.set(new StatsCollection(true, false, false, 90, false, false, false, true, true, false, false));
        collector.accept(signal(null, null), "Googlebot/2.1", HOST);
        collector.accept("not-json", WIN_CHROME, HOST);
        VisitBuffer.Batch b = collector.buffer().drain();
        assertThat(b.ignoredBot()).isZero();
        assertThat(b.ignoredInvalid()).isZero();
    }

    @Test
    void 봇_요청은_봇이_켜졌을_때만_종류별로_센다() {
        collector.countBotPage("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)");
        collector.countBotPage("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)");
        collector.countBotPage("Mozilla/5.0 (compatible; Yeti/1.1; +https://naver.me/spd)");
        collector.countBotPage("curl/8.4.0");
        collector.countBotPage(null);
        collector.countBotPage(WIN_CHROME); // 사람 — 세지 않는다
        VisitBuffer.Batch b = collector.buffer().drain();
        LocalDate d = today();
        assertThat(b.bots()).containsExactlyInAnyOrderEntriesOf(Map.of(new VisitBuffer.BotKey(d, "GOOGLE"), 2L, new VisitBuffer.BotKey(d, "NAVER"), 1L,
                new VisitBuffer.BotKey(d, "TOOL"), 2L));

        setting.set(new StatsCollection(true, true, true, 90, true, true, true, true, false, false, false));
        collector.countBotPage("Googlebot/2.1");
        setting.set(new StatsCollection(false, true, true, 90, true, true, true, true, true, false, false));
        collector.countBotPage("Googlebot/2.1");
        assertThat(collector.buffer().drain().bots()).isEmpty();
    }

    // ── 3. 봇 셈 지점 — SiteHeadFilter ─────────────────────────────────────

    /** 머리를 채운 index.html 을 늘 같은 본문·ETag 로 */
    static final class FixedRenderer extends IndexHtmlRenderer {

        final boolean present;

        FixedRenderer(boolean present) {
            this.present = present;
        }

        @Override
        public Optional<RenderedIndex> render(PublicSiteSnapshot snapshot) {
            return present ? Optional.of(new RenderedIndex("<html></html>".getBytes(StandardCharsets.UTF_8), "qa-etag")) : Optional.empty();
        }
    }

    static final class NullSiteService extends PublicSiteService {

        NullSiteService() {
            super(null, null, null, null, null);
        }

        @Override
        public PublicSiteSnapshot snapshot() {
            return null;
        }
    }

    private SiteHeadFilter filter(boolean rendered) {
        return new SiteHeadFilter(new NullSiteService(), new FixedRenderer(rendered), new AdminPreviewFrame(""), collector);
    }

    private static MockHttpServletRequest page(String method, String path, String ua) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, path);
        r.setServletPath(path);
        if (ua != null) {
            r.addHeader("User-Agent", ua);
        }
        return r;
    }

    private long botRequests() {
        return collector.buffer().drain().bots().values().stream().mapToLong(Long::longValue).sum();
    }

    @Test
    void 화면_HTML_요청_하나가_봇_1이고_HEAD_와_304_도_센다() throws Exception {
        String google = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";
        SiteHeadFilter f = filter(true);
        for (String path : List.of("/", "/posts/1", "/board/free", "/index.html")) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            f.doFilter(page("GET", path, google), res, new MockFilterChain());
            assertThat(res.getStatus()).isEqualTo(200);
        }
        assertThat(botRequests()).isEqualTo(4);

        MockHttpServletResponse head = new MockHttpServletResponse();
        f.doFilter(page("HEAD", "/", google), head, new MockFilterChain());
        assertThat(head.getContentAsByteArray()).isEmpty();
        MockHttpServletRequest cond = page("GET", "/", google);
        cond.addHeader("If-None-Match", "\"qa-etag\"");
        MockHttpServletResponse notModified = new MockHttpServletResponse();
        f.doFilter(cond, notModified, new MockFilterChain());
        assertThat(notModified.getStatus()).isEqualTo(304);
        assertThat(botRequests()).isEqualTo(2);

        // 화면 파일이 없어도(빌드 전) 화면 요청이면 센다
        filter(false).doFilter(page("GET", "/", google), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(botRequests()).isEqualTo(1);
    }

    @Test
    void API_정적_파일_POST_사람_요청은_봇으로_세지_않는다() throws Exception {
        String google = "Googlebot/2.1";
        SiteHeadFilter f = filter(true);
        for (String path : List.of("/api/search", "/api", "/api/site/info", "/media/a.png", "/_nuxt/app.js", "/favicon.ico", "/robots.txt", "/sitemap.xml",
                "/manifest.webmanifest", "/actuator/health", "/.well-known/security.txt")) {
            f.doFilter(page("GET", path, google), new MockHttpServletResponse(), new MockFilterChain());
        }
        f.doFilter(page("POST", "/", google), new MockHttpServletResponse(), new MockFilterChain());
        f.doFilter(page("GET", "/", WIN_CHROME), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(botRequests()).isZero();
    }

    @Test
    void 봇이_꺼져_있으면_화면_요청도_세지_않는다() throws Exception {
        setting.set(new StatsCollection(true, true, true, 90, true, true, true, true, false, false, false));
        filter(true).doFilter(page("GET", "/", "Googlebot/2.1"), new MockHttpServletResponse(), new MockFilterChain());
        assertThat(botRequests()).isZero();
    }

    // ── 2. 검색어 ───────────────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"  RTX   4090  |rtx 4090", "Hello\tWorld|hello world", "AB|ab", "그래픽카드|그래픽카드", "  가  나  |가 나",
            "Ｆｕｌｌ　ｗｉｄｔｈ|ｆｕｌｌ ｗｉｄｔｈ", "iphone 15|iphone 15", "2024|2024", "rtx-4090-ti|rtx-4090-ti", "010|010"})
    void 다듬기는_앞뒤_공백_연속_공백_소문자(String raw, String expected) {
        assertThat(VisitCollector.normalizeTerm(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "  a  ", " ", "", "가", "foo@bar.com", "문의 foo.bar@example.co.kr 부탁", "010-1234-5678", "01012345678", "010 1234 5678",
            "+82 10-1234-5678", "+821012345678", "02-123-4567", "(02)1234-5678", "031.123.4567", "900101-1234567", "9001011234567", "주민 900101 2234567",
            "ab\u0000cd", "ab\u0007cd", "ab\u001Fcd"})
    void 짧거나_개인정보_모양이거나_제어_문자면_저장하지_않는다(String raw) {
        assertThat(VisitCollector.normalizeTerm(raw)).isNull();
    }

    @Test
    void 길이는_코드_포인트로_2자_이상_50자_이하() {
        String emoji = "😀"; // 한 글자(코드 포인트), char 2개
        assertThat(VisitCollector.normalizeTerm(emoji.repeat(50))).isEqualTo(emoji.repeat(50));
        assertThat(VisitCollector.normalizeTerm(emoji.repeat(51))).isNull();
        assertThat(VisitCollector.normalizeTerm(emoji)).isNull();
        assertThat(VisitCollector.normalizeTerm(emoji.repeat(2))).isEqualTo(emoji.repeat(2));
        assertThat(VisitCollector.normalizeTerm("가".repeat(50))).hasSize(50);
        assertThat(VisitCollector.normalizeTerm("가".repeat(51))).isNull();
        assertThat(VisitCollector.normalizeTerm("  " + "a".repeat(50) + "   ")).as("다듬은 뒤 길이").hasSize(50);
    }

    @Test
    void 검색은_다듬은_검색어_칸으로_모으고_걸러진_것은_수만_결과_0건은_따로() {
        collector.countSearch("RTX 4090", false, WIN_CHROME);
        collector.countSearch("  rtx   4090 ", true, WIN_CHROME);
        collector.countSearch("RtX 4090", true, WIN_CHROME);
        collector.countSearch("010-1234-5678", false, WIN_CHROME);
        collector.countSearch("a", true, WIN_CHROME);
        collector.countSearch("foo@bar.com", true, WIN_CHROME);
        collector.countSearch("rtx 4090", false, "Googlebot/2.1"); // 봇
        collector.countSearch("rtx 4090", false, null); // 빈 UA = 봇
        collector.countSearch("rtx 4090", false, "Mozilla/5.0 (compatible; Yeti/1.1; +https://naver.me/spd)");
        collector.countSearch(null, false, WIN_CHROME);
        VisitBuffer.Batch b = collector.buffer().drain();
        LocalDate d = today();
        assertThat(b.searches()).containsExactlyEntriesOf(Map.of(new VisitBuffer.SearchKey(d, "rtx 4090"), new VisitBuffer.SearchCount(3, 2)));
        assertThat(b.searchFiltered()).containsExactlyEntriesOf(Map.of(d, 3L));
    }

    @Test
    void 검색어가_꺼져_있거나_방문_수집_전체가_꺼지면_세지_않는다() {
        setting.set(new StatsCollection(true, true, true, 90, true, true, true, false, true, false, false));
        collector.countSearch("rtx 4090", false, WIN_CHROME);
        collector.countSearch("a", false, WIN_CHROME);
        setting.set(new StatsCollection(false, true, true, 90, true, true, true, true, true, false, false));
        collector.countSearch("rtx 4090", false, WIN_CHROME);
        VisitBuffer.Batch b = collector.buffer().drain();
        assertThat(b.searches()).isEmpty();
        assertThat(b.searchFiltered()).isEmpty();
    }

    // ── 2. 검색어 셈 지점 — SearchController(첫 쪽 성공만) ───────────────────

    /** 검색 서비스 흉내 — 정해 둔 응답을 주거나 던진다 */
    static final class FakeSearchService extends SearchService {

        Supplier<SearchResponse> next;

        FakeSearchService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public SearchResponse search(String qRaw, String scopeRaw, String boardRaw, String orderRaw, String pageRaw, String cursorRaw, String sizeRaw, Long viewerId) {
            return next.get();
        }
    }

    private static SearchResponse result(Integer page, int items) {
        List<com.community.search.dto.response.SearchRowView> rows = items == 0 ? List.of() : java.util.Collections.nCopies(items, null);
        return new SearchResponse("q", List.of("q"), "all", null, rows, page, page == null ? null : 3, false, null);
    }

    private static MockHttpServletRequest searchRequest(String ua) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/api/search");
        r.addHeader("User-Agent", ua);
        return r;
    }

    @Test
    void 검색_API_는_첫_쪽_성공_응답만_센다_커서_2쪽_오류는_세지_않는다() {
        FakeSearchService service = new FakeSearchService();
        SearchController controller = new SearchController(service, collector);

        service.next = () -> result(1, 2);
        controller.search("RTX 4090", null, null, null, null, null, null, null, searchRequest(WIN_CHROME));
        service.next = () -> result(1, 0);
        controller.search("없는말", null, null, null, "1", null, null, null, searchRequest(WIN_CHROME));
        service.next = () -> result(2, 2);
        controller.search("RTX 4090", null, null, null, "2", null, null, null, searchRequest(WIN_CHROME));
        service.next = () -> result(null, 2); // 커서 더보기
        controller.search("RTX 4090", null, null, null, null, "123", null, null, searchRequest(WIN_CHROME));
        service.next = () -> {
            throw ValidationException.of("q", new MessageRef("search.q.short"));
        };
        assertThatThrownBy(() -> controller.search("RTX 4090", null, null, null, null, null, null, null, searchRequest(WIN_CHROME)))
                .isInstanceOf(ValidationException.class);
        service.next = () -> result(1, 2);
        controller.search("RTX 4090", null, null, null, null, null, null, null, searchRequest("Googlebot/2.1")); // 봇

        VisitBuffer.Batch b = collector.buffer().drain();
        LocalDate d = today();
        assertThat(b.searches()).containsExactlyInAnyOrderEntriesOf(Map.of(new VisitBuffer.SearchKey(d, "rtx 4090"), new VisitBuffer.SearchCount(1, 0),
                new VisitBuffer.SearchKey(d, "없는말"), new VisitBuffer.SearchCount(1, 1)));
        assertThat(b.searchFiltered()).isEmpty();
    }

    // ── 6. 공개 사이트 스위치 ───────────────────────────────────────────────

    @Test
    void 사이트_정보의_analytics_environment_는_전체와_환경이_모두_켜졌을_때만() {
        assertThat(SiteAnalyticsView.of(ALL2).environment()).isTrue();
        assertThat(SiteAnalyticsView.of(new StatsCollection(false, true, true, 90, true, true, true, true, true, true, true)))
                .isEqualTo(new SiteAnalyticsView(false, false, false, false, false, false));
        assertThat(SiteAnalyticsView.of(new StatsCollection(true, false, false, 90, false, false, false, true, true, false, false)))
                .as("검색어·봇은 신호가 아니라 여기 없다").isEqualTo(new SiteAnalyticsView(true, false, false, false, false, false));
        assertThat(SiteAnalyticsView.of(new StatsCollection(true, false, false, 90, false, false, true, false, false, false, false)).environment()).isTrue();
    }
}
