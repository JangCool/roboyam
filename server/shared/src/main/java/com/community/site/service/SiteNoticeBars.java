package com.community.site.service;

import com.community.zconfiguration.message.MessageRef;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 공지 띠(공개 사이트 모든 화면의 메뉴 줄 아래 한 줄 공지 — 관리자 설정 > 일반 「공지 띠」) 규칙. 관리자 저장(admin)과 공개 읽기(api)가 같은 규칙을 쓴다.
 * 화면은 {@code util-site-notice-bar.ts} 가 미리 검사하고 판정은 서버가 한다. DB 의 {@code ck_site_settings_notice_bar_*}(V156)가 칸 하나씩 한 번 더 지킨다.
 *
 * <ul>
 *   <li>출처: {@code BOARD}(slug {@value #NOTICE_BOARD_SLUG} 게시판의 최근 글) · {@code CUSTOM}(운영자가 쓴 문구 한 줄). 글 수는 5 또는 10.</li>
 *   <li>문구: 앞뒤 공백을 뗀 뒤 비면 없음(null). {@code CUSTOM} 이면 필수. 있으면(출처와 상관없이) 1~{@value #TEXT_MAX}자(코드포인트), {@code <}·{@code >}·제어 문자 없음.</li>
 *   <li>링크(선택): 앞뒤 공백을 뗀 뒤 비면 없음. 있으면 {@value #LINK_MAX}자 이하, 공백·제어 문자 없음, {@code /} 로 시작(단 {@code //}·{@code /\} 아님 — 브라우저가 다른 사이트 주소로 읽는다)
 *       또는 {@code http://}·{@code https://} 로 시작(대소문자 무시).</li>
 *   <li>묶음 표식({@code version}): 글이면 {@code b:} + 보이는 글 ID 를 쉼표로 이은 것, 문구면 {@code c:} + SHA-256(문구 + "\n" + (링크 ?? "")) 앞 16자리 hex, 꺼짐 {@value #VERSION_OFF}.
 *       공개 사이트는 이 값별로 「닫음」을 기억한다.</li>
 * </ul>
 */
public final class SiteNoticeBars {

    public static final String SOURCE_BOARD = "BOARD";
    public static final String SOURCE_CUSTOM = "CUSTOM";
    public static final List<String> SOURCES = List.of(SOURCE_BOARD, SOURCE_CUSTOM);
    public static final List<Integer> BOARD_COUNTS = List.of(5, 10);
    public static final int DEFAULT_BOARD_COUNT = 5;
    public static final int TEXT_MAX = 100;
    public static final int LINK_MAX = 500;
    /** 공지 띠가 읽는 게시판의 주소(slug) — 고정 */
    public static final String NOTICE_BOARD_SLUG = "notice";
    public static final String VERSION_OFF = "off";

    private SiteNoticeBars() {
    }

    /** 앞뒤 공백을 뗀 값(비면 null) */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    public static boolean isSource(String source) {
        return source != null && SOURCES.contains(source);
    }

    public static boolean isBoardCount(Integer count) {
        return count != null && BOARD_COUNTS.contains(count);
    }

    /** 문구 위반(키) — {@code text} 는 {@link #normalize} 를 거친 값 */
    public static Optional<MessageRef> textViolation(String text, String source) {
        if (text == null) {
            return SOURCE_CUSTOM.equals(source) ? Optional.of(new MessageRef("siteNoticeBar.text.required")) : Optional.empty();
        }
        if (text.codePointCount(0, text.length()) > TEXT_MAX) {
            return Optional.of(new MessageRef("siteNoticeBar.text.length", String.valueOf(TEXT_MAX)));
        }
        if (text.indexOf('<') >= 0 || text.indexOf('>') >= 0 || text.chars().anyMatch(Character::isISOControl)) {
            return Optional.of(new MessageRef("siteNoticeBar.text.chars"));
        }
        return Optional.empty();
    }

    /** 링크 위반(키) — {@code link} 는 {@link #normalize} 를 거친 값, 없음은 위반이 아니다 */
    public static Optional<MessageRef> linkViolation(String link) {
        if (link == null) {
            return Optional.empty();
        }
        if (link.codePointCount(0, link.length()) > LINK_MAX) {
            return Optional.of(new MessageRef("siteNoticeBar.link.length", String.valueOf(LINK_MAX)));
        }
        return linkShapeOk(link) ? Optional.empty() : Optional.of(new MessageRef("siteNoticeBar.link.shape"));
    }

    private static boolean linkShapeOk(String link) {
        if (link.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c) || Character.isISOControl(c))) {
            return false;
        }
        if (link.startsWith("/")) {
            return link.length() == 1 || (link.charAt(1) != '/' && link.charAt(1) != '\\');
        }
        String lower = link.toLowerCase(Locale.ROOT);
        return (lower.startsWith("http://") && link.length() > "http://".length()) || (lower.startsWith("https://") && link.length() > "https://".length());
    }

    /** 글 묶음 표식 — {@code b:} + 글 ID 를 쉼표로 이은 것(글이 없으면 {@code b:}) */
    public static String boardVersion(List<Long> postIds) {
        return "b:" + postIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** 문구 표식 — {@code c:} + SHA-256(문구 + "\n" + (링크 ?? "")) 앞 16자리 hex(문구가 없으면 {@code c:}) */
    public static String customVersion(String text, String link) {
        if (text == null) {
            return "c:";
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest((text + "\n" + (link == null ? "" : link)).getBytes(StandardCharsets.UTF_8));
            return "c:" + HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
