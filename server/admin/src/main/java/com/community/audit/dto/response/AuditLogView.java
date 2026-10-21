package com.community.audit.dto.response;

import java.time.Instant;

/**
 * 감사 기록 한 줄 — admin-web 의 {@code AuditLogView} 모양이다. 달라진 점은 계약서(roles-contract.md 4단계)에 있다:
 * {@code auditNo} 는 문자열(Snowflake)이고 {@code actorId}·{@code actorEmail} 은 null 일 수 있으며, {@code result} 에 {@code DENIED} 가 있고, {@code actorType} 이 더해졌다.
 *
 * @param auditNo        기록 ID(문자열)
 * @param occurredAt     일어난 시각
 * @param actorId        행위자 운영자 ID. 시스템·로그인 전 행위는 null
 * @param actorType      {@code OPERATOR} · {@code SYSTEM} · {@code ANONYMOUS}
 * @param actorEmail     늘 null — 조회 응답은 이메일을 내지 않는다(행위자는 이름으로 표시)
 * @param actorName      행위자 이름(지금의 것). 시스템은 「시스템」, 로그인 전 행위는 「로그인하지 않은 사용자」, 지워진 운영자는 null(화면이 ID 로 대신한다)
 * @param actorIp        요청자 IP
 * @param action         작업 코드
 * @param actionName     작업 이름(요청 언어). 모르는 코드는 코드 그대로
 * @param targetType     대상 종류. 없으면 null
 * @param targetTypeName 대상 종류 이름(요청 언어). 모르는 종류는 종류 그대로
 * @param targetId       대상 식별자
 * @param targetName     대상의 지금 이름(운영자·역할·회원·배치·사이트·등급 설정). 모르거나 지워졌으면 null — 기록에는 이름을 남기지 않는다
 * @param result         {@code OK} · {@code FAIL} · {@code DENIED}
 * @param message        실패·거부 사유 문구(요청 언어). 없으면 null
 * @param changes        바뀐 내용 — {@code [{field, before, after}]} 의 JSON <b>문자열</b>. 보기만 한 줄 등 바뀐 것이 없으면 null
 */
public record AuditLogView(String auditNo, Instant occurredAt, String actorId, String actorType, String actorEmail, String actorName, String actorIp, String action,
                           String actionName, String targetType, String targetTypeName, String targetId, String targetName, String result, String message, String changes) {
}
