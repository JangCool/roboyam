package com.community.home.dto;

import tools.jackson.databind.JsonNode;

/**
 * 가젯 한 개의 저장 값(내용 데이터 없음) — 저장된 가젯과 테마 기본 배치의 가젯이 같은 모양이다. 공개 홈은 이것에 종류별 {@code data} 를 붙인다.
 *
 * @param id     가젯 ID — 저장된 것은 Snowflake 글자, 테마 기본 배치의 것은 {@code seed-<n>}
 * @param type   종류(8종 — {@code HomeGadgetKinds})
 * @param style  공통 꾸미기(user-web {@code GadgetStyle})
 * @param config 종류별 설정(게시판·개수·정렬 … — 공개 홈이 데이터를 고르는 데 쓰고 화면에도 그대로 간다)
 * @param more   「더보기」 {@code {label, to}} 또는 null
 */
public record HomeGadgetSpec(String id, String type, String title, boolean desktop, boolean mobile, JsonNode style, JsonNode config, JsonNode more) {
}
