package com.community.post.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 관리자 글 상세의 투표({@code GET /api/admin/posts/{id}} 의 {@code poll}) — <b>집계만</b>: 마감 전에도 참여 수·선택지별 득표 수를 보이고, 누가 무엇을 골랐는지는 없다.
 * docs/features/post-poll-contract.md.
 *
 * @param closesAt   마감 시각(없으면 null — 마감 없음)
 * @param closed     마감됐는가(시각이 지났다)
 * @param voterCount 참여한 사람 수(복수 선택이어도 한 사람은 한 번)
 * @param options    선택지 차례대로 — 득표 수 포함
 */
public record AdminPollView(String id, String question, boolean multiple, boolean changeAllowed, Instant closesAt, boolean closed, int voterCount,
                            List<AdminPollOptionView> options, Instant createdAt) {
}
