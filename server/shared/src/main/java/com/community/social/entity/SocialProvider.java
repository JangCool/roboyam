package com.community.social.entity;

import java.util.Arrays;
import java.util.Optional;

/** 소셜 로그인 공급자. social_provider_settings.provider 의 CHECK 와 같은 값이다. {@code id} 는 콜백 주소에 쓰는 소문자 이름이다. */
public enum SocialProvider {
    GOOGLE("google"),
    KAKAO("kakao"),
    NAVER("naver");

    private final String id;

    SocialProvider(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** 공급자 콘솔에 등록할 콜백 경로({@code /api/auth/social/kakao/callback}) — 공개 주소 뒤에 붙인다. 공개 서버 {@code SocialLoginService.CALLBACK_PATH} 와 같다 */
    public String callbackPath() {
        return "/api/auth/social/" + id + "/callback";
    }

    /** 화면에 보이는 이름의 메시지 키({@code social.provider.<이름>}) — 요청 언어로 풀린다 */
    public String displayNameKey() {
        return "social.provider." + name();
    }

    /** 대소문자를 무시하고 이름({@code NAVER})이나 id({@code naver})로 찾는다 */
    public static Optional<SocialProvider> from(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String stripped = name.strip();
        return Arrays.stream(values()).filter(p -> p.name().equalsIgnoreCase(stripped) || p.id.equalsIgnoreCase(stripped)).findFirst();
    }
}
