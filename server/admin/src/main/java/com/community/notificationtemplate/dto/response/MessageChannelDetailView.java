package com.community.notificationtemplate.dto.response;

import java.util.List;
import java.util.Map;

/**
 * 대상 상세의 채널 하나.
 *
 * @param defaults 언어별 기본 문구 id — 기본 문구가 없는 채널(카카오톡)은 값이 null
 * @param selected 언어별 「쓸 문구」 id(없으면 빈 글자 — 카카오톡)
 * @param rules    특수 상황 규칙(차례 그대로)
 * @param messages 이 대상·채널의 문구 전부(기본 먼저, 그다음 사용자 문구 — 만든 차례)
 */
public record MessageChannelDetailView(String channel, Map<String, String> defaults, Map<String, String> selected, List<MessageRuleView> rules,
                                       List<MessageView> messages) {
}
