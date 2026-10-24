package com.community.post.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 상태 탭 건수 — 상태 조건만 뺀 같은 거르기의 건수({@code public} 은 자바 예약어라 칸 이름만 바꿔 싣는다) */
public record AdminPostCountsView(long all, @JsonProperty("public") long publicCount, long hidden, long deleted) {
}
