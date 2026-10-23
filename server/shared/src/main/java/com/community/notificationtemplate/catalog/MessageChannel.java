package com.community.notificationtemplate.catalog;

import java.util.Arrays;
import java.util.Optional;

/**
 * 알림 문구의 채널 — 이벤트가 나가는 길(docs/features/message-template-contract.md 「개정 4」). 화면·DB 와 상수 이름 그대로 주고받는다.
 *
 * <ul>
 *   <li>{@link #IN_APP} 앱 알림(관리자 종 서랍 · 회원 알림함) — 제목 한 줄 + 짧은 평문. 템플릿이 없다.</li>
 *   <li>{@link #EMAIL} 이메일 — 제목 + 간단 서식 본문(버튼 블록 하나까지) + 메일 템플릿.</li>
 *   <li>{@link #SLACK} · {@link #MATTERMOST} — 제목 한 줄 + 그 앱 표기 본문 + 그 종류의 템플릿(첨부 카드).</li>
 *   <li>{@link #KAKAO} 카카오톡 알림톡 — <b>준비 중</b>(알림 도구에서 카카오가 업체 계약 전). 보기만 하고 만들거나 고르지 못한다(409 {@code CHANNEL_PREPARING}).</li>
 * </ul>
 */
public enum MessageChannel {

    IN_APP,
    EMAIL,
    SLACK,
    MATTERMOST,
    KAKAO;

    /** 준비 중인 채널 — 보기만 하고 만들거나 고를 수 없다 */
    public boolean preparing() {
        return this == KAKAO;
    }

    /** 템플릿(겉모양)을 고르는 채널인가 — 앱 알림만 아니다 */
    public boolean templated() {
        return this != IN_APP;
    }

    /** Slack · Mattermost 인가 */
    public boolean chat() {
        return this == SLACK || this == MATTERMOST;
    }

    /** 이름으로 찾는다(대소문자 그대로). 모르는 값이면 비어 있다 */
    public static Optional<MessageChannel> of(String name) {
        return Arrays.stream(values()).filter(channel -> channel.name().equals(name)).findFirst();
    }
}
