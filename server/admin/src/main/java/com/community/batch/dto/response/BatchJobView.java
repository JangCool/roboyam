package com.community.batch.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 배치 목록의 한 줄 — 카탈로그의 정의 + 운영 설정 + 마지막 실행 요약 + 실행 요청·파라미터·단계. Java 배치와 DB 배치(저장 프로시저 + pg_cron)가 같은 모양이다.
 * 이름·설명은 서버가 요청 언어로 풀어 준다(그래서 {@code i18n}·{@code descI18n} 은 항상 null).
 *
 * @param jobName           배치 키
 * @param category          {@code MEMBER} · {@code SYSTEM}
 * @param source            {@code JAVA}(애플리케이션 스케줄러) · {@code POSTGRESQL}(DB 가 돌린다 — pg_cron)
 * @param schedule          지금 쓰는 cron. Java 배치는 Spring 6칸(초 분 시 일 월 요일), DB 배치는 pg_cron 5칸(분 시 일 월 요일). 쓸 수 없는 DB 배치는 기본 cron(모르면 빈 글)
 * @param defaultSchedule   기본 cron(같은 문법)
 * @param nextRunAt         다음 실행 예정 시각(UTC). 꺼져 있거나 쓸 수 없거나 더는 실행되지 않으면 null
 * @param lastRunAt         마지막 실행 시작 시각. 한 번도 안 돌았으면 null
 * @param lastStatus        {@code RUNNING} · {@code OK} · {@code FAIL} · {@code SKIP}. 한 번도 안 돌았으면 null
 * @param lastMessage       마지막 실행의 요약(실패면 사유). 없으면 null
 * @param running           지금 실행 중인가
 * @param runnable          지금 실행할 수 있는가. Java 배치는 항상 true, DB 배치는 {@code available} 과 같다
 * @param available         쓸 수 있는가. Java 배치는 항상 true. DB 배치는 pg_cron 을 쓸 수 없거나(확장·서버 설정·권한) DB 에 등록되어 있지 않으면 false —
 *                          이때 켜기/끄기·cron 수정·지금 실행은 409 {@code BATCH_UNAVAILABLE}
 * @param unavailableReason {@code available=false} 인 이유(요청 언어로 푼 글). 쓸 수 있으면 null
 * @param scheduleZone      {@code schedule} 을 해석하는 시간대(IANA 이름). Java 배치는 {@code roboyam.batch.zone}, DB 배치는 pg_cron 의 {@code cron.timezone}(기본 GMT)
 * @param lastRunId         마지막 실행(지금 도는 중이면 그 실행)의 로그 번호. 한 번도 안 돌았으면 null
 * @param pendingRequest    아직 끝나지 않은 요청(PENDING·RUNNING). 없으면 null — 있는 동안 지금 실행은 409 {@code BATCH_RUNNING}
 * @param params            받을 수 있는 실행 파라미터(없으면 빈 배열)
 * @param steps             단계(차례대로 — 한 단계짜리도 1개)
 * @param resumable         「실패 지점부터 다시」를 지원하나 — Java 작업만 true 일 수 있다
 */
public record BatchJobView(String jobName, String displayName, String description, String i18n, String descI18n, String category, String source, String schedule,
                           String defaultSchedule, boolean enabled, Instant nextRunAt, Instant lastRunAt, String lastStatus, Long lastAffectedCount,
                           String lastMessage, boolean running, boolean runnable, boolean available, String unavailableReason, String scheduleZone,
                           String lastRunId, BatchRunRequestView pendingRequest, List<BatchParamDefView> params, List<BatchStepDefView> steps, boolean resumable) {
}
