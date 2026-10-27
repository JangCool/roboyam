package com.community.portone.dto.request;

/**
 * 포트원 본인인증 카드 저장({@code PUT /api/admin/portone-settings/identity}) — 본인인증 채널 키 하나(상점 ID·API 비밀키는 공통 카드 — 2a 결정 14). null = 안 보낸 칸.
 * 검사는 서비스가 한다({@code fields.identityChannelKey}: 빈 값·500자 초과, 아무 칸도 없으면 400 {@code VALIDATION}). {@link #toString()} 은 값을 싣지 않는다.
 */
public record PortOneIdentitySaveRequest(String identityChannelKey) {

    @Override
    public String toString() {
        return "PortOneIdentitySaveRequest[identityChannelKey=" + (identityChannelKey == null ? "null" : "(감춤)") + "]";
    }
}
