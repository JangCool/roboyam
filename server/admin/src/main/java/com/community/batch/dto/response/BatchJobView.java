package com.community.batch.dto.response;

import java.time.Instant;

/**
 * 배치 목록의 한 줄 — 카탈로그의 정의 + 운영 설정 + 마지막 실행 요약. 이름·설명은 서버가 요청 언어로 풀어 준다(그래서 {@code i18n}·{@code descI18n} 은 항상 null).
 *
 * @param jobName       배치 키
 * @param category      {@code MEMBER} · {@code SYSTEM}
 * @param source        항상 {@code JAVA}(애플리케이션 스케줄러 — DB 가 돌리는 배치는 이 제품에 없다)
 * @param schedule      지금 쓰는 cron(Spring 6칸, 시간대 Asia/Seoul)
 * @param nextRunAt     다음 실행 예정 시각(UTC). 꺼져 있거나 더는 실행되지 않으면 null
 * @param lastRunAt     마지막 실행 시작 시각. 한 번도 안 돌았으면 null
 * @param lastStatus    {@code RUNNING} · {@code OK} · {@code FAIL} · {@code SKIP}. 한 번도 안 돌았으면 null
 * @param lastMessage   마지막 실행의 요약(실패면 사유). 없으면 null
 * @param running       지금 실행 중인가
 * @param runnable      지금 실행할 수 있는가(항상 true)
 */
public record BatchJobView(String jobName, String displayName, String description, String i18n, String descI18n, String category, String source, String schedule,
                           String defaultSchedule, boolean enabled, Instant nextRunAt, Instant lastRunAt, String lastStatus, Long lastAffectedCount,
                           String lastMessage, boolean running, boolean runnable) {
}
