package com.community.board.dto.request;

/** 공통 말머리는 켜고 끄기만 보낸다(이름·운영진 전용은 고정) */
public record BoardCommonPrefixSaveRequest(String id, Boolean active) {
}
