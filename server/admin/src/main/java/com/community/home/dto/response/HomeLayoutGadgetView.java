package com.community.home.dto.response;

import tools.jackson.databind.JsonNode;

/**
 * 배치 편집기가 보는 가젯 한 개 — admin-web {@code HomeLayoutGadget}. 내용 설정은 ⚙ 설정이 있는 종류의 ⚙ 칸만 싣는다({@code GadgetConfigs.view}, 다른 종류는 null).
 *
 * @param id      저장된 가젯은 Snowflake 글자, 시드 가젯(테마 기본 배치 · 페이지 처음 배치)은 {@code seed-<n>}
 * @param desktop 데스크톱(lg 이상)에서 보이는가
 * @param config  공구 {@code {count, sort}} · 글 목록 {@code {count, sort, boardScope, boardIds, periodTabs}} · 갤러리 {@code {count, boardScope, boardIds}} ·
 *                게시판 카드 {@code {perBoard, boardScope, boardIds}} — 저장 값, 없으면 처음 값. 다른 종류는 null
 */
public record HomeLayoutGadgetView(String id, String type, String title, boolean desktop, boolean mobile, JsonNode config) {
}
