package com.community.home.dto.response;

import tools.jackson.databind.JsonNode;

/**
 * 공개 가젯 한 개 — user-web {@code Gadget}. {@code style}·{@code config} 는 저장된 그대로, {@code data} 는 종류별 모양({@code dto.response.gadget}).
 *
 * @param empty  내용이 없는가(서버 판정)
 * @param locked 로그인한 사람만 보는 가젯의 잠긴 뼈대인가 — 지금은 보는 사람 설정이 없어 늘 false
 * @param more   「더보기」 {@code {label, to}} 또는 null
 * @param hidden 이 사람에게 그리지 않는 가젯인가 — 지금은 19금 전용 가젯({@code ADULT_POSTS})을 성인이 아닌 사람이 볼 때만 true(그때 {@code data} null · {@code empty} true).
 *               화면은 자리째 빼고 그린다(adult-board-contract)
 */
public record GadgetView(String id, String type, String title, boolean desktop, boolean mobile, boolean empty, boolean locked, JsonNode style,
                             JsonNode config, JsonNode more, Object data, boolean hidden) {
}
