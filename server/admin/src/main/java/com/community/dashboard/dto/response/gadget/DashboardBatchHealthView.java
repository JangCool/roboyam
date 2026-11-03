package com.community.dashboard.dto.response.gadget;

import java.time.Instant;
import java.util.List;

/** {@code BATCH_HEALTH} — 최근 24시간 실패(최근 차례, 최대 10) + 실행 중. {@code name} 은 요청 언어의 배치 이름, {@code message} 는 오류 문구 또는 결과 요약 */
public record DashboardBatchHealthView(List<Failed> failed, List<Running> running) implements DashboardGadgetData {

    public record Failed(String id, String name, Instant failedAt, String message) {
    }

    public record Running(String id, String name, Instant startedAt) {
    }
}
