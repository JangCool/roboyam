package com.community.comment.dto.request;

/** {@code PUT /api/comments/{id}} — 비회원 댓글은 {@code password} 필수. {@code contentFormat} TEXT · HTML(빠지면 TEXT) */
public record CommentUpdateRequest(String contentFormat, String content, String password) {

    @Override
    public String toString() {
        return "CommentUpdateRequest[password=(감춤)]";
    }
}
