package com.community.comment.dto.request;

/** {@code PUT /api/comments/{id}} — 비회원 댓글은 {@code password} 필수 */
public record CommentUpdateRequest(String content, String password) {

    @Override
    public String toString() {
        return "CommentUpdateRequest[password=(감춤)]";
    }
}
