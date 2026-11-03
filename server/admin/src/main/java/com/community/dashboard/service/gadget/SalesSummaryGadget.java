package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.SalesPeriods;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardSalesSummaryView;
import com.community.dashboard.repository.master.DashboardCommerceStore;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 매출 요약 — 오늘 · 어제 · 이번 달 · 지난달 같은 기간({@link SalesPeriods}, 한국 날짜)의 현금 결제(결제일 기준)·환불(환불 완료일 기준) 합·건수(commerce.md 16-2).
 * 결제·환불 표를 인덱스 시각 범위로 한 번씩 읽는다({@link DashboardCommerceStore#paidSums} · {@link DashboardCommerceStore#refundSums}).
 *
 * <p>사용자 결정 2026-11-03: 매출 범위는 결제 시험만 빼고 사업자 광고·반품 배송비·포인트 충전을 모두 넣는다(지금 그대로). 환불에는 송금 완료가 기록된 계좌 송금 환불을
 * 송금 완료 시각에 넣는다(처리 중·송금 전은 뺀다). 결과는 서버 캐시 300초(지난 기간 값은 바뀌지 않고 오늘 몫만 최대 5분 늦다 — {@code DashboardGadgetCache}).
 */
@Component
public class SalesSummaryGadget implements DashboardGadgetProvider {

    private final DashboardCommerceStore store;

    public SalesSummaryGadget(DashboardCommerceStore store) {
        this.store = store;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.SALES_SUMMARY;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        SalesPeriods p = SalesPeriods.of(query.now());
        List<SalesPeriods.Range> ranges = List.of(p.today(), p.yesterday(), p.month(), p.lastMonth());
        List<DashboardCommerceStore.Sum> paid = store.paidSums(ranges);
        List<DashboardCommerceStore.Sum> refunded = store.refundSums(ranges);
        return new DashboardSalesSummaryView(period(paid, refunded, 0), period(paid, refunded, 1), period(paid, refunded, 2), period(paid, refunded, 3));
    }

    private static DashboardSalesSummaryView.Period period(List<DashboardCommerceStore.Sum> paid, List<DashboardCommerceStore.Sum> refunded, int i) {
        return new DashboardSalesSummaryView.Period(paid.get(i).amount(), paid.get(i).count(), refunded.get(i).amount(), refunded.get(i).count());
    }
}
