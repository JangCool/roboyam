package com.community.search.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 통합 검색어 규칙 — user-web {@code utils/util-search-rules.ts}({@code UtilSearchRules})와 <b>똑같이</b> 판정한다(화면이 미리 검사하고 서버가 다시 판정한다). 한쪽을 고치면 둘 다 고친다.
 *
 * <ul>
 *   <li>다듬기: 널 문자를 빼고, 이어진 공백(자바스크립트 {@code \s} 와 같은 글자들)을 한 칸으로, 앞뒤 공백을 뗀다.</li>
 *   <li>검사(첫 번째 까닭): 전체 2자 미만 {@code TOO_SHORT} → 50자 초과 {@code TOO_LONG} → 2자 미만 단어가 있음 {@code SHORT_WORD}. 글자 수는 유니코드 한 글자씩(코드 포인트).</li>
 *   <li>단어: 공백으로 나눈 것 중 중복(대소문자 구분 — 화면과 같다)을 버리고 앞에서 5개까지.</li>
 * </ul>
 */
public final class SearchQueryPolicy {

    public static final int MIN_TOKEN = 2;
    public static final int MAX_QUERY = 50;
    public static final int MAX_TOKENS = 5;

    /** 검색어가 걸리는 까닭 */
    public enum Problem {
        TOO_SHORT,
        TOO_LONG,
        SHORT_WORD
    }

    /** 자바스크립트 정규식 {@code \s} 와 같은 글자들 */
    private static final Pattern SPACES = Pattern.compile("[\t\n\u000B\f\r \u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF]+");

    private SearchQueryPolicy() {
    }

    public static int length(String text) {
        return text.codePointCount(0, text.length());
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String collapsed = SPACES.matcher(raw.replace("\u0000", "")).replaceAll(" ");
        int start = 0;
        int end = collapsed.length();
        while (start < end && collapsed.charAt(start) == ' ') {
            start++;
        }
        while (end > start && collapsed.charAt(end - 1) == ' ') {
            end--;
        }
        return collapsed.substring(start, end);
    }

    /** 단어들(중복·{@value #MAX_TOKENS}개 초과는 버린다) */
    public static List<String> distinctWords(String raw) {
        String n = normalize(raw);
        List<String> out = new ArrayList<>();
        if (n.isEmpty()) {
            return out;
        }
        for (String w : n.split(" ")) {
            if (!out.contains(w) && out.size() < MAX_TOKENS) {
                out.add(w);
            }
        }
        return out;
    }

    /** 걸리는 까닭 — 보낼 수 있으면 null */
    public static Problem check(String raw) {
        String n = normalize(raw);
        int total = length(n);
        if (total < MIN_TOKEN) {
            return Problem.TOO_SHORT;
        }
        if (total > MAX_QUERY) {
            return Problem.TOO_LONG;
        }
        for (String w : n.split(" ")) {
            if (length(w) < MIN_TOKEN) {
                return Problem.SHORT_WORD;
            }
        }
        return null;
    }
}
