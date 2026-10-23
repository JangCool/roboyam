package com.community.notificationtemplate.dto.response;

/** 특수 상황 규칙 하나 — 위에서부터 차례로 보고 맞는 첫 규칙의 문구를 쓴다(없으면 언어별 쓸 문구) */
public record MessageRuleView(String id, MessageRuleConditionView condition, String messageId) {
}
