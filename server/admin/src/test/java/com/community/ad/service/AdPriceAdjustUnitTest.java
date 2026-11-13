package com.community.ad.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 여러 상품 단가 조정 계산(ad-price-rules-contract 규칙 5) — 비율·정액, 10원 단위 버림, 0원 아래는 null(400), 정수 계산 */
class AdPriceAdjustUnitTest {

    @Test
    void 비율은_기준_곱하기_100_더하기_비율_나누기_100_후_10원_버림() {
        assertThat(AdPriceRuleAdminService.adjusted(50_000, "PERCENT", -15)).isEqualTo(42_500);
        assertThat(AdPriceRuleAdminService.adjusted(33_333, "PERCENT", 10)).isEqualTo(36_660);  // 36,666.3 → 36,660
        assertThat(AdPriceRuleAdminService.adjusted(12_345, "PERCENT", -90)).isEqualTo(1_230);  // 1,234.5 → 1,230
        assertThat(AdPriceRuleAdminService.adjusted(9, "PERCENT", 300)).isEqualTo(30);          // 36 → 30
        assertThat(AdPriceRuleAdminService.adjusted(0, "PERCENT", 300)).isZero();
    }

    @Test
    void 정액은_더한_뒤_10원_버림_0원_아래면_null() {
        assertThat(AdPriceRuleAdminService.adjusted(5_000, "AMOUNT", 1_234)).isEqualTo(6_230);
        assertThat(AdPriceRuleAdminService.adjusted(5_000, "AMOUNT", -4_995)).isZero();          // 5 → 0
        assertThat(AdPriceRuleAdminService.adjusted(5_000, "AMOUNT", -5_000)).isZero();
        assertThat(AdPriceRuleAdminService.adjusted(5_000, "AMOUNT", -5_001)).isNull();
    }
}
