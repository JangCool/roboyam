package com.community.pointgame.dto.request;

import java.util.List;

/**
 * {@code POST /api/me/point-games/lotto/draws} — 세트 수(1 또는 묶음 크기 5) · 방법({@code AUTO}|{@code SEMI_AUTO}) · 고정 번호 · 제외 번호. 검사 차례가 계약(칸 순서
 * {@code sets → mode → fixed → excluded})이라 어노테이션 없이 받고 서비스가 모은다. 빈 목록은 {@code null} 로 와도 된다.
 */
public record LottoDrawRequest(Integer sets, String mode, List<Integer> fixed, List<Integer> excluded) {
}
