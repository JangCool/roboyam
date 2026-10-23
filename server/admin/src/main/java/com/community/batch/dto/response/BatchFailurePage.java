package com.community.batch.dto.response;

import java.util.List;

/** 실행의 건별 실패 한 쪽 — {@code page} 는 0 부터, 일어난 시각 최근순 */
public record BatchFailurePage(List<BatchRunFailureView> items, long total, int page, int size) {
}
