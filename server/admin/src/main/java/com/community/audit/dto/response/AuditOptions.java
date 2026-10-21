package com.community.audit.dto.response;

import java.util.List;

/**
 * 감사 로그 거르기 선택지. {@code actions} 의 원천은 서버 {@code AuditActions} 상수(선언한 차례), {@code targetTypes} 는 서버가 기록하는 대상 종류, {@code results} 는 결과 3종이다.
 * 이름은 요청 언어({@code Accept-Language})로 푼다.
 */
public record AuditOptions(List<AuditOption> actions, List<AuditOption> targetTypes, List<AuditOption> results) {
}
