package com.community.notificationtemplate.engine;

/**
 * 이메일 한 통 — 렌더링 엔진의 결과. {@code MailDispatcher#sendTagged(to, subject, untaggedSubject, html)} 에 그대로 넘긴다
 * (메일 설정에 제목 머리말이 있으면 {@code untaggedSubject} 앞에 머리말이 붙고, 없으면 {@code subject} 를 쓴다).
 *
 * @param messageId       고른 문구 id(로그·감사용)
 * @param subject         {@code [사이트 이름] 제목}
 * @param untaggedSubject 사이트 이름 머리말을 뺀 제목(값을 채운 한 줄)
 * @param html            문서 HTML(메일 템플릿을 씌운 것)
 */
public record RenderedEmail(String messageId, String subject, String untaggedSubject, String html) {
}
