package com.community.home.dto;

/**
 * 홈 격자에서 가젯 하나의 자리(칸 단위) — 공유 레이어 {@code @roboyam/home-layout} 의 {@code HomeLayoutItem} 과 같은 모양.
 *
 * @param gadgetId 가젯 ID(문자열)
 * @param x        왼쪽 칸(0부터)
 * @param y        위쪽 행(0부터, 행 높이 15px · 간격 16px)
 * @param w        폭(칸 수)
 * @param h        최소 높이(행 수)
 */
public record HomeLayoutItem(String gadgetId, int x, int y, int w, int h) {
}
