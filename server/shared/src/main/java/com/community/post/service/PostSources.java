package com.community.post.service;

import com.community.post.dto.PostSource;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 글 출처 목록 규칙 — 한 곳(V232 {@code posts.sources} · 게시판 정책 「출처 칸 쓰기」 {@code sourcesEnabled}). 공개 글쓰기·고치기와 AI 기자 뉴스 글이 함께 쓴다.
 *
 * <ul>
 *   <li>정리({@link #normalize}): 줄마다 이름·주소의 앞뒤 공백을 떼고, 둘 다 빈 줄은 버린다. 같은 주소(정리한 글자 그대로)는 처음 것 하나만(적은 차례).</li>
 *   <li>검사: 주소 필수 · http·https 만(호스트 있음) · {@value #URL_MAX}자 이하, 이름은 비어도 되고(화면이 도메인을 보인다) {@value #NAME_MAX}자 이하,
 *       정리 뒤 최대 {@value #MAX_PER_POST}개. 틀리면 문구 키({@code post.sources.*}) — 부르는 쪽이 {@code fields.sources} 400 으로 낸다.</li>
 *   <li>저장({@link #toJson}): JSON 배열 문자열, 비면 null(칸 없음). 읽기({@link #fromJson})는 틀린 값이면 빈 목록.</li>
 * </ul>
 */
public final class PostSources {

    /** 글당 최대 출처 수 */
    public static final int MAX_PER_POST = 5;
    /** 출처 이름 최대 글자 수(코드 포인트) */
    public static final int NAME_MAX = 40;
    /** 원문 주소 최대 글자 수 */
    public static final int URL_MAX = 500;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<PostSource>> LIST = new TypeReference<>() {
    };

    private PostSources() {
    }

    /**
     * 글쓰기·고치기 {@code sources} 정리 결과.
     *
     * @param sources 정리한 출처(적은 차례, 같은 주소 없음) — 틀리면 빈 목록
     * @param error   틀렸을 때 문구 키({@code post.sources.urlInvalid}·{@code post.sources.nameTooLong}·{@code post.sources.tooMany}), 맞으면 null
     */
    public record Result(List<PostSource> sources, String error) {

        public boolean ok() {
            return error == null;
        }

        /** 문구 키의 자리 값(최대 개수 · 이름 최대 글자 수 · 주소 최대 글자 수) */
        public String limit() {
            return switch (error == null ? "" : error) {
                case "post.sources.tooMany" -> String.valueOf(MAX_PER_POST);
                case "post.sources.nameTooLong" -> String.valueOf(NAME_MAX);
                default -> String.valueOf(URL_MAX);
            };
        }
    }

    /** 목록 정리·검사 — null 이면 빈 목록(맞음) */
    public static Result normalize(List<PostSource> raw) {
        if (raw == null || raw.isEmpty()) {
            return new Result(List.of(), null);
        }
        List<PostSource> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PostSource s : raw) {
            if (s == null) {
                continue;
            }
            String name = strip(s.name());
            String url = strip(s.url());
            if (name == null && url == null) {
                continue; // 빈 줄
            }
            if (url == null || !validUrl(url)) {
                return new Result(List.of(), "post.sources.urlInvalid");
            }
            if (name != null && name.codePointCount(0, name.length()) > NAME_MAX) {
                return new Result(List.of(), "post.sources.nameTooLong");
            }
            if (seen.add(url)) {
                out.add(new PostSource(name, url));
            }
        }
        if (out.size() > MAX_PER_POST) {
            return new Result(List.of(), "post.sources.tooMany");
        }
        return new Result(List.copyOf(out), null);
    }

    /** http·https 주소인가(호스트 있음 · 공백 없음 · {@value #URL_MAX}자 이하) */
    public static boolean validUrl(String url) {
        if (url == null || url.isEmpty() || url.length() > URL_MAX || url.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            return false;
        }
        try {
            URI u = new URI(url);
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            return ("http".equals(scheme) || "https".equals(scheme)) && u.getHost() != null && !u.getHost().isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    /** 출처 이름으로 쓸 글자 — 공백을 하나로 모으고 앞 {@value #NAME_MAX}자까지(AI 기자가 언론사 이름·원래 글 제목으로 만들 때). 비면 null */
    public static String nameOf(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.replaceAll("\s+", " ").strip();
        if (s.isEmpty()) {
            return null;
        }
        if (s.codePointCount(0, s.length()) > NAME_MAX) {
            s = s.substring(0, s.offsetByCodePoints(0, NAME_MAX)).strip();
        }
        return s;
    }

    /** 이름 한 줄로(금지어 검사에 넣는 글자) — 이름이 하나도 없으면 null */
    public static String namesHaystack(List<PostSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        List<String> names = sources.stream().map(PostSource::name).filter(n -> n != null && !n.isEmpty()).toList();
        return names.isEmpty() ? null : String.join("\n", names);
    }

    /** 저장 값 — JSON 배열 문자열, 비면 null */
    public static String toJson(List<PostSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        return JSON.writeValueAsString(sources);
    }

    /** 저장 값 읽기 — 없거나 틀린 값이면 빈 목록 */
    public static List<PostSource> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<PostSource> list = JSON.readValue(json, LIST);
            return list == null ? List.of() : list.stream().filter(s -> s != null && s.url() != null).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String strip(String s) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }
}
