package com.community.audit.dto.request;

/**
 * 감사 로그 조회 조건(쿼리 문자열) — 빈 조건은 보내지 않는다. 값 해석은 서비스가 하고 틀린 값은 칸 이름과 함께 400 으로 알린다(admin-web {@code AuditQuery}).
 *
 * @param from       이 시각 이후(UTC ISO 순간, 포함)
 * @param to         이 시각 이전(UTC ISO 순간, 포함)
 * @param actor      행위자 — 운영자 ID 와 같거나 이름의 일부(대소문자 무시). 이메일은 맞추지 않는다
 * @param action     작업 코드(예: {@code BATCH_RUN})
 * @param targetType 대상 종류(예: {@code OPERATOR})
 * @param target     대상 — ID 의 일부 또는 지금의 이름(운영자·역할·회원·배치)의 일부
 * @param result     {@code OK} · {@code FAIL} · {@code DENIED}
 * @param page       0 부터(기본 0)
 * @param size       1~100(기본 20)
 */
public record AuditSearchRequest(String from, String to, String actor, String action, String targetType, String target, String result, String page, String size) {
}
