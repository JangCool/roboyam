package com.community.pointgame.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 룰렛 한 번 — {@code point_game_roulette_spins}(V234, docs/features/point-games-contract.md). 돌리기 트랜잭션이 적립 포인트 차감({@code ROULETTE_SPIN})·추첨·당첨 지급
 * ({@code ROULETTE_WIN}) 뒤에 넣고 고치지 않는다. 원장 두 줄의 대상이 이 ID 다. 하루 횟수(한국 시간)는 회원 잔액 행 잠금 아래에서 이 표를 센다.
 */
@Entity
@Table(name = "point_game_roulette_spins")
public class RouletteSpinEntity {

    @Id
    private Long id;

    private Long userId;
    /** 맞힌 상품 키({@code MISS}·{@code P10} …) */
    private String prizeKey;
    /** 쓴 적립 포인트 */
    private int cost;
    /** 받은 적립 포인트(꽝 0) */
    private int won;
    private Instant createdAt;

    protected RouletteSpinEntity() {
    }

    public static RouletteSpinEntity create(long id, long userId, String prizeKey, int cost, int won, Instant now) {
        RouletteSpinEntity e = new RouletteSpinEntity();
        e.id = id;
        e.userId = userId;
        e.prizeKey = prizeKey;
        e.cost = cost;
        e.won = won;
        e.createdAt = now;
        return e;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getPrizeKey() {
        return prizeKey;
    }

    public int getCost() {
        return cost;
    }

    public int getWon() {
        return won;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
