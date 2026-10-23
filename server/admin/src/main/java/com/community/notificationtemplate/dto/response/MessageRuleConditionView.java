package com.community.notificationtemplate.dto.response;

/** 특수 상황 규칙의 조건 — 조건 이름 하나 + 값 하나 */
public record MessageRuleConditionView(String key, String value) {
}
