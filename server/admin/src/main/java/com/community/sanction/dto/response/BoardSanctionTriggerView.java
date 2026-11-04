package com.community.sanction.dto.response;

/** 계기 글·댓글 — {@code type} POST · COMMENT, {@code title} 줄 때의 글 제목(없으면 null). 지워졌어도 번호는 남는다 */
public record BoardSanctionTriggerView(String type, String postId, String commentId, String title) {
}
