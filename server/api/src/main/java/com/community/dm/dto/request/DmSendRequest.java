package com.community.dm.dto.request;

/** {@code POST /api/users/{id}/dm} — 본문 검사(빈 글·500자·제어 문자)는 서비스가 한다 */
public record DmSendRequest(String body) {

    @Override
    public String toString() {
        return "DmSendRequest[body=(감춤)]";
    }
}
