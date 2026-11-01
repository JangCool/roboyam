package com.community.hotdeal.service;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * 상품 링크의 호스트 정규화 — 판정·저장·같은 딜 키가 같은 규칙을 쓴다: 소문자 · 끝 점 뗌 · 앞의 {@code www.} 뗌 · 국제화 도메인은 ASCII(punycode).
 * 스킴·포트·경로·쿼리·조각은 호스트에 들지 않는다.
 */
public final class DealHosts {

    /** 상품 링크 최대 길이(계약 「필드·검증」) */
    public static final int URL_MAX = 1000;

    private DealHosts() {
    }

    /** {@code http(s)} 이고 호스트가 있는 주소인가(공백·제어 문자·자격 증명이 없어야 한다). 길이는 따로 본다 */
    public static boolean isHttpUrl(String url) {
        return parse(url) != null;
    }

    /** 주소 → 판정 호스트. 주소가 아니면 null */
    public static String hostOf(String url) {
        URI uri = parse(url);
        return uri == null ? null : normalizeHost(uri.getHost());
    }

    /** 호스트 글자 → 판정 호스트(소문자·끝 점·{@code www.} 뗌·ASCII). 비었거나 바꿀 수 없으면 null */
    public static String normalizeHost(String host) {
        if (host == null) {
            return null;
        }
        String h = host.strip();
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        while (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);
        }
        if (h.isEmpty()) {
            return null;
        }
        try {
            h = IDN.toASCII(h, IDN.ALLOW_UNASSIGNED);
        } catch (IllegalArgumentException e) {
            return null;
        }
        h = h.toLowerCase(Locale.ROOT);
        if (h.startsWith("www.") && h.length() > 4) {
            h = h.substring(4);
        }
        return h.isEmpty() ? null : h;
    }

    /** 호스트가 규칙 도메인에 걸리는가 — 같거나 그 하위 도메인(접미사 일치, 점 경계) */
    public static boolean matches(String host, String domain) {
        if (host == null || domain == null) {
            return false;
        }
        return host.equals(domain) || host.endsWith("." + domain);
    }

    /** http(s) 주소로 읽는다 — 아니면 null */
    static URI parse(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String u = url.strip();
        for (int i = 0; i < u.length(); i++) {
            char c = u.charAt(i);
            if (c <= 0x20 || c == 0x7f || Character.isISOControl(c)) {
                return null;
            }
        }
        URI uri;
        try {
            uri = new URI(u);
        } catch (URISyntaxException e) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return null;
        }
        if (uri.getHost() == null || uri.getHost().isBlank() || uri.getRawUserInfo() != null) {
            return null;
        }
        return uri;
    }
}
