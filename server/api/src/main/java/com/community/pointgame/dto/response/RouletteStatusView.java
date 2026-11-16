package com.community.pointgame.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/me/point-games/roulette} — 값·하루 횟수·상품표(판에 이 차례로 시계 방향)·적립 잔액.
 *
 * @param usedToday   오늘(한국 시간 0시부터) 돌린 횟수
 * @param freeBalance 적립(무상) 포인트 잔액 — 충전 포인트는 쓰지 않아 싣지 않는다
 * @param resetsAt    횟수가 다시 차는 시각(다음 한국 자정)
 */
public record RouletteStatusView(int cost, int dailyLimit, int usedToday, List<RoulettePrizeView> prizes, long freeBalance, Instant resetsAt) {
}
