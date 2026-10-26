package com.community.group.dto.request;

/**
 * {@code PUT /api/groups/{id}/managers/{userId}} — 임명 · 맡은 일 바꾸기. {@code permissions} 는 맡은 일 키 배열(1~5개, 중복 없이). JSON 값 그대로({@code Object}) 받아
 * 서비스가 형을 본다(배열이 아니거나 글자가 아닌 값·모르는 키·중복·빈 배열은 {@code 400 fields.permissions}).
 */
public record GroupManagerRequest(Object permissions) {
}
