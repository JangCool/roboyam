package com.community.home.dto.response;

import tools.jackson.databind.JsonNode;

/**
 * 공개 홈 가젯 한 개 — user-web {@code HomeGadget}. {@code style}·{@code config} 는 저장된 그대로, {@code data} 는 종류별 모양({@code dto.response.gadget}).
 *
 * @param empty  내용이 없는가(서버 판정)
 * @param locked 로그인한 사람만 보는 가젯의 잠긴 뼈대인가 — 지금은 보는 사람 설정이 없어 늘 false
 * @param more   「더보기」 {@code {label, to}} 또는 null
 */
public record HomeGadgetView(String id, String type, String title, boolean desktop, boolean mobile, boolean empty, boolean locked, JsonNode style,
                             JsonNode config, JsonNode more, Object data) {
}
