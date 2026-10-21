package com.community.batch.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 배치 cron 수정 — Spring 6칸 식(초 분 시 일 월 요일). 형식·실행 간격 검사는 서비스가 한다 */
public record BatchScheduleRequest(
        @NotBlank(message = "{batch.schedule.required}") @Size(max = 100, message = "{batch.schedule.format}") String schedule) {
}
