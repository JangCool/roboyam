package com.community.notification.dto.response;

import java.util.List;

/**
 * 알림 도구 한 개. <b>비밀(봇 토큰·웹훅 주소)은 값도 암호문도 싣지 않고</b> {@code secretKeys}(저장돼 있는 비밀 칸 이름: {@code botToken}·{@code webhookUrl}) 로 「입력됨」만 알린다.
 * 기본 제공 도구(서랍·이메일·브라우저)는 {@code builtIn=true} 이고 id 가 {@code drawer}·{@code email}·{@code browser} 다 — 이름을 바꾸거나 지울 수 없다.
 *
 * @param lastTest          마지막 연결 시험 결과. 시험한 적이 없거나 시험할 수 없는 도구(서랍·브라우저)는 null
 * @param usedByEventCount  이 도구를 전달 도구로 고른 알림 이벤트 수
 */
public record NotificationChannelView(String channelId, String type, String name, String description, boolean enabled, boolean builtIn, NotificationChannelConfigView config,
                                      List<String> secretKeys, NotificationChannelTestResultView lastTest, int usedByEventCount) {
}
