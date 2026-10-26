package com.community.group.dto.request;

/** 개설 반려·비활성화 사유(1~200자, 필수 — 신청자·모임장에게 그대로 간다) */
public record GroupAdminReasonRequest(String reason) {
}
