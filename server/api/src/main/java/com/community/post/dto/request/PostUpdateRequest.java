package com.community.post.dto.request;

import tools.jackson.databind.JsonNode;

/**
 * {@code PUT /api/posts/{id}}(user-web {@code PostUpdateRequest}) — 비회원 글은 {@code password} 필수.
 *
 * @param contentFormat 본문 형식 TEXT · HTML — 빠지면 TEXT(예전 클라이언트)
 * @param poll          투표({@code PollInput} 모양) — <b>칸을 아예 빼면 그대로</b>, {@code null} 이면 빼기, 값이면 만들기·고치기(docs/features/post-poll-contract.md).
 *                      record 로는 「빠짐」과 「null」을 가를 수 없어 {@link JsonNode} 로 받는다 — 빠짐 = Java null, JSON null = {@code NullNode}({@code PollWriter#checkForUpdate})
 */
public record PostUpdateRequest(String prefixId, String title, String contentFormat, String content, Boolean adult, String password, JsonNode poll) {

    @Override
    public String toString() {
        return "PostUpdateRequest[prefixId=" + prefixId + ", password=(감춤)]";
    }
}
