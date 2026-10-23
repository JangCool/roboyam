package com.community.mfa.totp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * TOTP(RFC 6238, HMAC-SHA1, 6자리, 30초, ±1칸) — commerce-foundation-contract 「0-3」 데이터·보안 규칙 「TOTP」.
 */
class TotpTest {

    /** RFC 6238 부록 B 의 SHA1 시험 비밀키(ASCII "12345678901234567890") */
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Nested
    class RFC_6238_부록_B_시험_벡터 {

        @ParameterizedTest(name = "T={0}초 → {1}")
        @CsvSource({
                "59, 94287082",
                "1111111109, 07081804",
                "1111111111, 14050471",
                "1234567890, 89005924",
                "2000000000, 69279037",
                "20000000000, 65353130"
        })
        void SHA1_8자리_벡터와_같다(long epochSeconds, String expected) {
            long step = Totp.step(Instant.ofEpochSecond(epochSeconds));

            assertThat(Totp.code(RFC_SECRET, step, 8)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "T={0}초 → 6자리 {1}")
        @CsvSource({
                "59, 287082",
                "1111111109, 081804",
                "1111111111, 050471",
                "1234567890, 005924",
                "2000000000, 279037",
                "20000000000, 353130"
        })
        void 우리가_쓰는_6자리는_8자리_벡터의_뒤_6자리다(long epochSeconds, String expected) {
            assertThat(Totp.code(RFC_SECRET, Totp.step(Instant.ofEpochSecond(epochSeconds)))).isEqualTo(expected).hasSize(6);
        }

        @Test
        void 시간_칸은_30초_단위_내림이다() {
            assertThat(Totp.step(Instant.ofEpochSecond(59))).isEqualTo(1);
            assertThat(Totp.step(Instant.ofEpochSecond(60))).isEqualTo(2);
            assertThat(Totp.step(Instant.ofEpochSecond(1111111109))).isEqualTo(0x23523EC);
            assertThat(Totp.PERIOD_SECONDS).isEqualTo(30);
            assertThat(Totp.DIGITS).isEqualTo(6);
        }
    }

    @Nested
    class 허용_범위 {

        private final Instant now = Instant.ofEpochSecond(1_800_000_015L);
        private final long current = Totp.step(now);

        @Test
        void 지금_칸과_앞뒤_1칸은_받아들인다() {
            for (long offset = -1; offset <= 1; offset++) {
                Totp.Match match = Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, current + offset), now, null);

                assertThat(match.result()).as("칸 %d", offset).isEqualTo(Totp.Result.ACCEPTED);
                assertThat(match.step()).isEqualTo(current + offset);
            }
        }

        @Test
        void 두_칸_이상_어긋나면_거절한다() {
            assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, current - 2), now, null).result()).isEqualTo(Totp.Result.INVALID);
            assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, current + 2), now, null).result()).isEqualTo(Totp.Result.INVALID);
        }

        @Test
        void 다른_비밀키의_코드나_형식이_아닌_값은_틀림이다() {
            byte[] other = "abcdefghijabcdefghij".getBytes(StandardCharsets.US_ASCII);

            assertThat(Totp.verify(RFC_SECRET, Totp.code(other, current), now, null).result()).isEqualTo(Totp.Result.INVALID);
            assertThat(Totp.verify(RFC_SECRET, null, now, null).result()).isEqualTo(Totp.Result.INVALID);
            assertThat(Totp.verify(RFC_SECRET, "12345", now, null).result()).isEqualTo(Totp.Result.INVALID);
        }
    }

    @Nested
    class 재사용_방지 {

        private final Instant now = Instant.ofEpochSecond(1_800_000_015L);
        private final long current = Totp.step(now);

        @Test
        void 받아들인_칸의_같은_코드는_다시_쓰지_못한다() {
            String code = Totp.code(RFC_SECRET, current);
            Totp.Match first = Totp.verify(RFC_SECRET, code, now, null);

            Totp.Match again = Totp.verify(RFC_SECRET, code, now, first.step());

            assertThat(first.result()).isEqualTo(Totp.Result.ACCEPTED);
            assertThat(again.result()).isEqualTo(Totp.Result.REUSED);
        }

        @Test
        void 받아들인_칸보다_이른_칸의_코드도_거절한다() {
            assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, current - 1), now, current).result()).isEqualTo(Totp.Result.REUSED);
        }

        @Test
        void 받아들인_칸보다_늦은_칸은_받아들인다() {
            Totp.Match next = Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, current + 1), now, current);

            assertThat(next.result()).isEqualTo(Totp.Result.ACCEPTED);
            assertThat(next.step()).isEqualTo(current + 1);
        }
    }
}
