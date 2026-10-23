package com.community.board.service;

import com.community.board.dto.BoardSecurityValues;
import com.community.board.dto.ContentBody;
import com.community.board.dto.request.GuestCredentials;
import com.community.post.entity.ContentFormat;
import com.community.zconfiguration.message.MessageRef;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 글·댓글 입력 규칙 — 길이·링크/이미지 수·비회원 이름/비밀번호/자동입력 방지·금지어. 위반은 칸 경로 → 문구 키로 모아 서비스가 {@code ValidationException} 으로 던진다.
 * 수치·판정은 user-web {@code UtilPost}·목업 {@code post-mock.ts}({@code mockCheckLinks}·{@code mockCheckGuest}·{@code mockCheckBlockedWords})와 <b>같아야 한다</b>
 * (화면이 같은 기준으로 미리 검사한다). 한쪽을 고치면 함께 고친다.
 */
public final class ContentRules {

    /** 제목 1~100자 · 본문 10~200,000자(앞뒤 공백 제외, 고정 규칙) */
    public static final int TITLE_MIN = 1;
    public static final int TITLE_MAX = 100;
    public static final int CONTENT_MIN = 10;
    public static final int CONTENT_MAX = 200_000;
    /** 댓글 1~2,000자(관리자 설정에 없는 값 — 가정) */
    public static final int COMMENT_MAX = 2000;
    /** HTML 본문(정화한 뒤, 태그 포함)의 상한 — 보이는 글자 상한과 별도로 서식이 지나치게 긴 본문을 막는다(가정) */
    public static final int CONTENT_HTML_MAX = 400_000;
    public static final int COMMENT_HTML_MAX = 20_000;
    /** 글 하나에 넣을 수 있는 이미지·YouTube 수(새 회원은 보안 설정의 새 회원 이미지 수가 먼저 걸린다 — 가정) */
    public static final int POST_IMAGE_MAX = 30;
    public static final int POST_YOUTUBE_MAX = 10;
    /** 비회원 비밀번호 최대 길이(관리자 설정에 없는 값 — 가정) */
    public static final int GUEST_PASSWORD_MAX = 20;
    /** bcrypt 는 72바이트를 넘는 부분을 무시하므로 해시하기 전에 거절한다 */
    public static final int PASSWORD_MAX_BYTES = 72;
    /** 신고 기타 설명 최대 길이(가정) */
    public static final int REPORT_DETAIL_MAX = 300;

    /** user-web {@code LINK_COUNT_PATTERN} 과 같다 */
    private static final Pattern LINK = Pattern.compile("(https?://[^\\s<>\"']+|\\bwww\\.[^\\s<>\"']+)", Pattern.CASE_INSENSITIVE);
    /** user-web {@code IMAGE_PATTERN} 과 같다 — 마크다운 이미지 또는 이미지 확장자 주소 */
    private static final Pattern IMAGE = Pattern.compile("(!\\[[^\\]]*\\]\\([^)]*\\)|https?://[^\\s<>\"']+\\.(?:png|jpe?g|gif|webp)\\b)", Pattern.CASE_INSENSITIVE);

    private ContentRules() {
    }

    /** 링크·이미지 규칙을 누구에게 거는가 */
    public enum Writer {
        /** 비회원 — 보안 설정의 비회원 링크 수·이미지 허용 */
        GUEST,
        /** 첫 등급 회원(매니저·스텝 제외) — 새 회원 링크·이미지 수 */
        NEW_MEMBER,
        /** 그 밖의 회원 */
        MEMBER
    }

    public static int linkCount(String text) {
        return count(LINK, text);
    }

    public static int imageCount(String text) {
        return count(IMAGE, text);
    }

    private static int count(Pattern pattern, String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        Matcher m = pattern.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** 보이는 글자 수(코드 포인트) */
    public static int length(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    /** 요청의 본문 형식 — 빠지면 TEXT(예전 클라이언트), 모르는 값이면 null */
    public static ContentFormat format(String raw) {
        if (raw == null || raw.isBlank()) {
            return ContentFormat.TEXT;
        }
        try {
            return ContentFormat.valueOf(raw.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 앞뒤 공백을 자르고 줄바꿈을 {@code \n} 으로 맞춘다(null 은 빈 글자) */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n").replace('\r', '\n').replace("\u0000", "").strip();
    }

    /**
     * 링크·이미지 수 — 넘으면 {@code path} 칸(본문) 오류. 목업 {@code mockCheckLinks} 와 같은 순서다.
     *
     * @param allowImages 이 글·댓글에 이미지를 넣을 수 있는가(댓글은 늘 false, 글은 게시판의 이미지 허용)
     */
    public static void checkLinksImages(String content, Writer writer, boolean allowImages, BoardSecurityValues s, String path, Map<String, MessageRef> out) {
        checkLinksImages(linkCount(content), imageCount(content), writer, allowImages, s, path, out);
    }

    /** 링크·이미지 수를 이미 센 경우(HTML 본문 — {@link ContentBody}) */
    public static void checkLinksImages(int links, int images, Writer writer, boolean allowImages, BoardSecurityValues s, String path,
                                        Map<String, MessageRef> out) {
        if (writer == Writer.GUEST) {
            if (links > s.guestLinkLimit()) {
                out.put(path, s.guestLinkLimit() == 0 ? new MessageRef("board.content.link.guestNone") : new MessageRef("board.content.link.guest", s.guestLinkLimit()));
            } else if (images > 0 && (!s.guestImageUploadAllowed() || !allowImages)) {
                out.put(path, new MessageRef("board.content.image.guest"));
            }
            return;
        }
        if (writer == Writer.NEW_MEMBER) {
            if (links > s.newMemberLinkLimit()) {
                out.put(path, new MessageRef("board.content.link.newMember", s.newMemberLinkLimit()));
            } else if (images > s.newMemberImageLimit()) {
                out.put(path, new MessageRef("board.content.image.newMember", s.newMemberImageLimit()));
            }
        }
        if (!out.containsKey(path) && images > 0 && !allowImages) {
            out.put(path, new MessageRef("board.content.image.board"));
        }
    }

    /** 댓글에는 이미지를 넣지 않는다 — {@link #checkLinksImages} 를 이미지 불가로 부른 뒤 문구만 댓글용으로 바꾼다 */
    public static void checkCommentLinksImages(ContentBody body, Writer writer, BoardSecurityValues s, Map<String, MessageRef> out) {
        checkLinksImages(body.links(), body.images(), writer, false, s, "content", out);
        MessageRef ref = out.get("content");
        if (ref != null && ref.key().equals("board.content.image.board")) {
            out.put("content", new MessageRef("board.content.image.comment"));
        }
    }

    /**
     * 비회원 이름·비밀번호·자동입력 방지 토큰(있는지만 — 확인은 캡차 검증기가 트랜잭션 밖에서 한다). 칸 경로 {@code guest.name}·{@code guest.password}·{@code guest.captchaToken}.
     */
    public static void checkGuest(GuestCredentials guest, BoardSecurityValues s, Map<String, MessageRef> out) {
        if (guest == null) {
            out.put("guest.name", new MessageRef("board.guest.name.required"));
            return;
        }
        String name = normalize(guest.name());
        int nameLength = length(name);
        if (nameLength == 0) {
            out.put("guest.name", new MessageRef("board.guest.name.required"));
        } else if (nameLength < s.guestNameMin() || nameLength > s.guestNameMax() || name.indexOf('\n') >= 0) {
            out.put("guest.name", new MessageRef("board.guest.name.length", s.guestNameMin(), s.guestNameMax()));
        }
        String password = guest.password() == null ? "" : guest.password();
        int passwordLength = length(password);
        if (passwordLength < s.guestPasswordMin() || passwordLength > GUEST_PASSWORD_MAX) {
            out.put("guest.password", new MessageRef("board.guest.password.length", s.guestPasswordMin(), GUEST_PASSWORD_MAX));
        } else if (password.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAX_BYTES) {
            out.put("guest.password", new MessageRef("board.guest.password.tooLong", PASSWORD_MAX_BYTES));
        }
        if (s.guestCaptchaRequired() && (guest.captchaToken() == null || guest.captchaToken().isBlank())) {
            out.put("guest.captchaToken", new MessageRef("board.guest.captcha.required"));
        }
    }

    /** 금지어 검사를 하는가(켜져 있고 단어가 있다) */
    private static boolean active(BoardSecurityValues s) {
        return Boolean.TRUE.equals(s.blockedWordsEnabled()) && s.blockedWords() != null && !s.blockedWords().isEmpty();
    }

    /** 거부 방식일 때 처음 걸린 금지어(운영자가 정한 단어 그대로) — 걸리지 않았거나 가림 방식이면 비어 있다 */
    public static Optional<String> blockedWord(BoardSecurityValues s, String... texts) {
        if (!active(s) || !"BLOCK".equals(s.blockedWordsAction())) {
            return Optional.empty();
        }
        StringBuilder joined = new StringBuilder();
        for (String text : texts) {
            if (text != null) {
                joined.append(text.toLowerCase(Locale.ROOT)).append('\n');
            }
        }
        String haystack = joined.toString();
        return firstMatch(s.blockedWords(), haystack);
    }

    private static Optional<String> firstMatch(List<String> words, String haystack) {
        for (String word : words) {
            if (word != null && !word.isBlank() && haystack.contains(word.toLowerCase(Locale.ROOT))) {
                return Optional.of(word);
            }
        }
        return Optional.empty();
    }

    /** 가림 방식일 때 금지어를 같은 길이의 {@code *} 로 바꾼다(대소문자 무시). 거부 방식·꺼짐이면 그대로 */
    public static String mask(BoardSecurityValues s, String text) {
        if (text == null || !active(s) || !"MASK".equals(s.blockedWordsAction())) {
            return text;
        }
        String out = text;
        for (String word : s.blockedWords()) {
            if (word == null || word.isBlank()) {
                continue;
            }
            Matcher m = Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(out);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                m.appendReplacement(sb, "*".repeat(m.group().length()));
            }
            m.appendTail(sb);
            out = sb.toString();
        }
        return out;
    }
}
