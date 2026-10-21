package com.community.batch.dto.response;

import java.util.List;

/** 배치 로그 한 쪽 — {@code page} 는 0 부터 */
public record BatchLogPage(List<BatchLogView> items, long total, int page, int size) {
}
