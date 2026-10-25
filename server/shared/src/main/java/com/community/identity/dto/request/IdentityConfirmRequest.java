package com.community.identity.dto.request;

/**
 * 인증 완료 {@code POST /api/identity-verifications/{id}/confirm} 의 본문. 포트원은 비어 있고(서버가 포트원에 결과를 묻는다), 가짜 구현만 {@code fake} 를 읽는다.
 */
public record IdentityConfirmRequest(FakeIdentityInput fake) {
}
