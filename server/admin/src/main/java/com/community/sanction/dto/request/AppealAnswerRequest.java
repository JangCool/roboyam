package com.community.sanction.dto.request;

/** {@code POST /api/admin/appeals/{id}/accept|reject} — 답변 1~500자 */
public record AppealAnswerRequest(String answer) {
}
