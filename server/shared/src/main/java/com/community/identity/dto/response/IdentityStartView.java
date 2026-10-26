package com.community.identity.dto.response;

import com.community.identity.dto.IdentityProviderKind;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 인증 시작 — {@code POST /api/identity-verifications}. 서버가 인증 번호를 먼저 발급한다(30분 유효). {@code storeId}·{@code channelKey} 는 포트원일 때만 나간다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IdentityStartView(String verificationId, IdentityProviderKind provider, String storeId, String channelKey) {
}
