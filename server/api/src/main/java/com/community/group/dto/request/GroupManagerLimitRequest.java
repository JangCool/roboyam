package com.community.group.dto.request;

/**
 * {@code PUT /api/groups/{id}/manager-limit} — 이 소모임의 매니저 인원 상한. JSON 값 그대로({@code Object}) 받아 서비스가 정수인지 본다
 * (문자열 {@code "3"}·소수·빠진 칸은 {@code 400 fields.limit}).
 */
public record GroupManagerLimitRequest(Object limit) {
}
