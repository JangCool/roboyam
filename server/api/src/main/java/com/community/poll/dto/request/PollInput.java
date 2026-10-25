package com.community.poll.dto.request;

import java.util.List;

/**
 * 글쓰기·고치기 요청의 {@code poll} — docs/features/post-poll-contract.md. 규칙은 {@code PollRules}.
 *
 * @param question      질문(공백 정리 뒤 1~100자)
 * @param options       선택지(빈 칸은 빼고 2~6개, 각 1~50자, 대소문자 무시 서로 다름)
 * @param multiple      복수 선택 — 비우면 끔(하나만 고른다)
 * @param closesAt      마감 시각 — 시간대를 포함한 ISO-8601(예 {@code 2026-10-25T21:00:00+09:00}), 지금+1분 ~ 365일 안. 비우면 마감 없음
 * @param changeAllowed 「다시 투표 허용」 — 비우면 끔(한 번 투표하면 바꿀 수 없다)
 */
public record PollInput(String question, List<String> options, Boolean multiple, String closesAt, Boolean changeAllowed) {
}
