package com.community.pointgame.dto.response;

/**
 * 룰렛 상품 하나 — 꽝은 {@code points} 0.
 *
 * @param key      상품 식별자({@code MISS}·{@code P10} …)
 * @param chanceBp 확률(만분율 — 다 더하면 10,000). 판의 칸 크기
 */
public record RoulettePrizeView(String key, int points, int chanceBp) {
}
