package com.community.home.dto.response;

/**
 * 배치 편집기가 보는 가젯 한 개 — admin-web {@code HomeLayoutGadget}. 내용 설정은 관리자가 고치는 종류만 싣는다(지금은 공구 가젯의 개수·정렬, 다른 종류는 null).
 *
 * @param id      저장된 가젯은 Snowflake 글자, 테마 기본 배치의 가젯은 {@code seed-<n>}
 * @param desktop 데스크톱(lg 이상)에서 보이는가
 * @param config  공구 가젯 설정(저장한 값, 없으면 처음 값 — 5개 · 마감 임박 순), 다른 종류는 null
 */
public record HomeLayoutGadgetView(String id, String type, String title, boolean desktop, boolean mobile, Config config) {

    /** @param sort {@code DEADLINE} · {@code NEWEST} · {@code POPULAR} */
    public record Config(int count, String sort) {
    }
}
