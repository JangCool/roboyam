package com.community.board.dto.request;

/**
 * 저장할 고유 말머리 — id 가 null 이면 새로 만든다. 목록 순서가 표시 순서이고 빠진 기존 말머리는 지운다(쓰는 글이 있으면 409 PREFIX_IN_USE).
 *
 * @param adultOnly 성인 전용(빠지면 false) — 게시판 {@code allowAdult} 가 켜져 있어야 한다(아니면 {@code fields.prefixes[i].adultOnly}). 새로 켜면 그 말머리의 기존 글도 19금이 된다
 */
public record BoardPrefixSaveRequest(String id, String name, Boolean staffOnly, String color, Boolean active, Boolean adultOnly) {
}
