package com.community.post.dto.request;

/**
 * {@code PUT /api/posts/{id}}(user-web {@code PostUpdateRequest}) — 비회원 글은 {@code password} 필수.
 *
 * @param contentFormat 본문 형식 TEXT · HTML — 빠지면 TEXT(예전 클라이언트)
 */
public record PostUpdateRequest(String prefixId, String title, String contentFormat, String content, Boolean adult, String password) {

    @Override
    public String toString() {
        return "PostUpdateRequest[prefixId=" + prefixId + ", password=(감춤)]";
    }
}
