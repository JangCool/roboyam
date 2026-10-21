package com.community.audit.dto.response;

/** 거르기 선택지 하나 — {@code value} 는 조회 조건에 보내는 값, {@code label} 은 요청 언어의 이름 */
public record AuditOption(String value, String label) {
}
