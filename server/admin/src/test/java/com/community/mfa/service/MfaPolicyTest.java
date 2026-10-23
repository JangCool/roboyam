package com.community.mfa.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.mfa.dto.MfaDecision;
import com.community.mfa.dto.MfaLoginMethod;
import com.community.mfa.dto.MfaSettings;
import com.community.mfa.dto.MfaStage;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 판정 규칙 — 상태 판정 표 5줄, 유예 마감 = max(켠 시각, 계정 생성 시각) + 지금 유예 일수(결정 8 · 화면 확정 결정 1), 기기 신뢰 일수 = 개월 × 30.
 */
class MfaPolicyTest {

    private static final Instant ENABLED_AT = Instant.parse("2026-10-01T00:00:00Z");

    private static MfaSettings on(int graceDays) {
        return new MfaSettings(true, graceDays, 1, 5, ENABLED_AT, 1L);
    }

    private static MfaSettings off() {
        return new MfaSettings(false, 14, 1, 5, ENABLED_AT, 1L);
    }

    @Nested
    class 유예_마감 {

        @Test
        void 켜기_전에_만든_계정은_켠_시각부터_센다() {
            Instant created = ENABLED_AT.minus(Duration.ofDays(100));

            assertThat(MfaPolicy.graceEndsAt(on(14), created)).isEqualTo(ENABLED_AT.plus(Duration.ofDays(14)));
        }

        @Test
        void 켠_뒤에_만든_계정은_계정을_만든_시각부터_센다() {
            Instant created = ENABLED_AT.plus(Duration.ofDays(20));

            assertThat(MfaPolicy.graceEndsAt(on(14), created)).isEqualTo(created.plus(Duration.ofDays(14)));
        }

        @Test
        void 유예_일수를_바꾸면_저장_없이_바로_반영된다() {
            Instant created = ENABLED_AT.minus(Duration.ofDays(1));

            assertThat(MfaPolicy.graceEndsAt(on(30), created)).isEqualTo(ENABLED_AT.plus(Duration.ofDays(30)));
            assertThat(MfaPolicy.graceEndsAt(on(1), created)).isEqualTo(ENABLED_AT.plus(Duration.ofDays(1)));
        }

        @Test
        void 꺼져_있으면_유예가_없다() {
            assertThat(MfaPolicy.graceEndsAt(off(), ENABLED_AT)).isNull();
            assertThat(MfaPolicy.graceExpired(off(), ENABLED_AT, ENABLED_AT.plus(Duration.ofDays(400)))).isFalse();
        }

        @Test
        void 마감_직전까지는_유예_안이고_마감_시각부터_지남이다() {
            Instant end = ENABLED_AT.plus(Duration.ofDays(14));

            assertThat(MfaPolicy.graceExpired(on(14), ENABLED_AT, end.minusMillis(1))).isFalse();
            assertThat(MfaPolicy.graceExpired(on(14), ENABLED_AT, end)).isTrue();
            assertThat(MfaPolicy.decide(on(14), false, false, ENABLED_AT, end.minusMillis(1)).stage()).isEqualTo(MfaStage.GRACE);
            assertThat(MfaPolicy.decide(on(14), false, false, ENABLED_AT, end).stage()).isEqualTo(MfaStage.ENROLL);
        }
    }

    @Nested
    class 상태_판정_표 {

        private final Instant now = ENABLED_AT.plus(Duration.ofDays(3));

        @Test
        void 사이트_꺼짐이면_등록_여부와_무관하게_바로_로그인() {
            for (boolean enrolled : new boolean[] {true, false}) {
                MfaDecision decision = MfaPolicy.decide(off(), enrolled, false, ENABLED_AT, now);

                assertThat(decision.isPending()).isFalse();
                assertThat(decision.method()).isEqualTo(MfaLoginMethod.OFF);
                assertThat(decision.stage()).isNull();
            }
        }

        @Test
        void 켜짐_등록됨_유효한_신뢰_기기면_바로_로그인() {
            MfaDecision decision = MfaPolicy.decide(on(14), true, true, ENABLED_AT, now);

            assertThat(decision.isPending()).isFalse();
            assertThat(decision.method()).isEqualTo(MfaLoginMethod.TRUSTED_DEVICE);
            assertThat(decision.stage()).isNull();
        }

        @Test
        void 켜짐_등록됨이면_VERIFY_대기() {
            MfaDecision decision = MfaPolicy.decide(on(14), true, false, ENABLED_AT, now);

            assertThat(decision.isPending()).isTrue();
            assertThat(decision.stage()).isEqualTo(MfaStage.VERIFY);
        }

        @Test
        void 켜짐_미등록_유예_안이면_로그인하고_GRACE_안내() {
            MfaDecision decision = MfaPolicy.decide(on(14), false, false, ENABLED_AT, now);

            assertThat(decision.isPending()).isFalse();
            assertThat(decision.method()).isEqualTo(MfaLoginMethod.GRACE);
            assertThat(decision.stage()).isEqualTo(MfaStage.GRACE);
            assertThat(decision.graceEndsAt()).isEqualTo(ENABLED_AT.plus(Duration.ofDays(14)));
        }

        @Test
        void 켜짐_미등록_유예_지남이면_ENROLL_대기() {
            MfaDecision decision = MfaPolicy.decide(on(2), false, false, ENABLED_AT, now);

            assertThat(decision.isPending()).isTrue();
            assertThat(decision.stage()).isEqualTo(MfaStage.ENROLL);
        }

        @Test
        void 신뢰_기기는_미등록이면_소용없다() {
            assertThat(MfaPolicy.decide(on(2), false, true, ENABLED_AT, now).stage()).isEqualTo(MfaStage.ENROLL);
        }
    }

    @Test
    void 기기_신뢰_일수는_개월_곱하기_30일이고_기본_1개월은_30일() {
        assertThat(new MfaSettings(true, 14, 1, 5, null, null).trustDays()).isEqualTo(30);
        assertThat(new MfaSettings(true, 14, 12, 5, null, null).trustDays()).isEqualTo(360);
    }
}
