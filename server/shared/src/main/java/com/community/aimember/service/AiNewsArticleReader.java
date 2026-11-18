package com.community.aimember.service;

import com.community.notification.net.OutboundTargetGuard;
import com.community.notification.net.OutboundTargetGuard.Target;
import com.community.notification.net.SafeHttpClient;
import com.community.notification.net.SafeHttpClient.FetchLimits;
import com.community.notification.net.SafeHttpClient.FetchResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI 기자 뉴스 글의 <b>원문 기사 읽기</b>(ai-news-reporter-contract 「기사체·원문 읽기」 2026-11-18) — 수집 항목의 원문 링크를 열어 본문 발췌({@link AiNewsArticleText})를 뽑는다.
 * RSS 요약만으로는 자료가 너무 짧아 모델이 빈자리를 사견으로 채웠다(사용자 2026-11-18). 기사 사진은 가져오지 않는다(사용자 2026-11-18 — 별도 저작물).
 *
 * <ul>
 *   <li>요청은 알림·핫딜과 같은 SSRF 방어 클라이언트({@link SafeHttpClient} + {@link OutboundTargetGuard#strictlyBlocked}) — 사설망·메타데이터 주소는 막는다.
 *       리디렉션 최대 {@value #MAX_REDIRECTS}회(매 단계 다시 검사), {@code text/html} 만 {@value #HTML_MAX_BYTES} 바이트까지, 전체 {@value #TOTAL_MILLIS}ms.</li>
 *   <li>구글 뉴스 검색 링크(키워드 출처)는 원문이 아니라 건너뛴다.</li>
 *   <li>실패해도 던지지 않는다 — 빈 결과면 예전처럼 RSS 요약만으로 쓴다.</li>
 *   <li>본문 발췌는 모델에 주는 자료일 뿐 글에 옮기지 않는다(프롬프트가 자기 말로 요약하게 한다 — 저작권 「정당한 인용」).</li>
 * </ul>
 */
@Component
public class AiNewsArticleReader {

    private static final Logger log = LoggerFactory.getLogger(AiNewsArticleReader.class);

    static final int MAX_REDIRECTS = 3;
    static final int HTML_MAX_BYTES = 1024 * 1024;
    static final int CONNECT_MILLIS = 3000;
    static final int READ_MILLIS = 5000;
    static final int TOTAL_MILLIS = 10_000;
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; irobit-news/1.0)";
    private static final Pattern META_CHARSET = Pattern.compile("<meta[^>]+charset\\s*=\\s*[\"']?([a-zA-Z0-9_-]+)", Pattern.CASE_INSENSITIVE);

    /** 읽은 원문 — 본문 발췌(없으면 null) */
    public record Article(String text) {

        public static final Article EMPTY = new Article(null);
    }

    private final OutboundTargetGuard guard;
    private final SafeHttpClient http;

    public AiNewsArticleReader(OutboundTargetGuard guard, SafeHttpClient http) {
        this.guard = guard;
        this.http = http;
    }

    /** 원문을 읽는다 — 못 읽으면 {@link Article#EMPTY} */
    public Article read(String url) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://")) || skipped(url)) {
            return Article.EMPTY;
        }
        try {
            return fetch(url).map(page -> new Article(AiNewsArticleText.extract(decode(page.body(), page.charset())))).orElse(Article.EMPTY);
        } catch (RuntimeException e) {
            log.info("뉴스 원문을 읽지 못했다(요약만으로 쓴다) host={} 원인={}", hostOf(url), e.getClass().getSimpleName());
            return Article.EMPTY;
        }
    }

    /** 원문이 아닌 주소 — 구글 뉴스 검색 결과(키워드 출처)는 리디렉션 페이지라 읽지 않는다 */
    static boolean skipped(String url) {
        String host = hostOf(url);
        return host == null || host.equals("news.google.com") || host.endsWith(".news.google.com");
    }

    private record Page(String url, byte[] body, String charset) {
    }

    private Optional<Page> fetch(String url) {
        long deadline = System.nanoTime() + TOTAL_MILLIS * 1_000_000L;
        String current = url;
        for (int hop = 0; ; hop++) {
            Target target;
            try {
                target = guard.resolve(withoutFragment(current), OutboundTargetGuard::strictlyBlocked);
            } catch (OutboundTargetGuard.BlockedTargetException e) {
                return Optional.empty();
            }
            FetchResponse response;
            try {
                response = http.get(target, headers(), new FetchLimits(CONNECT_MILLIS, READ_MILLIS, deadline, HTML_MAX_BYTES, "text/html"::equals, null));
            } catch (IOException e) {
                return Optional.empty();
            }
            int status = response.status();
            if (status >= 300 && status < 400 && response.location() != null) {
                String next = absolute(current, response.location());
                if (next == null || hop + 1 > MAX_REDIRECTS) {
                    return Optional.empty();
                }
                current = next;
                continue;
            }
            if (status < 200 || status >= 300 || !"text/html".equals(response.contentType()) || response.body().length == 0 || !identity(response.contentEncoding())) {
                return Optional.empty();
            }
            return Optional.of(new Page(current, response.body(), response.charset()));
        }
    }

    /** 응답 문자 집합 → HTML 의 meta charset → UTF-8 차례(국내 언론사는 아직 EUC-KR 이 있다) */
    static String decode(byte[] body, String headerCharset) {
        Charset cs = charset(headerCharset);
        if (cs == null) {
            String head = new String(body, 0, Math.min(body.length, 4096), StandardCharsets.ISO_8859_1);
            Matcher m = META_CHARSET.matcher(head);
            cs = m.find() ? charset(m.group(1)) : null;
        }
        return new String(body, cs == null ? StandardCharsets.UTF_8 : cs);
    }

    private static Charset charset(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Charset.forName(name.strip());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1");
        headers.put("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.5");
        return headers;
    }

    private static boolean identity(String encoding) {
        return encoding == null || encoding.isBlank() || "identity".equals(encoding);
    }

    private static String withoutFragment(String url) {
        int hash = url.indexOf('#');
        return hash < 0 ? url : url.substring(0, hash);
    }

    /** Location → 절대 주소(http·https 만). 아니면 null */
    private static String absolute(String base, String location) {
        try {
            URI resolved = new URI(base).resolve(location.strip());
            String scheme = resolved.getScheme() == null ? null : resolved.getScheme().toLowerCase(Locale.ROOT);
            return "http".equals(scheme) || "https".equals(scheme) ? resolved.toString() : null;
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    private static String hostOf(String url) {
        try {
            String host = new URI(url).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
