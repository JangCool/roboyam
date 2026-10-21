package com.community.installation.dto.request;

/** 설치 토큰 확인 요청. {@link #toString()} 은 토큰을 감춘다 */
public record VerifyTokenRequest(String token) {

    @Override
    public String toString() {
        return "VerifyTokenRequest[token=" + (token == null ? "null" : "(감춤)") + "]";
    }
}
