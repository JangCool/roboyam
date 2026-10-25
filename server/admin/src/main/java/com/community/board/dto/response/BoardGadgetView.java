package com.community.board.dto.response;

/**
 * 「가젯 노출 금지」에 고를 수 있는 가젯 하나 — {@code key} 는 {@link com.community.board.entity.BoardGadget} 이름(저장 요청 {@code gadgetExclusions} 에 보내는 값),
 * {@code label} 은 요청 언어의 이름(번들 {@code board.gadget.<key>}).
 */
public record BoardGadgetView(String key, String label) {
}
