package com.community.profile.dto.response;

import java.util.List;

/** 내 로그인 이력 — 최근 것부터, 최근 20건 고정(쪽 나누기 없음) */
public record LoginHistoryResponse(List<LoginHistoryItem> items) {
}
