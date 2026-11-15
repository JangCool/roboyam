package com.community.post.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 글 태그 글자 규칙 — 한 곳(docs/features/post-tags-contract.md 「필드·검증」). 글쓰기·고치기({@code tags}), 게시판 목록·매거진 거르기({@code ?tag=}),
 * 글 목록 가젯 태그 탭({@code tagTabs} — 관리자 검사·공개 읽기)이 모두 이것으로 정리·비교한다.
 *
 * <ul>
 *   <li>정리({@link #clean}): 앞뒤 공백 제거 → 앞의 {@code #} 떼기(여러 개면 모두) → 다시 앞뒤 공백 제거 → 안쪽 공백(연달아 있으면) 하나로.</li>
 *   <li>모양({@link #valid}): 1~{@value #NAME_MAX}자(코드 포인트) · 줄바꿈·제어 문자·{@code #}·{@code ,} 없음.</li>
 *   <li>비교 열쇠({@link #key}): 정리한 글자의 영문 소문자(대소문자 무시 — 저장은 처음 쓴 모양). DB {@code post_tags.tag_key}.</li>
 *   <li>목록({@link #normalize}): 빈 칸(정리 뒤 빈 글자)은 버리고, 열쇠가 같으면 처음 것 하나만(적은 차례), 최대 {@value #MAX_PER_POST}개.</li>
 * </ul>
 */
public final class PostTags {

    /** 글당 최대 태그 수 */
    public static final int MAX_PER_POST = 5;
    /** 태그 하나의 최대 글자 수(코드 포인트 — DB varchar(20)) */
    public static final int NAME_MAX = 20;

    private static final Pattern SPACES = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private PostTags() {
    }

    /**
     * 글쓰기·고치기 {@code tags} 정리 결과.
     *
     * @param tags  정리한 태그(적은 차례, 겹침 없음) — 틀리면 빈 목록
     * @param error 틀렸을 때 문구 키({@code post.tags.invalid}·{@code post.tags.tooMany}), 맞으면 null
     */
    public record Result(List<String> tags, String error) {

        public boolean ok() {
            return error == null;
        }
    }

    /** 하나 정리 — null 이면 빈 글자. 모양 검사는 하지 않는다({@link #valid}) */
    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.strip();
        int i = 0;
        while (i < s.length() && s.charAt(i) == '#') {
            i++;
        }
        s = s.substring(i).strip();
        // 줄바꿈·탭은 공백으로 합치지 않는다(모양 검사에서 거절) — 그 밖의 공백만 하나로
        StringBuilder out = new StringBuilder(s.length());
        boolean space = false;
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            boolean blank = Character.isWhitespace(c) && !Character.isISOControl(c) || Character.isSpaceChar(c);
            if (blank) {
                if (!space) {
                    out.append(' ');
                }
                space = true;
            } else {
                out.append(c);
                space = false;
            }
        }
        return out.toString();
    }

    /** 정리한 태그의 모양이 맞는가 — 1~{@value #NAME_MAX}자, 줄바꿈·제어 문자·{@code #}·{@code ,} 없음 */
    public static boolean valid(String cleaned) {
        if (cleaned == null || cleaned.isEmpty()) {
            return false;
        }
        int length = cleaned.codePointCount(0, cleaned.length());
        if (length > NAME_MAX) {
            return false;
        }
        return cleaned.codePoints().noneMatch(cp -> Character.isISOControl(cp) || cp == '#' || cp == ',' || cp == ' ' || cp == ' ');
    }

    /** 비교 열쇠 — 정리한 글자의 영문 소문자(공백은 이미 하나로) */
    public static String key(String cleaned) {
        return SPACES.matcher(cleaned).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    /**
     * 거르기 값({@code ?tag=}) → 비교 열쇠. 비었으면 null(거르지 않음), 모양이 틀리면 {@code ""}(맞는 글이 없다 — 빈 목록).
     */
    public static String filterKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = clean(raw);
        if (cleaned.isEmpty()) {
            return null;
        }
        return valid(cleaned) ? key(cleaned) : "";
    }

    /** 글쓰기·고치기 {@code tags} — 최대 {@value #MAX_PER_POST}개 */
    public static Result normalize(List<String> raw) {
        return normalize(raw, MAX_PER_POST);
    }

    /**
     * 태그 목록 정리 — 빈 칸은 버리고, 열쇠가 같으면 처음 것 하나(적은 차례). 모양이 틀린 것이 있으면 {@code post.tags.invalid},
     * 겹침을 덜어도 {@code max} 개를 넘으면 {@code post.tags.tooMany}(둘 다면 모양 먼저). null 이면 빈 목록.
     */
    public static Result normalize(List<String> raw, int max) {
        if (raw == null || raw.isEmpty()) {
            return new Result(List.of(), null);
        }
        Map<String, String> byKey = new LinkedHashMap<>();
        for (String one : raw) {
            String cleaned = clean(one);
            if (cleaned.isEmpty()) {
                continue;
            }
            if (!valid(cleaned)) {
                return new Result(List.of(), "post.tags.invalid");
            }
            byKey.putIfAbsent(key(cleaned), cleaned);
        }
        if (byKey.size() > max) {
            return new Result(List.of(), "post.tags.tooMany");
        }
        return new Result(List.copyOf(byKey.values()), null);
    }

    /** 이미 정리한 태그 목록 → 열쇠 목록(같은 차례) */
    public static List<String> keys(List<String> tags) {
        List<String> out = new ArrayList<>(tags.size());
        tags.forEach(t -> out.add(key(t)));
        return out;
    }
}
