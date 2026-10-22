package com.community.board.dto.request;

/** 저장할 고유 말머리 — id 가 null 이면 새로 만든다. 목록 순서가 표시 순서이고 빠진 기존 말머리는 지운다(쓰는 글이 있으면 409 PREFIX_IN_USE) */
public record BoardPrefixSaveRequest(String id, String name, Boolean staffOnly, String color, Boolean active) {
}
