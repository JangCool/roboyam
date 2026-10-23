package com.community.notificationtemplate.dto.response;

import java.util.List;

/** 대상·채널이 쓸 수 있는 조건 하나(코드가 정한 선택지만 — 자유 조건식 없음) */
public record MessageConditionView(String key, String label, List<MessageConditionOptionView> options) {
}
