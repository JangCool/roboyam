package com.community.pointgame.service;

import com.community.pointgame.dto.response.RoulettePrizeView;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * 룰렛 값·하루 횟수·상품표(point-games-contract 「룰렛」 — 지금은 서버 상수, 관리자 설정은 미확정). 확률은 만분율이고 합이 {@value #TOTAL_BP} 다.
 * 꽝 7000 · 10P 1200 · 20P 800 · 30P 500 · 50P 300 · 100P 150 · 500P 50 → 한 번 평균 9.8P(= 50P 의 19.6%). 판에 이 차례로 시계 방향으로 놓인다.
 */
public final class RouletteTable {

    /** 한 번 돌리는 값(적립 포인트) */
    public static final int COST = 50;
    /** 하루(한국 시간 0시 초기화) 최대 횟수 */
    public static final int DAILY_LIMIT = 5;
    public static final int TOTAL_BP = 10_000;

    /** 상품 하나 */
    public record Prize(String key, int points, int chanceBp) {
    }

    public static final List<Prize> PRIZES = List.of(
            new Prize("MISS", 0, 7000),
            new Prize("P10", 10, 1200),
            new Prize("P20", 20, 800),
            new Prize("P30", 30, 500),
            new Prize("P50", 50, 300),
            new Prize("P100", 100, 150),
            new Prize("P500", 500, 50));

    static {
        int sum = PRIZES.stream().mapToInt(Prize::chanceBp).sum();
        if (sum != TOTAL_BP) {
            throw new IllegalStateException("룰렛 확률 합이 " + TOTAL_BP + " 이 아니다: " + sum);
        }
    }

    private RouletteTable() {
    }

    /** 0 이상 {@value #TOTAL_BP} 미만의 굴린 값이 떨어지는 칸 — 표 차례로 확률을 빼 나가 처음 음수가 되는 칸 */
    public static Prize at(int roll) {
        if (roll < 0 || roll >= TOTAL_BP) {
            throw new IllegalArgumentException("굴린 값이 범위 밖이다: " + roll);
        }
        int left = roll;
        for (Prize prize : PRIZES) {
            left -= prize.chanceBp();
            if (left < 0) {
                return prize;
            }
        }
        throw new IllegalStateException("확률 합이 맞지 않는다");
    }

    /** 굴린다 — 서버 난수(운영은 {@code SecureRandom}) */
    public static Prize pick(RandomGenerator random) {
        return at(random.nextInt(TOTAL_BP));
    }

    public static List<RoulettePrizeView> views() {
        return PRIZES.stream().map(p -> new RoulettePrizeView(p.key(), p.points(), p.chanceBp())).toList();
    }
}
