package com.community.pointgame.dto.response;

import java.time.Instant;

/**
 * {@code GET·PUT /api/admin/point-games/settings} — 로또 번호 뽑기의 「당첨 번호 확인」 링크(point-games-contract 「당첨 확인 링크」).
 *
 * @param defaultProviderName 처음 값(동행복권) — 「기본값」 안내
 * @param defaultCheckUrl     처음 값({@code https://dhlottery.co.kr})
 * @param updatedAt           마지막 저장 시각(저장한 적 없으면 null)
 * @param updatedBy           마지막으로 저장한 운영자 이름(없거나 지워졌으면 null)
 */
public record PointGameSettingsView(String lotteryProviderName, String lotteryCheckUrl, String defaultProviderName, String defaultCheckUrl, Instant updatedAt,
                                    String updatedBy) {
}
