package com.community.address.service;

import com.community.zconfiguration.message.MessageRef;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 주소 검색의 규칙 값과 검사(docs/features/address-search-contract.md 「필드·검증」). 관리자 화면(admin-web {@code UtilAddressSettings})과 같은 한도다 — 한쪽을 바꾸면 다른 쪽도 바꾼다.
 */
public final class AddressSearchPolicy {

    /** 다음(카카오) 우편번호 공식 스크립트 — 관리자가 비워 두면 이 값. user-web {@code useAddressSearch} 의 기본값과 같다 */
    public static final String DEFAULT_SCRIPT_URL = "https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js";
    public static final int SCRIPT_URL_MAX = 500;
    public static final int STALE_DAYS_MIN = 7;
    public static final int STALE_DAYS_MAX = 365;

    /** 검색어 — 띄어쓰기를 뺀 글자 수 2자 이상, 80자까지, 낱말 6개까지(나머지는 버린다) */
    public static final int KEYWORD_MIN = 2;
    public static final int KEYWORD_MAX = 80;
    public static final int MAX_TERMS = 6;
    /** 한 쪽 최대 줄 수(화면은 10) */
    public static final int PAGE_SIZE_MAX = 30;
    public static final int PAGE_SIZE_DEFAULT = 10;
    /** 건수는 이만큼까지만 센다 — 「서울」처럼 넓은 검색어가 수백만 줄을 세지 않게. 넘으면 total 이 이 값이다 */
    public static final int TOTAL_CAP = 1000;

    private AddressSearchPolicy() {
    }

    /**
     * 검색어를 낱말로 나눈다 — 앞뒤 공백을 지우고, 주소를 붙여 넣을 때 섞이는 괄호·쉼표는 띄어쓰기로 보고, 같은 낱말은 한 번만. 모든 낱말이 결과에 들어 있어야 한다(AND).
     * 비었거나 너무 짧으면 빈 목록(부르는 쪽이 400).
     */
    public static List<String> terms(String keyword) {
        if (keyword == null) {
            return List.of();
        }
        String cleaned = keyword.replaceAll("[(),]", " ").strip();
        if (cleaned.replaceAll("\\s+", "").length() < KEYWORD_MIN) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String word : cleaned.split("\\s+")) {
            if (!word.isEmpty()) {
                unique.add(word);
            }
            if (unique.size() == MAX_TERMS) {
                break;
            }
        }
        return new ArrayList<>(unique);
    }

    /** 검색어 칸 문제 — 없으면 null */
    public static MessageRef keywordProblem(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return new MessageRef("address.search.keywordRequired");
        }
        if (keyword.strip().length() > KEYWORD_MAX) {
            return new MessageRef("address.search.keywordTooLong", KEYWORD_MAX);
        }
        if (terms(keyword).isEmpty()) {
            return new MessageRef("address.search.keywordTooShort", KEYWORD_MIN);
        }
        return null;
    }

    /** ILIKE 패턴 — 입력한 {@code \ % _} 는 글자 그대로(ESCAPE '\') */
    public static String containsPattern(String term) {
        return "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** 스크립트 주소 문제 — 비었으면(공식 주소를 쓴다) 문제 없음 */
    public static MessageRef scriptUrlProblem(String scriptUrl) {
        if (scriptUrl == null || scriptUrl.isBlank()) {
            return null;
        }
        String value = scriptUrl.strip();
        if (value.length() > SCRIPT_URL_MAX) {
            return new MessageRef("address.settings.scriptUrlTooLong", SCRIPT_URL_MAX);
        }
        if (!value.startsWith("https://") || value.length() == "https://".length() || value.chars().anyMatch(Character::isWhitespace)) {
            return new MessageRef("address.settings.scriptUrlNotHttps");
        }
        return null;
    }

    /** 갱신 권고 일수 문제 — 정수 7~365 */
    public static MessageRef staleDaysProblem(Integer days) {
        if (days == null) {
            return new MessageRef("address.settings.staleDaysRequired");
        }
        if (days < STALE_DAYS_MIN || days > STALE_DAYS_MAX) {
            return new MessageRef("address.settings.staleDaysOutOfRange", STALE_DAYS_MIN, STALE_DAYS_MAX);
        }
        return null;
    }

    /** 저장할 스크립트 주소 — 비었으면 null(공식 주소) */
    public static String storedScriptUrl(String scriptUrl) {
        return scriptUrl == null || scriptUrl.isBlank() ? null : scriptUrl.strip();
    }

    /** 회원 화면이 쓸 스크립트 주소 */
    public static String effectiveScriptUrl(String stored) {
        return stored == null || stored.isBlank() ? DEFAULT_SCRIPT_URL : stored;
    }
}
