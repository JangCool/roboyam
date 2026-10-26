package com.community.zconfiguration.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** 커머스의 날짜 기준은 한국 시간(commerce.md 3절) — 만 나이·재인증 가능일이 이 시간대로 정해진다 */
class ServiceTimeTest {

    @Test
    void 서비스_날짜는_UTC_가_아니라_한국_시간이다() {
        assertThat(ServiceTime.ZONE.getId()).isEqualTo("Asia/Seoul");
        assertThat(ServiceTime.today(Clock.fixed(Instant.parse("2026-10-24T14:59:59Z"), ZoneOffset.UTC))).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(ServiceTime.today(Clock.fixed(Instant.parse("2026-10-24T15:00:00Z"), ZoneOffset.UTC))).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(ServiceTime.dateOf(Instant.parse("2026-12-31T15:00:00Z"))).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void 다시_할_수_있는_날은_그날_언제_해도_되는_첫_날이다() {
        // 한국 시간 0시 정각에 풀리면 그날, 아니면 다음 날
        assertThat(ServiceTime.firstFullDateAfter(Instant.parse("2026-11-22T15:00:00Z"))).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(ServiceTime.firstFullDateAfter(Instant.parse("2026-11-22T15:00:00.000001Z"))).isEqualTo(LocalDate.of(2026, 11, 24));
        assertThat(ServiceTime.firstFullDateAfter(Instant.parse("2026-11-23T01:00:00Z"))).isEqualTo(LocalDate.of(2026, 11, 24));
    }
}
