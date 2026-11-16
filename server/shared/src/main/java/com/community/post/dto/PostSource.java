package com.community.post.dto;

/**
 * 글 출처 한 줄(V232 {@code posts.sources} 배열의 원소 — {@code {"name":"전자신문","url":"https://…"}}). 글쓰기·고치기 요청과 글 상세 응답이 같은 모양을 쓴다.
 * 정리·검사 규칙은 {@link com.community.post.service.PostSources}.
 *
 * @param name 출처 이름(앞뒤 공백 정리 · 최대 40자), 없으면 null — 화면은 주소의 도메인을 보인다
 * @param url  원문 주소(http·https 만 · 최대 500자)
 */
public record PostSource(String name, String url) {
}
