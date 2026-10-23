package com.community.notificationtemplate.dto.response;

import java.util.List;
import java.util.Map;

/**
 * 대상의 채널 하나 요약 — 이벤트 목록의 「쓰는 문구」 · 필수 안내 메일 목록 · 편집 화면(변수·조건)이 쓴다.
 *
 * @param channel      {@code IN_APP|EMAIL|SLACK|MATTERMOST|KAKAO}
 * @param preparing    준비 중인 채널(카카오톡) — 보기만 하고 고르거나 만들 수 없다
 * @param variables    쓸 수 있는 변수 — 받는 사람의 공통 변수 + 대상·채널의 변수
 * @param selected     언어별({@code ko}·{@code en}) 쓰는 문구 — 쓸 문구가 없으면(카카오톡) 값이 null
 * @param messageCount 이 대상·채널의 사용자 문구 수(상한 {@code MESSAGE_LIMIT})
 */
public record MessageChannelSummaryView(String channel, boolean preparing, List<MessageVariableView> variables, List<MessageConditionView> conditions,
                                        Map<String, MessageSelectedView> selected, int ruleCount, int messageCount) {
}
