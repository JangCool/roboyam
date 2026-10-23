package com.community.notificationtemplate.dto.request;

/** 규칙의 조건 — 조건 이름 하나 + 값 하나 */
public record MessageRuleConditionInput(String key, String value) {
}
