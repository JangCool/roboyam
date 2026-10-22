package com.community.membernotification.dto.response;

/** {@code POST /api/notifications/read-all} — 읽음으로 바꾼 개수(없으면 0) */
public record ReadAllResultView(long updated) {
}
