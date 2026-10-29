package com.community.home.dto.response;

import tools.jackson.databind.JsonNode;

/**
 * 배치 편집기가 보는 가젯 한 개 — admin-web {@code HomeLayoutGadget}.
 *
 * @param id      저장된 가젯은 Snowflake 글자, 시드 가젯(테마 기본 배치 · 페이지 처음 배치 · 게시판 레이아웃 기본 본문)은 {@code seed-<n>}
 * @param desktop 데스크톱(lg 이상)에서 보이는가
 * @param style   꾸미기 칸 전부(저장 값, 없으면 테마 기본 꾸미기 — {@code GadgetStyles.view}). 게시판 본문은 null
 * @param config  내용 설정 — 그 종류의 표 칸 전부(저장 값, 없으면 처음 값 — {@code GadgetConfigs.view}). 게시판 본문은 null
 */
public record HomeLayoutGadgetView(String id, String type, String title, boolean desktop, boolean mobile, JsonNode style, JsonNode config) {
}
