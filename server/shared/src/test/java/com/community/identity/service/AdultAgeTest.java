package com.community.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 만 19세 판정(commerce.md 12-1, 계약 「0-4」) — 19번째 생일부터 성인, 2월 29일생은 평년에 3월 1일(민법 기간 계산) */
class AdultAgeTest {

    @Test
    void 열아홉_번째_생일부터_성인이다() {
        LocalDate birth = LocalDate.of(2007, 10, 25);
        assertThat(AdultAge.adultFrom(birth)).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(AdultAge.isAdult(birth, LocalDate.of(2026, 10, 24))).isFalse();
        assertThat(AdultAge.isAdult(birth, LocalDate.of(2026, 10, 25))).isTrue();
        assertThat(AdultAge.isAdult(birth, LocalDate.of(2030, 1, 1))).isTrue();
    }

    @Test
    void 이월_29일생은_19년_뒤_평년의_3월_1일부터_성인이다() {
        // 윤년 + 19 는 늘 평년이다 — LocalDate#plusYears 는 2월 28일로 내리므로 하루 이르다
        for (int year : new int[] {1996, 2004, 2008, 2012, 2016}) {
            LocalDate birth = LocalDate.of(year, 2, 29);
            assertThat(AdultAge.adultFrom(birth)).isEqualTo(LocalDate.of(year + 19, 3, 1));
            assertThat(birth.plusYears(19)).isEqualTo(LocalDate.of(year + 19, 2, 28));
        }
        assertThat(AdultAge.isAdult(LocalDate.of(2008, 2, 29), LocalDate.of(2027, 2, 28))).isFalse();
        assertThat(AdultAge.isAdult(LocalDate.of(2008, 2, 29), LocalDate.of(2027, 3, 1))).isTrue();
    }

    @Test
    void 이월_28일생과_3월_1일생은_그날_그대로다() {
        assertThat(AdultAge.adultFrom(LocalDate.of(2009, 2, 28))).isEqualTo(LocalDate.of(2028, 2, 28));
        assertThat(AdultAge.adultFrom(LocalDate.of(1997, 3, 1))).isEqualTo(LocalDate.of(2016, 3, 1));
    }
}
