package com.community.notificationtemplate.dto.request;

import java.util.List;
import java.util.Map;

/** 채널 하나의 저장 값 — 언어별({@code ko}·{@code en}) 쓸 문구 id + 규칙 목록 전체(차례 그대로) */
public record MessageChannelSaveRequest(Map<String, String> selected, List<MessageRuleInput> rules) {
}
