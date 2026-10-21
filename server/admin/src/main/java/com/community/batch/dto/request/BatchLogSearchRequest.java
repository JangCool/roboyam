package com.community.batch.dto.request;

/**
 * 배치 로그 조회 조건(쿼리 문자열) — 빈 조건은 보내지 않는다. 값 해석은 서비스가 하고 틀린 값은 칸 이름과 함께 400 으로 알린다.
 *
 * @param jobName 배치 키(예: {@code grade-promotion})
 * @param status  {@code RUNNING} · {@code OK} · {@code FAIL} · {@code SKIP}
 * @param trigger {@code SCHEDULED} · {@code MANUAL}
 * @param from    시작 시각의 하한(UTC ISO 순간, 포함)
 * @param to      시작 시각의 상한(UTC ISO 순간, 포함)
 * @param page    0 부터(기본 0)
 * @param size    1~100(기본 20)
 */
public record BatchLogSearchRequest(String jobName, String status, String trigger, String from, String to, String page, String size) {
}
