package com.community.pointgame.entity;

/**
 * 로또 번호 뽑는 방법(point-games-contract 「뽑는 방법」) — {@code point_game_lotto_draws.mode} CHECK 와 같은 값.
 * 자동 = 6개 모두 무작위(고정·제외 없음) · 반자동 = 고정 번호 1~5개 + 나머지 무작위(제외 번호는 선택).
 */
public enum LottoPickMode {
    AUTO,
    SEMI_AUTO
}
