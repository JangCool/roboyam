package com.community.group.dto.response;

/** {@code POST /api/groups} — 승인제 {@code 202 {id, status: PENDING}} · 자유 개설 {@code 201 {id, status: ACTIVE}} */
public record GroupCreateResultView(String id, String status) {
}
