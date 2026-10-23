package com.community.notificationtemplate.engine;

/**
 * 앱 알림(관리자 종 서랍 · 회원 알림함) 한 줄 — 렌더링 엔진의 결과. 평문이다(그리는 쪽이 글자로 보인다 — HTML 로 넣지 않는다).
 *
 * @param messageId 고른 문구 id
 * @param title     제목(값을 채운 한 줄)
 * @param body      본문(값을 채운 평문, 없으면 빈 글자)
 */
public record RenderedInApp(String messageId, String title, String body) {
}
