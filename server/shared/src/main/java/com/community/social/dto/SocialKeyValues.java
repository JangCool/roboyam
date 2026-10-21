package com.community.social.dto;

/**
 * 검사를 마친 소셜 로그인 키 한 건. {@code clientSecret} 이 null 이면 시크릿을 비운 것이다. {@link #toString()} 은 시크릿을 감춘다.
 */
public record SocialKeyValues(String clientId, String clientSecret, boolean enabled) {

    @Override
    public String toString() {
        return "SocialKeyValues[clientId=" + clientId + ", clientSecret=" + (clientSecret == null ? "null" : "(감춤)")
                + ", enabled=" + enabled + "]";
    }
}
