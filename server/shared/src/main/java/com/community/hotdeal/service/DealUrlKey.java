package com.community.hotdeal.service;

import com.community.zconfiguration.crypto.Digests;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 같은 딜 키(서버 규칙 3) — 스킴·호스트 소문자 · {@code www.} · 기본 포트 · 조각 · 끝 {@code /} 제거 + 추적 파라미터 제거({@link #isTracking}) + 규칙 {@code keyParams} 가 있으면
 * 그 쿼리만 → 이름·값 차례로 정렬 → SHA-256 hex. 같은 딜 = 같은 키의 공개·진행 중(ACTIVE·FLAGGED) 글(그 판정은 질의 쪽).
 *
 * <p>추적 파라미터 목록은 사이트 제휴(D5)의 「개인 제휴 파라미터 떼기」({@link #stripTracking})와 같다.
 */
public final class DealUrlKey {

    /** 이름이 같으면 추적 파라미터 */
    private static final Set<String> TRACKING_EXACT = Set.of("fbclid", "gclid", "igshid", "ref", "tag", "spm", "_trkparms", "sub_id");
    /** 이 머리로 시작하면 추적 파라미터 */
    private static final List<String> TRACKING_PREFIX = List.of("utm_", "aff");

    private DealUrlKey() {
    }

    /** 추적 파라미터 이름인가(대소문자 무시) — {@code utm_*} · {@code fbclid} · {@code gclid} · {@code igshid} · {@code ref} · {@code tag} · {@code aff*} · {@code spm} · {@code _trkparms} · {@code sub_id} */
    public static boolean isTracking(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return TRACKING_EXACT.contains(n) || TRACKING_PREFIX.stream().anyMatch(n::startsWith);
    }

    /**
     * 같은 딜 키.
     *
     * @param keyParams 이 링크 호스트 규칙의 {@code keyParams}(비면 추적 파라미터만 뗀 모든 쿼리)
     * @return 64자 hex. 주소가 아니면 null
     */
    public static String of(String url, Collection<String> keyParams) {
        String canonical = canonical(url, keyParams);
        return canonical == null ? null : Digests.sha256Hex(canonical);
    }

    /** 해시하기 전의 정규 주소(시험·관리자 확인용) */
    public static String canonical(String url, Collection<String> keyParams) {
        URI uri = DealHosts.parse(url);
        if (uri == null) {
            return null;
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = DealHosts.normalizeHost(uri.getHost());
        if (host == null) {
            return null;
        }
        int port = uri.getPort();
        boolean defaultPort = port == -1 || (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        Set<String> keep = keyParams == null ? Set.of() : keyParams.stream().map(k -> k.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<String[]> params = new ArrayList<>();
        for (String[] pair : parseQuery(uri.getRawQuery())) {
            String lower = pair[0].toLowerCase(Locale.ROOT);
            if (pair[0].isEmpty() || isTracking(pair[0])) {
                continue;
            }
            if (!keep.isEmpty() && !keep.contains(lower)) {
                continue;
            }
            params.add(pair);
        }
        params.sort(Comparator.<String[], String>comparing(p -> p[0]).thenComparing(p -> p[1]));
        StringBuilder out = new StringBuilder(scheme).append("://").append(host);
        if (!defaultPort) {
            out.append(':').append(port);
        }
        out.append(path);
        for (int i = 0; i < params.size(); i++) {
            out.append(i == 0 ? '?' : '&').append(params.get(i)[0]).append('=').append(params.get(i)[1]);
        }
        return out.toString();
    }

    /**
     * 추적 파라미터를 뗀 주소(사이트 제휴 변환 전 — D5). 나머지 쿼리 차례·조각은 그대로 둔다. 주소가 아니면 그대로 돌려준다.
     */
    public static String stripTracking(String url) {
        URI uri = DealHosts.parse(url);
        if (uri == null || uri.getRawQuery() == null) {
            return url;
        }
        String raw = url.strip();
        int q = raw.indexOf('?');
        int hash = raw.indexOf('#', q < 0 ? 0 : q);
        String base = q < 0 ? raw : raw.substring(0, q);
        String fragment = hash < 0 ? "" : raw.substring(hash);
        List<String> kept = new ArrayList<>();
        for (String[] pair : parseQuery(uri.getRawQuery())) {
            if (!isTracking(pair[0])) {
                kept.add(pair[1].isEmpty() && !pair[2].contains("=") ? pair[0] : pair[0] + "=" + pair[1]);
            }
        }
        return base + (kept.isEmpty() ? "" : "?" + String.join("&", kept)) + fragment;
    }

    /** 날 쿼리 → [이름, 값, 원래 조각] 목록(디코딩하지 않는다) */
    static List<String[]> parseQuery(String rawQuery) {
        List<String[]> out = new ArrayList<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return out;
        }
        for (String part : rawQuery.split("&")) {
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            out.add(eq < 0 ? new String[] {part, "", part} : new String[] {part.substring(0, eq), part.substring(eq + 1), part});
        }
        return out;
    }
}
