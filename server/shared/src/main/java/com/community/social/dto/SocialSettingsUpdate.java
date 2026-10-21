package com.community.social.dto;

/**
 * 소셜 로그인 키 한 건의 입력. {@link #toString()} 은 시크릿을 감춘다.
 *
 * @param enabled 생략하면 true
 */
public record SocialSettingsUpdate(String clientId, String clientSecret, Boolean enabled) {

    @Override
    public String toString() {
        return "SocialSettingsUpdate[clientId=" + clientId + ", clientSecret=" + (clientSecret == null ? "null" : "(감춤)")
                + ", enabled=" + enabled + "]";
    }
}
