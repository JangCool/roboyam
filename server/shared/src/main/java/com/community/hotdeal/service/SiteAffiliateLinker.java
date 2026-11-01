package com.community.hotdeal.service;

import com.community.hotdeal.entity.DealSiteAffiliateEntity;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 사이트 제휴 변환(D5 — 순수 함수). {@code SITE} 모드의 구매 링크({@code /api/deals/{postId}/go})와 글 응답의 {@code affiliateKind}·link-check 의 {@code siteAffiliate} 가 같은 판정을 쓴다.
 *
 * <ul>
 *   <li><b>몰 판정</b>: 켠 몰만, 최종 호스트를 먼저 보고(없거나 안 걸리면 처음 호스트), 접미사 일치, 긴 도메인 우선.</li>
 *   <li><b>TEMPLATE</b>: 틀의 {@code {url}} 자리(전부)에 {@code encodeURIComponent(원래 주소)}.</li>
 *   <li><b>QUERY</b>: 쿼리({@code k=v&…})를 붙이되 같은 이름이 있으면 그 값을 바꾼다(조각은 끝에 둔다).</li>
 * </ul>
 * 변환 전에 개인 제휴 파라미터를 뗀다({@link DealUrlKey#stripTracking}) — 부르는 쪽이 한다. 저장된 상품 링크는 바꾸지 않는다.
 */
public final class SiteAffiliateLinker {

    private SiteAffiliateLinker() {
    }

    /** 사이트 제휴 몰 한 줄(사본) */
    public record SiteStore(long id, String domain, String programName, String transformType, String template, String query, boolean enabled) {
    }

    /** 이 링크가 가는 켠 사이트 제휴 몰 */
    public static Optional<SiteStore> match(Collection<SiteStore> stores, String productHost, String finalHost) {
        List<SiteStore> enabled = stores.stream().filter(SiteStore::enabled).sorted(Comparator.comparingInt((SiteStore s) -> s.domain().length()).reversed()).toList();
        if (finalHost != null) {
            Optional<SiteStore> byFinal = enabled.stream().filter(s -> DealHosts.matches(finalHost, s.domain())).findFirst();
            if (byFinal.isPresent()) {
                return byFinal;
            }
        }
        return enabled.stream().filter(s -> DealHosts.matches(productHost, s.domain())).findFirst();
    }

    /** 몰의 변환을 한다 */
    public static String transform(SiteStore store, String url) {
        if (DealSiteAffiliateEntity.TEMPLATE.equals(store.transformType())) {
            return store.template().replace(DealSiteAffiliateEntity.URL_PLACEHOLDER, encodeUriComponent(url));
        }
        return withQuery(url, store.query());
    }

    /** 자바스크립트 {@code encodeURIComponent} 와 같은 인코딩 — {@code A-Z a-z 0-9 - _ . ! ~ * ' ( )} 만 그대로 */
    public static String encodeUriComponent(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("%21", "!").replace("%27", "'").replace("%28", "(")
                .replace("%29", ")").replace("%7E", "~");
    }

    /** 쿼리를 붙인다 — 같은 이름은 값을 바꾼다 */
    static String withQuery(String url, String extra) {
        String raw = url.strip();
        int hash = raw.indexOf('#');
        String fragment = hash < 0 ? "" : raw.substring(hash);
        String head = hash < 0 ? raw : raw.substring(0, hash);
        int q = head.indexOf('?');
        String base = q < 0 ? head : head.substring(0, q);
        List<String[]> params = new ArrayList<>(DealUrlKey.parseQuery(q < 0 ? null : head.substring(q + 1)));
        for (String[] add : DealUrlKey.parseQuery(extra == null ? null : extra.strip().replaceFirst("^[?&]+", ""))) {
            boolean replaced = false;
            for (int i = 0; i < params.size(); i++) {
                if (params.get(i)[0].equals(add[0])) {
                    params.set(i, add);
                    replaced = true;
                }
            }
            if (!replaced) {
                params.add(add);
            }
        }
        List<String> parts = params.stream().map(p -> p[2]).toList();
        return base + (parts.isEmpty() ? "" : "?" + String.join("&", parts)) + fragment;
    }
}
