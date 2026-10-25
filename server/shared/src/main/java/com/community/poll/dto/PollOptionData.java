package com.community.poll.dto;

/** 투표 선택지 한 줄(V81 {@code poll_options}) — {@code position} 0부터 차례 */
public record PollOptionData(long id, int position, String label) {
}
