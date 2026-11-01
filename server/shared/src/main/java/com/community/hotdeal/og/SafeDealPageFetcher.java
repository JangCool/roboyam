package com.community.hotdeal.og;

import com.community.hotdeal.service.DealHosts;
import com.community.notification.net.OutboundTargetGuard;
import com.community.notification.net.OutboundTargetGuard.Target;
import com.community.notification.net.SafeHttpClient;
import com.community.notification.net.SafeHttpClient.FetchLimits;
import com.community.notification.net.SafeHttpClient.FetchResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 상품 링크 가져오기의 운영 구현(서버 규칙 1·2) — {@link OutboundTargetGuard}·{@link SafeHttpClient} 를 그대로 쓴다(검사한 IP 로만 연결 · SNI · 헤더 주입 거절 · 전체 마감).
 *
 * <ul>
 *   <li><b>사설망 늘 거절</b>: 설정({@code roboyam.notification.allow-private-targets})과 무관하게 {@link OutboundTargetGuard#strictlyBlocked} 로 막는다.
 *       풀어 주는 길은 시험용 생성자({@link #SafeDealPageFetcher(OutboundTargetGuard, SafeHttpClient, Limits, Predicate)})뿐이고 설정 값이 없다.</li>
 *   <li><b>리디렉션</b>: 최대 {@value #MAX_REDIRECTS}회, 매 단계 새 주소를 다시 검사(모양·DNS·주소)한다. 따라간 모든 호스트가 판정(규칙 2) 대상이다 — 막혔거나 3회를 넘어
 *       따라가지 못한 마지막 주소의 호스트도 체인에 넣는다(보수적으로).</li>
 *   <li><b>본문</b>: {@code text/html} 만, {@value #HTML_MAX_BYTES} 바이트까지, {@code </head>} 가 나오면 멈춘다. 그림은 {@code image/*} 만 {@value #IMAGE_MAX_BYTES} 바이트까지(넘으면 실패).</li>
 *   <li><b>시간</b>: 연결 2초 · 읽기 4초 · 전체 6초(리디렉션 모든 단계가 한 마감을 나눠 쓴다). 그림은 따로 6초.</li>
 * </ul>
 * 결과는 짧은 사유({@link DealPageFetcher.Outcome})뿐이고 받은 본문·주소를 로그에 남기지 않는다.
 */
@Component
public class SafeDealPageFetcher implements DealPageFetcher {

    public static final int MAX_REDIRECTS = 3;
    public static final int HTML_MAX_BYTES = 1024 * 1024;
    public static final int IMAGE_MAX_BYTES = 5 * 1024 * 1024;
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; roboyam-linkcheck/1.0)";

    /** 시간 한도(밀리초) */
    public record Limits(int connectMillis, int readMillis, int totalMillis) {

        /** 서버 규칙 1 — 연결 2초 · 읽기 4초 · 전체 6초 */
        public static final Limits RULE = new Limits(2000, 4000, 6000);
    }

    private final OutboundTargetGuard guard;
    private final SafeHttpClient http;
    private final Limits limits;
    private final Predicate<InetAddress> blocked;

    @Autowired
    public SafeDealPageFetcher(OutboundTargetGuard guard, SafeHttpClient http) {
        this(guard, http, Limits.RULE, OutboundTargetGuard::strictlyBlocked);
    }

    /** 시험용 — 시간 한도와 막을 주소를 바꾼다(운영은 위 생성자만 쓴다) */
    public SafeDealPageFetcher(OutboundTargetGuard guard, SafeHttpClient http, Limits limits, Predicate<InetAddress> blocked) {
        this.guard = guard;
        this.http = http;
        this.limits = limits;
        this.blocked = blocked;
    }

    @Override
    public Page fetchPage(String url, boolean readHead) {
        long deadline = System.nanoTime() + limits.totalMillis() * 1_000_000L;
        List<String> hosts = new ArrayList<>();
        String current = url;
        addHost(hosts, current);
        for (int hop = 0; ; hop++) {
            Target target;
            try {
                target = guard.resolve(withoutFragment(current), blocked);
            } catch (OutboundTargetGuard.BlockedTargetException e) {
                Outcome outcome = e.reason() == OutboundTargetGuard.Reason.ADDRESS ? Outcome.BLOCKED : Outcome.FAILED;
                return new Page(hosts, current, outcome, null, 0);
            }
            FetchResponse response;
            try {
                response = http.get(target, headers("text/html,application/xhtml+xml;q=0.9,*/*;q=0.1"), new FetchLimits(limits.connectMillis(), limits.readMillis(),
                        deadline, readHead ? HTML_MAX_BYTES : 0, "text/html"::equals, "</head>"));
            } catch (IOException | RuntimeException e) {
                return new Page(hosts, current, Outcome.FAILED, null, 0);
            }
            int status = response.status();
            if (status >= 300 && status < 400 && response.location() != null) {
                String next = absolute(current, response.location());
                if (next == null) {
                    return new Page(hosts, current, Outcome.FAILED, null, 0);
                }
                addHost(hosts, next);
                if (hop + 1 > MAX_REDIRECTS) {
                    return new Page(hosts, next, Outcome.TOO_MANY_REDIRECTS, null, 0);
                }
                current = next;
                continue;
            }
            if (status < 200 || status >= 300) {
                return new Page(hosts, current, Outcome.FAILED, null, 0);
            }
            if (!"text/html".equals(response.contentType())) {
                return new Page(hosts, current, Outcome.NOT_HTML, null, 0);
            }
            OgTags og = readHead && isIdentity(response.contentEncoding()) ? OgParser.parse(response.body(), response.charset(), current) : null;
            return new Page(hosts, current, Outcome.OK, og, response.body().length);
        }
    }

    @Override
    public Image fetchImage(String url) {
        long deadline = System.nanoTime() + limits.totalMillis() * 1_000_000L;
        String current = url;
        for (int hop = 0; ; hop++) {
            Target target;
            try {
                target = guard.resolve(withoutFragment(current), blocked);
            } catch (OutboundTargetGuard.BlockedTargetException e) {
                return new Image(e.reason() == OutboundTargetGuard.Reason.ADDRESS ? Outcome.BLOCKED : Outcome.FAILED, null);
            }
            FetchResponse response;
            try {
                response = http.get(target, headers("image/webp,image/png,image/jpeg,image/gif,image/*;q=0.8"), new FetchLimits(limits.connectMillis(),
                        limits.readMillis(), deadline, IMAGE_MAX_BYTES, type -> type.startsWith("image/"), null));
            } catch (IOException | RuntimeException e) {
                return new Image(Outcome.FAILED, null);
            }
            int status = response.status();
            if (status >= 300 && status < 400 && response.location() != null) {
                String next = absolute(current, response.location());
                if (next == null || hop + 1 > MAX_REDIRECTS) {
                    return new Image(next == null ? Outcome.FAILED : Outcome.TOO_MANY_REDIRECTS, null);
                }
                current = next;
                continue;
            }
            if (status < 200 || status >= 300 || response.contentType() == null || !response.contentType().startsWith("image/") || response.truncated()
                    || response.body().length == 0 || !isIdentity(response.contentEncoding())) {
                return new Image(Outcome.FAILED, null);
            }
            return new Image(Outcome.OK, response.body());
        }
    }

    /** 조각(#…)은 서버로 가지 않는다 — 검사기가 조각 든 주소를 거절하므로 떼고 보낸다 */
    static String withoutFragment(String url) {
        int hash = url.indexOf('#');
        return hash < 0 ? url : url.substring(0, hash);
    }

    private static Map<String, String> headers(String accept) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Accept", accept);
        headers.put("Accept-Language", "ko-KR,ko;q=0.9,en;q=0.5");
        return headers;
    }

    private static boolean isIdentity(String encoding) {
        return encoding == null || encoding.isBlank() || "identity".equals(encoding);
    }

    private static void addHost(List<String> hosts, String url) {
        String host = DealHosts.hostOf(url);
        if (host != null && !hosts.contains(host)) {
            hosts.add(host);
        }
    }

    /** Location → 절대 주소(http·https 만). 아니면 null */
    static String absolute(String base, String location) {
        try {
            URI resolved = new URI(base).resolve(location.strip());
            String scheme = resolved.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) || resolved.getHost() == null) {
                return null;
            }
            return resolved.toString();
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }
}
