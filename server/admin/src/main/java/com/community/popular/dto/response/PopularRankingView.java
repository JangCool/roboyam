package com.community.popular.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/admin/popular-posts?period=} — 그 기간의 지금 순위와 뺀 글.
 *
 * @param from             기간의 시작(포함) — 한국시간 달력의 경계를 UTC 로(설정의 주 시작 요일)
 * @param to               기간의 끝(미포함)
 * @param weekStart        설정의 주 시작 요일 MONDAY · SUNDAY
 * @param computedAt       그 기간의 순위를 마지막으로 계산한 시각(아직 없으면 null)
 * @param recomputePending 다시 계산 요청이 대기·실행 중이다(배치 popular-rank 의 끝나지 않은 요청) — 화면이 「다시 계산하는 중」을 보이고 잠시 뒤 다시 읽는다
 * @param items            순위(공개 인기글과 같은 차례·같은 글 + 계산 뒤에 뺀 글은 {@code excluded true} 로 자리에 남는다)
 * @param excluded         지금 빠져 있는 글 — 최근에 뺀 것부터 최대 200개
 * @param reasons          뺀 사유 코드(화면 차례)
 */
public record PopularRankingView(String period, Instant from, Instant to, String weekStart, Instant computedAt, boolean recomputePending,
                                 List<PopularRankItemView> items, List<PopularExcludedView> excluded, List<String> reasons) {
}
