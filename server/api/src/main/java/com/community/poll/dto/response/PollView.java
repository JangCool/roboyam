package com.community.poll.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 글 상세의 {@code poll} · {@code GET /api/posts/{id}/poll} · 투표 응답 — docs/features/post-poll-contract.md. <b>익명</b>: 누가 무엇을 골랐는지는 없다(내 선택과 집계뿐).
 *
 * @param closesAt          마감 시각(없으면 null — 마감 없음)
 * @param closed            마감됐는가(시각이 지났다)
 * @param voted             내가 투표했는가
 * @param myOptionIds       내가 고른 선택지(투표 전·비회원은 빈 배열)
 * @param canVote           지금 투표 단추를 누를 수 있는가 — 로그인·이메일 인증·정지 아님·마감 전이고 (아직 투표 안 함 또는 {@code changeAllowed})
 * @param voteBlockedReason 누를 수 없는 까닭 CLOSED · LOGIN_REQUIRED · EMAIL_NOT_VERIFIED · ACCOUNT_RESTRICTED — 이미 투표했고 다시 투표할 수 없으면 null({@code voted} 로 안다)
 * @param resultsVisible    결과를 볼 수 있는가(투표했거나 마감됐다). false 면 {@code voterCount}·{@code results} 가 null
 * @param voterCount        참여한 사람 수(복수 선택이어도 한 사람은 한 번)
 * @param results           선택지 차례대로의 득표 수
 * @param editable          글쓴이이고 첫 표 전 — 고치기 화면의 투표 칸·[투표 지우기]
 */
public record PollView(String id, String question, boolean multiple, boolean changeAllowed, Instant closesAt, boolean closed, List<PollOptionView> options,
                       boolean voted, List<String> myOptionIds, boolean canVote, String voteBlockedReason, boolean resultsVisible, Integer voterCount,
                       List<PollResultView> results, boolean editable) {
}
