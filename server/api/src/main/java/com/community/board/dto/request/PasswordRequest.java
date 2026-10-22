package com.community.board.dto.request;

/**
 * 비밀번호 하나를 싣는 본문 — 글·댓글 지우기(비회원 글이면 필수, 회원 글이면 null)와 비회원 글 비밀번호 확인({@code POST /api/posts/{id}/verify-password}).
 * 비밀번호를 주소에 싣지 않으려고 본문으로 받는다.
 */
public record PasswordRequest(String password) {

    @Override
    public String toString() {
        return "PasswordRequest[password=(감춤)]";
    }
}
