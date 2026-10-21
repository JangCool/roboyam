package com.community.site.service;

import com.community.zconfiguration.message.MessageRef;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

/**
 * 사이트 이름·공개 주소·슬로건 규칙. 설치 마법사와 운영자 설정 화면이 같은 규칙을 쓴다(화면은 `util-site.ts` 가 미리 검사하고 판정은 서버가 한다).
 *
 * <ul>
 *   <li>사이트 이름: 앞뒤 공백을 자른 뒤 1~50자, 제어 문자 없음, {@code <}·{@code >} 없음</li>
 *   <li>공개 주소: {@code http(s)://호스트[:포트]} — <b>끝 {@code /} 없음, 경로·쿼리·조각·사용자 정보 없음</b>, 호스트는 ASCII, 포트는 1~65535, 200자 이하</li>
 *   <li>슬로건(한 줄 소개, 선택): 앞뒤 공백을 자른 뒤 <b>비어도 된다</b>(없음 = {@code null}), 있으면 1~100자, 제어 문자 없음, {@code <}·{@code >} 없음.
 *       DB 의 {@code ck_site_settings_tagline}(V5)이 같은 규칙을 한 번 더 지킨다</li>
 * </ul>
 * 위반이 없으면 {@code violation} 메서드는 empty 를 돌려준다. 위반은 메시지 키 + 인자({@link MessageRef})로 돌려주고 요청 언어로 푸는 일은 응답을 만드는 자리가 한다. 문구에는 입력한 값을 싣지 않는다.
 */
public final class SitePolicy {

    public static final int NAME_MAX_LENGTH = 50;
    public static final int PUBLIC_URL_MAX_LENGTH = 200;
    public static final int TAGLINE_MAX_LENGTH = 100;

    private SitePolicy() {
    }

    /** 앞뒤 공백을 자른 사이트 이름(없으면 null) */
    public static String normalizeName(String name) {
        return name == null ? null : name.strip();
    }

    /** 앞뒤 공백을 자른 슬로건. 입력이 없거나 공백뿐이면 {@code null}(= 문구 없음) — 저장도 {@code null} 로 한다 */
    public static String normalizeTagline(String tagline) {
        if (tagline == null) {
            return null;
        }
        String stripped = tagline.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /** 슬로건 규칙을 어기면 위반 문구(키)를 돌려준다. {@code tagline} 은 {@link #normalizeTagline} 을 거친 값이고, 없음({@code null})은 위반이 아니다 */
    public static Optional<MessageRef> taglineViolation(String tagline) {
        if (tagline == null) {
            return Optional.empty();
        }
        if (tagline.codePointCount(0, tagline.length()) > TAGLINE_MAX_LENGTH) {
            return Optional.of(new MessageRef("site.tagline.length", TAGLINE_MAX_LENGTH));
        }
        if (tagline.chars().anyMatch(Character::isISOControl)) {
            return Optional.of(new MessageRef("site.tagline.control"));
        }
        if (tagline.indexOf('<') >= 0 || tagline.indexOf('>') >= 0) {
            return Optional.of(new MessageRef("site.tagline.angle"));
        }
        return Optional.empty();
    }

    /** 사이트 이름 규칙을 어기면 위반 문구를 돌려준다. {@code name} 은 {@link #normalizeName} 을 거친 값이다 */
    public static Optional<MessageRef> nameViolation(String name) {
        if (name == null || name.isEmpty()) {
            return Optional.of(new MessageRef("site.name.required"));
        }
        if (name.codePointCount(0, name.length()) > NAME_MAX_LENGTH) {
            return Optional.of(new MessageRef("site.name.length", NAME_MAX_LENGTH));
        }
        if (name.chars().anyMatch(Character::isISOControl)) {
            return Optional.of(new MessageRef("site.name.control"));
        }
        if (name.indexOf('<') >= 0 || name.indexOf('>') >= 0) {
            return Optional.of(new MessageRef("site.name.angle"));
        }
        return Optional.empty();
    }

    /** 앞뒤 공백을 자르고 스킴·호스트를 소문자로 맞춘 공개 주소. 규칙에 맞지 않으면 자른 값을 그대로 돌려주므로 맞는 값에만 쓴다 */
    public static String normalizePublicUrl(String url) {
        String stripped = url == null ? null : url.strip();
        if (stripped == null || publicUrlViolation(stripped).isPresent()) {
            return stripped;
        }
        URI uri = parse(stripped);
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT)
                + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    }

    /** 공개 주소 규칙을 어기면 위반 문구를 돌려준다. {@code url} 은 앞뒤 공백을 자른 값이다 */
    public static Optional<MessageRef> publicUrlViolation(String url) {
        if (url == null || url.isEmpty()) {
            return Optional.of(new MessageRef("site.publicUrl.required"));
        }
        if (url.length() > PUBLIC_URL_MAX_LENGTH) {
            return Optional.of(new MessageRef("site.publicUrl.length", PUBLIC_URL_MAX_LENGTH));
        }
        URI uri = parse(url);
        MessageRef shape = new MessageRef("site.publicUrl.shape");
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            return Optional.of(shape);
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return Optional.of(shape);
        }
        // 호스트는 ASCII 만(한글 도메인은 퓨니코드로 입력한다). 사용자 정보·경로·쿼리·조각이 있으면 거절한다
        boolean ascii = url.chars().allMatch(c -> c > 0x20 && c < 0x7f);
        boolean plain = uri.getRawUserInfo() == null && uri.getRawPath().isEmpty() && uri.getRawQuery() == null
                && uri.getRawFragment() == null;
        boolean portOk = uri.getPort() == -1 || (uri.getPort() >= 1 && uri.getPort() <= 65535);
        if (!ascii || !plain || !portOk) {
            return Optional.of(shape);
        }
        return Optional.empty();
    }

    private static URI parse(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
