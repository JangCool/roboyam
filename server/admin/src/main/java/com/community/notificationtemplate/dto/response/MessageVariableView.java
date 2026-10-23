package com.community.notificationtemplate.dto.response;

/**
 * 대상·채널 하나가 쓸 수 있는 변수 — 코드가 정한다. 설명·견본 값은 요청 언어로 푼다.
 *
 * @param sample   미리보기·시험 발송에 쓰는 견본 값
 * @param required 보안 메일의 필수 변수 — 제목·본문·버튼 링크 어디에도 없으면 저장 거절
 * @param link     링크 변수(이메일 「버튼」의 주소로 고를 수 있는 것은 이것뿐)
 */
public record MessageVariableView(String name, String description, String sample, boolean required, boolean link) {
}
