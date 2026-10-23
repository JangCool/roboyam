package com.community.notificationtemplate.dto.response;

/** Slack · Mattermost 시험 게시 결과 — 알림 도구 연결 시험과 같은 모양(실패도 200, 문구는 요청 언어로 푼 한 줄 — 외부 응답 본문은 싣지 않는다) */
public record ChatMessageTestResultView(boolean ok, String message) {
}
