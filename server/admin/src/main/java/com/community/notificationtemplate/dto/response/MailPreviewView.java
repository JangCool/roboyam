package com.community.notificationtemplate.dto.response;

/** 견본 값을 채우고 메일 템플릿을 씌운 결과 — {@code subject} 는 {@code [사이트 이름] 제목}, {@code html} 은 문서 전체(화면은 iframe srcdoc 로만 보인다) */
public record MailPreviewView(String subject, String html) {
}
