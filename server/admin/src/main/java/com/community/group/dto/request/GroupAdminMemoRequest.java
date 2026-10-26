package com.community.group.dto.request;

/** 다시 열기 메모(0~200자, 선택 — 운영자만 본다, 알림에 싣지 않는다) */
public record GroupAdminMemoRequest(String memo) {
}
