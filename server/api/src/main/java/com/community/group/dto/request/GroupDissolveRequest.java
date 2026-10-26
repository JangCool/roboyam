package com.community.group.dto.request;

/** {@code POST /api/groups/{id}/dissolve} — 소모임 이름을 그대로 입력해야 한다(다르면 {@code fields.name}) */
public record GroupDissolveRequest(String name) {
}
