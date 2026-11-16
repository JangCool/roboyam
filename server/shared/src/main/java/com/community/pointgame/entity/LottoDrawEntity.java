package com.community.pointgame.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;

/**
 * 뽑은 로또 번호 한 세트 — {@code point_game_lotto_draws}(V234, point-games-contract 「내 번호」). 작은 수부터 6개({@code n1 < … < n6}, 1~45 — DB CHECK).
 * 한 번에 뽑은 세트들은 같은 {@code batchId}(적립 포인트 사용 원장 줄 {@code LOTTO_DRAW} 의 대상)를 갖는다. 고치지 않고, 회원이 지우면 행을 지운다(쓴 포인트는 돌려주지 않는다).
 */
@Entity
@Table(name = "point_game_lotto_draws")
public class LottoDrawEntity {

    @Id
    private Long id;

    private Long userId;
    private Long batchId;
    @Enumerated(EnumType.STRING)
    private LottoPickMode mode;
    private short n1;
    private short n2;
    private short n3;
    private short n4;
    private short n5;
    private short n6;
    private Instant createdAt;

    protected LottoDrawEntity() {
    }

    /** @param numbers 작은 수부터 서로 다른 6개(1~45 — 부르는 쪽이 만든다, DB CHECK 가 끝까지 막는다) */
    public static LottoDrawEntity create(long id, long userId, long batchId, LottoPickMode mode, List<Integer> numbers, Instant now) {
        if (numbers.size() != 6) {
            throw new IllegalArgumentException("번호는 6개여야 한다");
        }
        LottoDrawEntity e = new LottoDrawEntity();
        e.id = id;
        e.userId = userId;
        e.batchId = batchId;
        e.mode = mode;
        e.n1 = numbers.get(0).shortValue();
        e.n2 = numbers.get(1).shortValue();
        e.n3 = numbers.get(2).shortValue();
        e.n4 = numbers.get(3).shortValue();
        e.n5 = numbers.get(4).shortValue();
        e.n6 = numbers.get(5).shortValue();
        e.createdAt = now;
        return e;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getBatchId() {
        return batchId;
    }

    public LottoPickMode getMode() {
        return mode;
    }

    /** 작은 수부터 6개 */
    public List<Integer> getNumbers() {
        return List.of((int) n1, (int) n2, (int) n3, (int) n4, (int) n5, (int) n6);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
