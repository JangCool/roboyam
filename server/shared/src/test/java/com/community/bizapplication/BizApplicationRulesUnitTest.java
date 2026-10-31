package com.community.bizapplication;

import static com.community.bizapplication.entity.BizApplicationStatus.AWAITING_DEPOSIT;
import static com.community.bizapplication.entity.BizApplicationStatus.AWAITING_PAYMENT;
import static com.community.bizapplication.entity.BizApplicationStatus.CANCELED;
import static com.community.bizapplication.entity.BizApplicationStatus.EXPIRED;
import static com.community.bizapplication.entity.BizApplicationStatus.REJECTED;
import static com.community.bizapplication.entity.BizApplicationStatus.SCHEDULED;
import static com.community.bizapplication.entity.BizApplicationStatus.SUBMITTED;
import static com.community.bizapplication.entity.BizApplicationStatus.TAKEN_DOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizPhase;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.exception.BizApplicationException;
import com.community.bizapplication.repository.master.BizSlotStore.Occupied;
import com.community.bizapplication.service.BizDealLinks;
import com.community.bizapplication.service.BizPeriods;
import com.community.bizapplication.service.BizPricing;
import com.community.bizapplication.service.BizSlotService;
import com.community.bizapplication.service.BizTransitions;
import com.community.bizapplication.service.BizTransitions.Action;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 사업자 신청 건의 계산 규칙(DB 없이) — 상태 전이 표 전체(허용·거부), 자리 판정의 반열림 경계·최대 동시 겹침, 금액·부가세(버림)·일수, 일할 제안(user-web 과 같은 식), 기간 변환, 링크 검사.
 */
class BizApplicationRulesUnitTest {

    @Nested
    class 상태_전이 {

        /** 설계 2절 · ★-3 의 표 — 처리마다 허용되는 (전 → 후) 전부 */
        private final Map<Action, Map<BizApplicationStatus, BizApplicationStatus>> expected = expected();

        private Map<Action, Map<BizApplicationStatus, BizApplicationStatus>> expected() {
            Map<Action, Map<BizApplicationStatus, BizApplicationStatus>> t = new EnumMap<>(Action.class);
            t.put(Action.APPROVE_ONLINE, Map.of(SUBMITTED, AWAITING_PAYMENT));
            t.put(Action.APPROVE_MANUAL, Map.of(SUBMITTED, AWAITING_DEPOSIT));
            t.put(Action.REJECT, Map.of(SUBMITTED, REJECTED));
            t.put(Action.MEMBER_CANCEL, Map.of(SUBMITTED, CANCELED, AWAITING_PAYMENT, CANCELED, AWAITING_DEPOSIT, CANCELED));
            t.put(Action.CREATIVE_CHANGE, Map.of(SUBMITTED, SUBMITTED, AWAITING_PAYMENT, SUBMITTED, AWAITING_DEPOSIT, SUBMITTED));
            t.put(Action.PAY, Map.of(AWAITING_PAYMENT, SCHEDULED));
            t.put(Action.EXPIRE, Map.of(AWAITING_PAYMENT, EXPIRED));
            t.put(Action.MANUAL_SWITCH, Map.of(AWAITING_PAYMENT, AWAITING_DEPOSIT));
            t.put(Action.CONFIRM, Map.of(AWAITING_DEPOSIT, SCHEDULED));
            t.put(Action.OPERATOR_CANCEL, Map.of(SUBMITTED, CANCELED, AWAITING_PAYMENT, CANCELED, AWAITING_DEPOSIT, CANCELED, SCHEDULED, CANCELED));
            t.put(Action.SYSTEM_CANCEL, Map.of(SUBMITTED, CANCELED, AWAITING_PAYMENT, CANCELED, AWAITING_DEPOSIT, CANCELED));
            t.put(Action.REFUND_FULL, Map.of(SCHEDULED, CANCELED, TAKEN_DOWN, CANCELED));
            t.put(Action.REFUND_APPROVE, Map.of(SCHEDULED, CANCELED, TAKEN_DOWN, CANCELED));
            t.put(Action.TAKEDOWN, Map.of(SCHEDULED, TAKEN_DOWN));
            t.put(Action.RESTORE, Map.of(TAKEN_DOWN, SCHEDULED));
            t.put(Action.FORFEIT, Map.of(SCHEDULED, CANCELED, TAKEN_DOWN, CANCELED));
            return t;
        }

        @Test
        void 표에_있는_조합만_허용되고_나머지는_모두_409_STATE_CHANGED() {
            for (Action action : Action.values()) {
                Map<BizApplicationStatus, BizApplicationStatus> allowed = expected.get(action);
                assertThat(allowed).as("기대 표에 " + action).isNotNull();
                for (BizApplicationStatus from : BizApplicationStatus.values()) {
                    if (allowed.containsKey(from)) {
                        assertThat(BizTransitions.require(action, from)).as(action + " " + from).isEqualTo(allowed.get(from));
                    } else {
                        assertThatThrownBy(() -> BizTransitions.require(action, from)).as(action + " " + from).isInstanceOf(BizApplicationException.class)
                                .extracting(e -> ((BizApplicationException) e).getErrorCode()).isEqualTo(BizApplicationException.STATE_CHANGED);
                    }
                }
            }
            assertThat(BizTransitions.table()).isEqualTo(expected);
        }

        @Test
        void 끝난_상태_반려_취소_만료에서는_어떤_처리도_허용되지_않는다() {
            for (BizApplicationStatus closed : List.of(REJECTED, CANCELED, EXPIRED)) {
                for (Action action : Action.values()) {
                    assertThat(BizTransitions.target(action, closed)).as(action + " " + closed).isEmpty();
                }
            }
        }

        @Test
        void 자리를_차지하는_상태는_결제_입금_대기_예약_내림이고_심사_대기는_차지하지_않는다() {
            assertThat(BizApplicationStatus.OCCUPYING).containsExactlyInAnyOrder(AWAITING_PAYMENT, AWAITING_DEPOSIT, SCHEDULED, TAKEN_DOWN);
            assertThat(SUBMITTED.occupying()).isFalse();
        }

        @Test
        void 예약_안의_단계는_시각으로_계산한다_끝_시각은_포함하지_않는다() {
            Instant s = Instant.parse("2031-01-01T00:00:00Z");
            Instant e = Instant.parse("2031-01-03T00:00:00Z");
            assertThat(BizPhase.of(s, e, s.minusSeconds(1))).isEqualTo(BizPhase.UPCOMING);
            assertThat(BizPhase.of(s, e, s)).isEqualTo(BizPhase.RUNNING);
            assertThat(BizPhase.of(s, e, e.minusSeconds(1))).isEqualTo(BizPhase.RUNNING);
            assertThat(BizPhase.of(s, e, e)).isEqualTo(BizPhase.ENDED);
        }
    }

    @Nested
    class 자리_판정 {

        private final Instant t0 = Instant.parse("2031-01-01T00:00:00Z");

        private Occupied occ(int fromDay, int toDay) {
            return new Occupied(1, t0.plusSeconds(86400L * fromDay), t0.plusSeconds(86400L * toDay));
        }

        @Test
        void 반열림_앞_건의_끝이_뒷_건의_시작과_같으면_겹치지_않는다() {
            assertThat(BizSlotService.maxOverlap(List.of(occ(0, 2), occ(2, 4)), t0, t0.plusSeconds(86400L * 4))).isEqualTo(1);
            assertThat(BizSlotService.maxOverlap(List.of(occ(0, 2), occ(1, 4)), t0, t0.plusSeconds(86400L * 4))).isEqualTo(2);
        }

        @Test
        void 최대_동시_겹침은_줄_수가_아니라_같은_순간에_겹친_수다() {
            // 0~1, 1~2, 2~3 세 줄은 한 순간에 하나씩 — 한도 1 이면 자리가 있다(줄 수 3 이 아니다)
            assertThat(BizSlotService.maxOverlap(List.of(occ(0, 1), occ(1, 2), occ(2, 3)), t0, t0.plusSeconds(86400L * 3))).isEqualTo(1);
            // 0~10 위에 2~3, 2~5 — 2~3 사이 세 줄이 겹친다
            assertThat(BizSlotService.maxOverlap(List.of(occ(0, 10), occ(2, 3), occ(2, 5)), t0, t0.plusSeconds(86400L * 10))).isEqualTo(3);
        }

        @Test
        void 판정_창_밖의_줄은_세지_않는다() {
            Instant from = t0.plusSeconds(86400L * 5);
            Instant to = t0.plusSeconds(86400L * 6);
            assertThat(BizSlotService.maxOverlap(List.of(occ(0, 5), occ(6, 9)), from, to)).isZero();
        }
    }

    @Nested
    class 금액 {

        @Test
        void 일수는_24시간_단위_올림이고_최소_1() {
            Instant s = Instant.parse("2031-01-01T00:00:00Z");
            assertThat(BizPricing.days(s, s.plusSeconds(60))).isEqualTo(1);
            assertThat(BizPricing.days(s, s.plusSeconds(86400))).isEqualTo(1);
            assertThat(BizPricing.days(s, s.plusSeconds(86400 + 60))).isEqualTo(2);
            assertThat(BizPricing.days(s, s.plusSeconds(86400L * 7))).isEqualTo(7);
        }

        @Test
        void 부가세는_공급가의_10퍼센트_원_미만_버림이고_합계는_공급가_더하기_부가세() {
            assertThat(BizPricing.amountOf(350_000)).isEqualTo(new BizPricing.Amount(350_000, 35_000, 385_000));
            assertThat(BizPricing.amountOf(12_345)).isEqualTo(new BizPricing.Amount(12_345, 1_234, 13_579));
            assertThat(BizPricing.amountOf(9)).isEqualTo(new BizPricing.Amount(9, 0, 9));
        }

        @Test
        void 일할_제안은_결제액_곱하기_남은_날_나누기_총_일수_버림_오늘은_쓴_날() {
            // 7일(11-01 ~ 11-07 KST), 오늘 11-03 → 쓴 날 3, 남은 날 4 → floor(385,000 × 4 / 7) = 220,000
            BizPeriods.Period p = BizPeriods.ofDates(LocalDate.of(2031, 11, 1), LocalDate.of(2031, 11, 7));
            Instant today = BizPeriods.startOf(LocalDate.of(2031, 11, 3)).plusSeconds(3600 * 5);
            BizPricing.Prorate r = BizPricing.prorate(385_000, p.start(), p.end(), today);
            assertThat(r.totalDays()).isEqualTo(7);
            assertThat(r.usedDays()).isEqualTo(3);
            assertThat(r.proposed()).isEqualTo(220_000);
            // 버림: floor(100 × 2 / 3) = 66
            BizPeriods.Period q = BizPeriods.ofDates(LocalDate.of(2031, 11, 1), LocalDate.of(2031, 11, 3));
            assertThat(BizPricing.prorate(100, q.start(), q.end(), BizPeriods.startOf(LocalDate.of(2031, 11, 1))).proposed()).isEqualTo(66);
            // 마지막 날은 0, 시작 전은 전액
            assertThat(BizPricing.prorate(100, q.start(), q.end(), BizPeriods.startOf(LocalDate.of(2031, 11, 3))).proposed()).isZero();
            assertThat(BizPricing.prorate(100, q.start(), q.end(), q.start().minusSeconds(1)).proposed()).isEqualTo(100);
        }
    }

    @Nested
    class 기간_변환 {

        @Test
        void 공개_날짜는_끝_포함이고_서버는_다음_날_0시까지의_반열림() {
            BizPeriods.Period p = BizPeriods.ofDates(LocalDate.of(2031, 3, 1), LocalDate.of(2031, 3, 1));
            assertThat(p.start()).isEqualTo(Instant.parse("2031-02-28T15:00:00Z"));
            assertThat(p.end()).isEqualTo(Instant.parse("2031-03-01T15:00:00Z"));
            assertThat(BizPeriods.startDate(p.start())).isEqualTo(LocalDate.of(2031, 3, 1));
            assertThat(BizPeriods.endDate(p.end())).isEqualTo(LocalDate.of(2031, 3, 1));
        }

        @Test
        void 같은_길이로_뒤로_옮기기는_다음_날_0시부터() {
            BizPeriods.Period p = BizPeriods.ofDates(LocalDate.of(2031, 3, 1), LocalDate.of(2031, 3, 3));
            BizPeriods.Period moved = BizPeriods.shiftedForward(p, BizPeriods.startOf(LocalDate.of(2031, 3, 2)).plusSeconds(100));
            assertThat(moved.start()).isEqualTo(BizPeriods.startOf(LocalDate.of(2031, 3, 3)));
            assertThat(java.time.Duration.between(moved.start(), moved.end())).isEqualTo(java.time.Duration.ofDays(3));
        }
    }

    @Nested
    class 링크_검사 {

        @Test
        void 단축_URL_은_막고_쇼핑몰_이름과_제휴를_알려준다() {
            assertThat(BizDealLinks.check("https://bit.ly/abc").orElseThrow().blocked()).isTrue();
            BizDealLinks.Check shop = BizDealLinks.check("https://www.smartstore.naver.com/x/products/1").orElseThrow();
            assertThat(shop.blocked()).isFalse();
            assertThat(shop.shopName()).isEqualTo("네이버 스마트스토어");
            assertThat(BizDealLinks.check("https://link.coupang.com/a/b").orElseThrow().affiliate()).isTrue();
            assertThat(BizDealLinks.check("ftp://example.com")).isEmpty();
            assertThat(BizDealLinks.check("not a url")).isEmpty();
        }
    }

    @Test
    void 공개_이름은_광고_종류와_노출_자리로_바꿔_읽는다() {
        assertThat(BizProduct.ofPublic("WIDE")).contains(BizProduct.WIDE_BANNER);
        assertThat(BizProduct.ofPublic("HOME_TOP")).contains(BizProduct.HOTDEAL_TOP);
        assertThat(BizProduct.ofPublic("CATEGORY_TOP")).contains(BizProduct.HOTDEAL_CATEGORY);
        assertThat(BizProduct.ofPublic("WIDE_BANNER")).isEmpty();
        Set<BizProduct> ad = EnumSet.noneOf(BizProduct.class);
        for (BizProduct p : BizProduct.values()) {
            if (p.adProduct()) {
                ad.add(p);
            }
        }
        assertThat(ad).containsExactlyInAnyOrder(BizProduct.WIDE_BANNER, BizProduct.STRIP_BANNER, BizProduct.SQUARE_BANNER, BizProduct.SMALL_BANNER, BizProduct.TEXT_LINE);
    }
}
