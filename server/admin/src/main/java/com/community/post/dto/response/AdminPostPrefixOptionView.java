package com.community.post.dto.response;

/** 게시판 고르기의 말머리(켜진 것만) — {@code common} 은 공통 말머리(질문·후기·잡담·공지) */
public record AdminPostPrefixOptionView(String id, String name, boolean staffOnly, boolean common) {
}
