package com.community.pointgame.dto.response;

import java.util.List;

/** {@code POST /api/me/point-games/lotto/draws} — 이번에 뽑은 세트(최신이 위 — 목록과 같은 차례)와 새 적립 잔액 */
public record LottoDrawResultView(List<LottoDrawView> draws, long freeBalance) {
}
