package com.community.poll.dto;

import java.time.Instant;

/**
 * 글의 투표 한 줄(V81 {@code polls}) — {@link com.community.poll.repository.master.PollQueries} 가 읽은 값.
 *
 * @param closesAt 마감 시각(null = 마감 없음)
 */
public record PollData(long id, long postId, String question, boolean multiple, boolean changeAllowed, Instant closesAt, Instant createdAt) {

    /** 마감됐는가 — 시각이 지나면 그 자리에서 마감(배치 없음) */
    public boolean closedAt(Instant now) {
        return closesAt != null && !closesAt.isAfter(now);
    }
}
