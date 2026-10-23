package com.community.notificationtemplate.engine;

import com.community.notificationtemplate.catalog.MessageChannel;
import java.util.Map;

/**
 * Slack · Mattermost 로 올릴 메시지 카드 한 장 — 렌더링 엔진의 결과. 그 앱 API 의 <b>첨부(attachment) 하나</b>를 이미 만들어 두었고, 올리는 쪽
 * ({@code NotificationChannelPoster#post(NotificationChannelEntity, RenderedChat)})이 도구의 연결 방식(Slack {@code chat.postMessage} · Mattermost 웹훅 · 봇 API)에 맞게 감싼다.
 * 변수 값의 멘션·표기 기호는 이미 무력화돼 있다.
 *
 * @param tool         {@link MessageChannel#SLACK} · {@link MessageChannel#MATTERMOST}
 * @param fallbackText 알림 미리보기·로그용 한 줄(그림 이름 + 제목)
 * @param title        제목 줄(값을 채우고 무력화한 한 줄)
 * @param text         본문(템플릿 틀에 문구 본문을 넣고 값을 채운 그 앱 표기)
 * @param color        색 막대 {@code #rrggbb}
 * @param footer       꼬리말 한 줄(없으면 빈 글자)
 * @param buttonLabel  「열기」 버튼 글자(없으면 null)
 * @param buttonUrl    「열기」 버튼 주소(없으면 null — 링크 값이 없거나 http·https 가 아니면 버튼을 그리지 않는다)
 * @param attachment   그 앱 API 의 첨부 하나(JSON 으로 그대로 직렬화한다)
 */
public record RenderedChat(MessageChannel tool, String fallbackText, String title, String text, String color, String footer, String buttonLabel, String buttonUrl,
                           Map<String, Object> attachment) {
}
