package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.bizapplication.entity.BizPaymentMethod;
import com.community.bizapplication.entity.BizRefundStatus;
import com.community.bizapplication.entity.BizRefundType;
import com.community.bizapplication.repository.master.BizRefundStore.RefundRow;
import com.community.bizapplication.service.BizRefundSummary;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 환불 합계(biz-pause-contract 「추가 환불(클레임)」 — 관리자 상세 {@code refundSummary}) 계산 규칙. 승인된 줄만 세고, {@code feeKept} = 승인된 중단 환불 수수료 합 − 추가 환불 합(0 ~ 남은 가능액).
 */
class BizRefundSummaryUnitTest {

    private static final Instant T = Instant.parse("2031-01-05T00:00:00Z");

    private static RefundRow row(BizRefundType type, BizRefundStatus status, Long approved, Long fee) {
        boolean stop = type == BizRefundType.STOP_PRORATED;
        return new RefundRow(1, 1, type, status, BizPaymentMethod.ONLINE, 11_000, approved == null ? 0 : approved, 0, 7, approved, null, null, null, null, null, null, null,
                null, T, T, null, stop ? (approved == null ? 0 : approved) + fee : null, stop ? 10 : null, fee, stop ? T : null);
    }

    @Test
    void 승인된_줄만_세고_남은_가능액은_결제액_빼기_승인_합() {
        BizRefundSummary s = BizRefundSummary.of(11_000, List.of(row(BizRefundType.DOWNTIME, BizRefundStatus.APPROVED, 1_571L, null),
                row(BizRefundType.STOP_PRORATED, BizRefundStatus.DENIED, null, 500L)));
        assertThat(s).isEqualTo(new BizRefundSummary(11_000, 1_571, 9_429, 0));
    }

    @Test
    void 수수료_남은_몫은_중단_환불_수수료_합에서_추가_환불을_빼고_0_아래로_내려가지_않는다() {
        RefundRow stop = row(BizRefundType.STOP_PRORATED, BizRefundStatus.APPROVED, 4_500L, 500L);
        assertThat(BizRefundSummary.of(11_000, List.of(stop))).isEqualTo(new BizRefundSummary(11_000, 4_500, 6_500, 500));
        assertThat(BizRefundSummary.of(11_000, List.of(stop, row(BizRefundType.CLAIM, BizRefundStatus.APPROVED, 200L, null))))
                .isEqualTo(new BizRefundSummary(11_000, 4_700, 6_300, 300));
        assertThat(BizRefundSummary.of(11_000, List.of(stop, row(BizRefundType.CLAIM, BizRefundStatus.APPROVED, 6_000L, null))))
                .isEqualTo(new BizRefundSummary(11_000, 10_500, 500, 0));
    }

    @Test
    void 수수료_남은_몫은_남은_가능액을_넘지_않고_다_돌려주면_모두_0() {
        // 운영자가 중단 환불을 제안보다 많이 승인해 수수료 몫까지 이미 돌려준 경우
        RefundRow stop = row(BizRefundType.STOP_PRORATED, BizRefundStatus.APPROVED, 10_800L, 500L);
        assertThat(BizRefundSummary.of(11_000, List.of(stop))).isEqualTo(new BizRefundSummary(11_000, 10_800, 200, 200));
        assertThat(BizRefundSummary.of(11_000, List.of(row(BizRefundType.FULL_AUTO, BizRefundStatus.APPROVED, 11_000L, null))))
                .isEqualTo(new BizRefundSummary(11_000, 11_000, 0, 0));
    }
}
