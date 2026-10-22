package com.community.notification.dto.response;

import java.time.Instant;

/** 연결 시험 결과 — {@code message} 는 짧은 사유 코드를 요청 언어로 푼 문구다(외부 서비스의 응답 본문이 아니다). 시험이 실패한 것도 오류가 아니라 {@code ok=false} 로 돌아온다 */
public record NotificationChannelTestResultView(boolean ok, String message, Instant testedAt) {
}
