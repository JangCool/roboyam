package com.community.home.dto.response;

/**
 * 배치 편집기가 보는 가젯 한 개(내용 설정은 싣지 않는다) — admin-web {@code HomeLayoutGadget}.
 *
 * @param id      저장된 가젯은 Snowflake 글자, 테마 기본 배치의 가젯은 {@code seed-<n>}
 * @param desktop 데스크톱(lg 이상)에서 보이는가
 */
public record HomeLayoutGadgetView(String id, String type, String title, boolean desktop, boolean mobile) {
}
