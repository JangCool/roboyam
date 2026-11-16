package com.community.pointgame.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 뽑은 번호 한 세트.
 *
 * @param numbers 작은 수부터 6개(1~45)
 * @param mode    {@code AUTO}·{@code SEMI_AUTO}
 */
public record LottoDrawView(String id, List<Integer> numbers, String mode, Instant drawnAt) {
}
