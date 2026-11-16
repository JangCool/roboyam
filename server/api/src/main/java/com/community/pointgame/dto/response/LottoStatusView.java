package com.community.pointgame.dto.response;

import java.util.List;

/**
 * {@code GET /api/me/point-games/lotto} — 값·보관 한도·내 번호(최신이 위)·당첨 확인 링크.
 *
 * @param bundleSize 한 번에 뽑는 묶음 크기(5세트)
 * @param bundleCost 묶음 값
 * @param draws      내 번호 — 최신이 위, {@code storageLimit} 개까지
 * @param checkLink  당첨 확인 링크 — 설정이 규칙에 맞지 않으면 null(링크를 그리지 않는다)
 */
public record LottoStatusView(int setCost, int bundleSize, int bundleCost, long freeBalance, int storageLimit, List<LottoDrawView> draws,
                              LottoCheckLinkView checkLink) {
}
