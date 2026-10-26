package com.community.identity.dto;

/**
 * 인증을 시작할 때 화면(포트원 SDK)에 줄 값 — 비밀값이 아니다(API 비밀키는 싣지 않는다). 가짜 구현은 둘 다 null.
 *
 * @param storeId    포트원 상점 ID
 * @param channelKey 포트원 본인인증 채널 키
 */
public record IdentityStartInfo(String storeId, String channelKey) {

    public static IdentityStartInfo none() {
        return new IdentityStartInfo(null, null);
    }
}
