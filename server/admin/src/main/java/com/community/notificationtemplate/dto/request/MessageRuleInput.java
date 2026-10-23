package com.community.notificationtemplate.dto.request;

/** 규칙 한 줄 — 새 규칙은 {@code id} 없이 */
public record MessageRuleInput(String id, MessageRuleConditionInput condition, String messageId) {
}
