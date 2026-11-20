package com.community.jjal.dto.response;

/** 올리기·담기 결과(user-web {@code JjalSaveResult}) — 넣은 줄(맨 앞)과 바뀐 개수·상한 */
public record JjalSaveResultView(MyJjalItemView item, int count, int limit) {
}
