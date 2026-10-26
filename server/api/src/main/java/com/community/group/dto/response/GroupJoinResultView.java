package com.community.group.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code POST /api/groups/{id}/join} — 바로 가입 {@code 200 {result: JOINED, memberCount}} · 신청 {@code 202 {result: APPLIED}}.
 *
 * @param memberCount 바로 가입했을 때 바뀐 멤버 수(신청이면 칸이 없다)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupJoinResultView(String result, Integer memberCount) {
}
