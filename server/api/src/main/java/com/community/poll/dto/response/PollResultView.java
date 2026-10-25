package com.community.poll.dto.response;

/** 선택지 하나의 득표 수(표가 없으면 0) */
public record PollResultView(String optionId, int votes) {
}
