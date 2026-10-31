package com.community.bizapplication.service;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 핫딜 상품 링크 검사(H4 · 설계 10절) — 단축 URL 도메인 차단, 쇼핑몰 이름(목업 표 그대로 상수), 제휴 링크 표시. 신청 때도 서버가 다시 막는다(400 {@code fields.deal.linkUrl}).
 * 차단·쇼핑몰 표의 관리 화면은 후속(지금은 코드 상수 — 가정).
 */
public final class BizDealLinks {

    /** 검사 결과 — {@code blockedReason} 은 문구 키 */
    public record Check(String host, String shopName, boolean blocked, String blockedReason, boolean affiliate) {
    }

    private static final Map<String, String> SHOPS = Map.of(
            "coupang.com", "쿠팡",
            "link.coupang.com", "쿠팡",
            "11st.co.kr", "11번가",
            "gmarket.co.kr", "G마켓",
            "auction.co.kr", "옥션",
            "smartstore.naver.com", "네이버 스마트스토어",
            "brand.naver.com", "네이버 브랜드스토어",
            "ssg.com", "SSG닷컴");

    private static final Set<String> SHORTENERS = Set.of("bit.ly", "han.gl", "t.ly", "me2.do", "url.kr", "tinyurl.com");

    private static final Set<String> AFFILIATE_HOSTS = Set.of("link.coupang.com");

    private BizDealLinks() {
    }

    /** http(s) 주소의 호스트(소문자, 앞의 www. 뺌) — 형식이 틀리면 비어 있다 */
    public static Optional<String> host(String url) {
        if (url == null || url.isBlank() || url.length() > 500) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(url.strip());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null || uri.getHost().isBlank()) {
                return Optional.empty();
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return Optional.of(host.startsWith("www.") ? host.substring(4) : host);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** 검사 — 형식이 틀리면 비어 있다(400 은 부르는 쪽) */
    public static Optional<Check> check(String url) {
        return host(url).map(host -> {
            if (SHORTENERS.contains(host)) {
                return new Check(host, null, true, "bizapp.link.shortener", false);
            }
            String shop = SHOPS.entrySet().stream().filter(e -> host.equals(e.getKey()) || host.endsWith("." + e.getKey()))
                    .sorted((a, b) -> b.getKey().length() - a.getKey().length()).map(Map.Entry::getValue).findFirst().orElse(null);
            return new Check(host, shop, false, null, AFFILIATE_HOSTS.contains(host));
        });
    }
}
