package com.community.pointgame.dto.response;

/**
 * {@code POST /api/me/point-games/roulette/spins} — 결과는 서버가 정한다(화면은 그 칸에 판을 멈출 뿐).
 *
 * @param spinId      돌린 기록 ID
 * @param prizeKey    맞힌 상품의 {@code key}
 * @param won         받은 적립 포인트(꽝 0)
 * @param freeBalance 차감·지급 뒤 적립 잔액
 * @param usedToday   이번을 포함해 오늘 돌린 횟수
 */
public record RouletteSpinResultView(String spinId, String prizeKey, int won, long freeBalance, int usedToday) {
}
