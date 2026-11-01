package com.community.hotdeal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.hotdeal.og.DealPageFetcher.Image;
import com.community.hotdeal.og.DealPageFetcher.Outcome;
import com.community.hotdeal.og.DealPageFetcher.Page;
import com.community.hotdeal.og.SafeDealPageFetcher;
import com.community.notification.net.OutboundTargetGuard;
import com.community.notification.net.SafeHttpClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 서버 규칙 1(OG 가져오기 보안) — <b>외부 사이트를 부르지 않는다</b>: JDK 가짜 HTTP 서버(127.0.0.1)를 띄우고, 사설망 거절은 시험용 생성자로만 루프백 하나를 풀어 시험한다.
 * 운영 생성자는 설정({@code allow-private-targets})이 켜져 있어도 루프백을 막는다.
 */
class SafeDealPageFetcherTest {

    /** 시험용 — 루프백만 풀고 나머지는 운영과 같은 엄격 판정 */
    private static final java.util.function.Predicate<InetAddress> LOOPBACK_ONLY = a -> !a.isLoopbackAddress() && OutboundTargetGuard.strictlyBlocked(a);

    private HttpServer server;
    private String base;
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    private final OutboundTargetGuard guard = new OutboundTargetGuard(true); // 알림 설정은 사설망 허용 — 핫딜은 그래도 막아야 한다
    private final SafeHttpClient http = new SafeHttpClient(guard, 3000, 5000, 10000);
    private SafeDealPageFetcher fetcher;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.createContext("/", this::handle);
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        fetcher = new SafeDealPageFetcher(guard, http, new SafeDealPageFetcher.Limits(500, 400, 900), LOOPBACK_ONLY);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        hits.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
        try {
            switch (path) {
                case "/page" -> html(ex, "<html><head><meta property=\"og:title\" content=\"로봇 청소기 &amp; 물걸레\"><meta property=\"og:site_name\" content=\"테스트몰\">"
                        + "<meta property=\"og:image\" content=\"/img/a.png\"></head><body>본문</body></html>");
                case "/r1" -> redirect(ex, "/r2");
                case "/r2" -> redirect(ex, "/r3");
                case "/r3" -> redirect(ex, "/page");
                case "/loop1" -> redirect(ex, "/loop2");
                case "/loop2" -> redirect(ex, "/loop3");
                case "/loop3" -> redirect(ex, "/loop4");
                case "/loop4" -> redirect(ex, "/page");
                case "/to-private" -> redirect(ex, "http://10.0.0.1/secret");
                case "/to-metadata" -> redirect(ex, "http://169.254.169.254/latest/meta-data");
                case "/to-localhost" -> redirect(ex, "http://localhost:" + server.getAddress().getPort() + "/page");
                case "/json" -> {
                    byte[] body = "{\"og:image\":\"x\"}".getBytes(StandardCharsets.UTF_8);
                    ex.getResponseHeaders().set("Content-Type", "application/json");
                    ex.sendResponseHeaders(200, body.length);
                    ex.getResponseBody().write(body);
                }
                case "/huge" -> {
                    ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    ex.sendResponseHeaders(200, 0);
                    OutputStream out = ex.getResponseBody();
                    byte[] junk = "<!-- ................................................................ -->\n".repeat(64).getBytes(StandardCharsets.UTF_8);
                    int written = 0;
                    out.write("<html><head>".getBytes(StandardCharsets.UTF_8));
                    while (written < 2 * 1024 * 1024) {
                        out.write(junk);
                        written += junk.length;
                    }
                    out.write("<meta property=\"og:image\" content=\"http://127.0.0.1/late.png\"></head>".getBytes(StandardCharsets.UTF_8));
                }
                case "/head-then-junk" -> {
                    ex.getResponseHeaders().set("Content-Type", "text/html");
                    ex.sendResponseHeaders(200, 0);
                    OutputStream out = ex.getResponseBody();
                    out.write("<html><HEAD><meta property=\"og:title\" content=\"앞\"></HEAD><body>".getBytes(StandardCharsets.UTF_8));
                    out.write(new byte[800 * 1024]);
                }
                case "/slow-headers" -> {
                    sleep(2000);
                    html(ex, "<html><head></head></html>");
                }
                case "/trickle" -> {
                    ex.getResponseHeaders().set("Content-Type", "text/html");
                    ex.sendResponseHeaders(200, 0);
                    OutputStream out = ex.getResponseBody();
                    for (int i = 0; i < 40; i++) {
                        out.write(' ');
                        out.flush();
                        sleep(100);
                    }
                }
                case "/img/a.png" -> {
                    byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0, 0};
                    ex.getResponseHeaders().set("Content-Type", "image/png");
                    ex.sendResponseHeaders(200, png.length);
                    ex.getResponseBody().write(png);
                }
                case "/img/html" -> html(ex, "<html></html>");
                case "/img/huge" -> {
                    ex.getResponseHeaders().set("Content-Type", "image/jpeg");
                    ex.sendResponseHeaders(200, 0);
                    OutputStream out = ex.getResponseBody();
                    byte[] chunk = new byte[64 * 1024];
                    for (int i = 0; i < 6 * 16 + 1; i++) {
                        out.write(chunk);
                    }
                }
                default -> {
                    ex.sendResponseHeaders(404, -1);
                }
            }
        } catch (IOException ignored) {
            // 클라이언트가 먼저 끊었다(상한·시간 초과 시험)
        } finally {
            ex.close();
        }
    }

    private static void html(HttpExchange ex, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
    }

    private static void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().set("Location", location);
        ex.sendResponseHeaders(302, -1);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void 운영_생성자는_설정과_무관하게_사설망_루프백을_늘_거절한다() {
        SafeDealPageFetcher production = new SafeDealPageFetcher(guard, http);
        Page page = production.fetchPage(base + "/page", true);
        assertThat(page.outcome()).isEqualTo(Outcome.BLOCKED);
        assertThat(hits.get("/page")).isNull();
        assertThat(production.fetchImage(base + "/img/a.png").outcome()).isEqualTo(Outcome.BLOCKED);
        assertThat(OutboundTargetGuard.strictlyBlocked(InetAddress.getLoopbackAddress())).isTrue();
    }

    @Test
    void 규칙_값은_연결_2초_읽기_4초_전체_6초_리디렉션_3회_HTML_1MB() {
        assertThat(SafeDealPageFetcher.Limits.RULE).isEqualTo(new SafeDealPageFetcher.Limits(2000, 4000, 6000));
        assertThat(SafeDealPageFetcher.MAX_REDIRECTS).isEqualTo(3);
        assertThat(SafeDealPageFetcher.HTML_MAX_BYTES).isEqualTo(1024 * 1024);
    }

    @Test
    void 머리에서_OG_를_뽑고_그림_주소는_절대_주소로() {
        Page page = fetcher.fetchPage(base + "/page#frag", true);
        assertThat(page.outcome()).isEqualTo(Outcome.OK);
        assertThat(page.og().title()).isEqualTo("로봇 청소기 & 물걸레");
        assertThat(page.og().siteName()).isEqualTo("테스트몰");
        assertThat(page.og().imageUrl()).isEqualTo(base + "/img/a.png");
        assertThat(page.hosts()).containsExactly("127.0.0.1");
    }

    @Test
    void 리디렉션은_3회까지_따라가고_체인의_모든_호스트를_남긴다() {
        Page page = fetcher.fetchPage(base + "/r1", true);
        assertThat(page.outcome()).isEqualTo(Outcome.OK);
        assertThat(page.finalUrl()).isEqualTo(base + "/page");
        assertThat(hits.get("/page").get()).isEqualTo(1);

        Page viaName = fetcher.fetchPage(base + "/to-localhost", false);
        assertThat(viaName.outcome()).isEqualTo(Outcome.OK);
        assertThat(viaName.hosts()).containsExactly("127.0.0.1", "localhost");
    }

    @Test
    void 리디렉션이_3회를_넘으면_멈추고_네번째는_따라가지_않는다() {
        Page page = fetcher.fetchPage(base + "/loop1", true);
        assertThat(page.outcome()).isEqualTo(Outcome.TOO_MANY_REDIRECTS);
        assertThat(hits.get("/loop4").get()).isEqualTo(1);
        assertThat(hits.get("/page")).as("네 번째 리디렉션 대상은 열지 않는다").isNull();
    }

    @Test
    void 리디렉션_매_단계에서_주소를_다시_검사한다_사설망_메타데이터로_가면_막는다() {
        Page toPrivate = fetcher.fetchPage(base + "/to-private", true);
        assertThat(toPrivate.outcome()).isEqualTo(Outcome.BLOCKED);
        assertThat(toPrivate.hosts()).as("막힌 곳의 호스트도 판정 대상").containsExactly("127.0.0.1", "10.0.0.1");
        assertThat(fetcher.fetchPage(base + "/to-metadata", true).outcome()).isEqualTo(Outcome.BLOCKED);
    }

    @Test
    void text_html_이_아니면_본문을_읽지_않는다() {
        Page page = fetcher.fetchPage(base + "/json", true);
        assertThat(page.outcome()).isEqualTo(Outcome.NOT_HTML);
        assertThat(page.og()).isNull();
        assertThat(page.bodyBytes()).isZero();
    }

    @Test
    void 본문은_1MB_에서_자른다() {
        Page page = fetcher.fetchPage(base + "/huge", true);
        assertThat(page.outcome()).isEqualTo(Outcome.OK);
        assertThat(page.bodyBytes()).isEqualTo(SafeDealPageFetcher.HTML_MAX_BYTES);
        assertThat(page.og().imageUrl()).as("1MB 뒤의 OG 는 읽지 않는다").isNull();
    }

    @Test
    void head_가_끝나면_거기서_멈춘다() {
        Page page = fetcher.fetchPage(base + "/head-then-junk", true);
        assertThat(page.outcome()).isEqualTo(Outcome.OK);
        assertThat(page.og().title()).isEqualTo("앞");
        assertThat(page.bodyBytes()).isLessThan(200);
    }

    @Test
    void 머리를_읽지_않으면_본문을_받지_않는다() {
        Page page = fetcher.fetchPage(base + "/page", false);
        assertThat(page.outcome()).isEqualTo(Outcome.OK);
        assertThat(page.og()).isNull();
        assertThat(page.bodyBytes()).isZero();
    }

    @Test
    void 읽기_제한_시간을_넘기면_실패() {
        long started = System.nanoTime();
        Page page = fetcher.fetchPage(base + "/slow-headers", true);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        assertThat(page.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(elapsedMs).isLessThan(1500);
    }

    @Test
    void 조금씩_흘려도_전체_마감에서_끊는다() {
        long started = System.nanoTime();
        Page page = fetcher.fetchPage(base + "/trickle", true);
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        assertThat(page.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(elapsedMs).as("한 번 읽기는 400ms 안에 오지만 전체 900ms 에서 끊는다").isLessThan(2000);
    }

    @Test
    void 그림은_image_만_상한_5MB() {
        Image ok = fetcher.fetchImage(base + "/img/a.png");
        assertThat(ok.outcome()).isEqualTo(Outcome.OK);
        assertThat(ok.data()).hasSize(10);
        assertThat(fetcher.fetchImage(base + "/img/html").outcome()).isEqualTo(Outcome.FAILED);
        SafeDealPageFetcher roomy = new SafeDealPageFetcher(guard, http, new SafeDealPageFetcher.Limits(2000, 4000, 8000), LOOPBACK_ONLY);
        assertThat(roomy.fetchImage(base + "/img/huge").outcome()).as("5MB 넘음").isEqualTo(Outcome.FAILED);
        assertThat(fetcher.fetchImage("http://10.1.2.3/a.png").outcome()).isEqualTo(Outcome.BLOCKED);
    }
}
