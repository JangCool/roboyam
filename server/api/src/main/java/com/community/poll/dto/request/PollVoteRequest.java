package com.community.poll.dto.request;

import java.util.List;

/** {@code POST /api/posts/{id}/poll/votes} — 고른 선택지 ID(문자열). 단일 선택이면 정확히 1개, 복수 선택이면 1개 이상 */
public record PollVoteRequest(List<String> optionIds) {
}
