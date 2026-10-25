package com.community.popular.dto.response;

/**
 * 숫자 칸 하나의 범위.
 *
 * @param decimals 소수점 아래 자릿수 한도 — 0 이면 정수만
 */
public record PopularLimitView(double min, double max, int decimals) {
}
