package com.community.analytics.collect;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * 유입처 가르기(site-analytics-contract 「수집」) — 세션 첫 페이지의 외부 주소에서 <b>도메인(호스트)만</b> 남기고 분류한다. 경로·쿼리(검색어 등)는 버린다.
 *
 * <ul>
 *   <li>{@code utm_*} 가 하나라도 있으면 {@code CAMPAIGN}(도메인 없음).</li>
 *   <li>주소가 없으면 {@code DIRECT}(주소 입력·즐겨찾기·앱 안 링크 등 — 도메인 없음). 우리 사이트 호스트와 같아도 {@code DIRECT}(브라우저는 외부만 보내지만 꾸민 신호 대비).</li>
 *   <li>그 밖은 호스트 뒤끝으로 — SNS 하위 주소(블로그·카페 등)를 먼저, 그다음 검색(네이버·구글·다음·빙 …), SNS, 나머지는 {@code OTHER_SITE}.</li>
 * </ul>
 * 목록을 넓히면 이 한 곳만 고친다(지난 집계는 그때 분류대로 남는다).
 */
final class ReferrerClassifier {

    static final String SEARCH = "SEARCH";
    static final String SOCIAL = "SOCIAL";
    static final String DIRECT = "DIRECT";
    static final String OTHER_SITE = "OTHER_SITE";
    static final String CAMPAIGN = "CAMPAIGN";

    /** 포털 안의 커뮤니티·블로그 — 검색보다 먼저 본다 */
    private static final List<String> SOCIAL_FIRST = List.of("blog.naver.com", "cafe.naver.com", "band.naver.com", "post.naver.com", "cafe.daum.net",
            "blog.daum.net", "story.kakao.com", "pf.kakao.com", "open.kakao.com");
    private static final List<String> SEARCH_HOSTS = List.of("naver.com", "google.com", "google.co.kr", "daum.net", "bing.com", "yahoo.com", "yahoo.co.jp",
            "duckduckgo.com", "zum.com", "nate.com", "baidu.com", "yandex.ru", "yandex.com", "ecosia.org", "search.brave.com", "naver.me");
    private static final List<String> SOCIAL_HOSTS = List.of("facebook.com", "fb.com", "instagram.com", "twitter.com", "x.com", "t.co", "youtube.com", "youtu.be",
            "tiktok.com", "linkedin.com", "lnkd.in", "reddit.com", "pinterest.com", "threads.net", "kakao.com", "band.us", "discord.com", "telegram.org", "t.me",
            "tumblr.com", "line.me");

    /** 분류 결과 — 도메인이 없는 분류는 {@code domain} 이 null */
    record Referrer(String group, String domain) {
    }

    private ReferrerClassifier() {
    }

    /**
     * @param referrer 브라우저가 보낸 외부 주소(origin + 경로, 없으면 null)
     * @param hasUtm   utm_* 가 하나라도 있나
     * @param ownHost  이 요청을 받은 호스트(소문자, 없으면 null)
     */
    static Referrer classify(String referrer, boolean hasUtm, String ownHost) {
        if (hasUtm) {
            return new Referrer(CAMPAIGN, null);
        }
        String host = host(referrer);
        if (host == null || host.equals(ownHost)) {
            return new Referrer(DIRECT, null);
        }
        if (matches(host, SOCIAL_FIRST)) {
            return new Referrer(SOCIAL, host);
        }
        if (matches(host, SEARCH_HOSTS) || host.startsWith("google.") || host.contains(".google.")) {
            return new Referrer(SEARCH, host);
        }
        if (matches(host, SOCIAL_HOSTS)) {
            return new Referrer(SOCIAL, host);
        }
        return new Referrer(OTHER_SITE, host);
    }

    /** http(s) 주소의 호스트(소문자, 포트 없음) — 읽을 수 없으면 null */
    static String host(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.strip());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) || host == null || host.isEmpty() || host.length() > 253) {
                return null;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean matches(String host, List<String> suffixes) {
        for (String s : suffixes) {
            if (host.equals(s) || host.endsWith("." + s)) {
                return true;
            }
        }
        return false;
    }
}
