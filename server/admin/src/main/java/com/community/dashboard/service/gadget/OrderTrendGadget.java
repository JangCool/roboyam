package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardOrderTrendView;
import com.community.dashboard.repository.master.DashboardCommerceStore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 일별 결제 추이 — 오늘 포함 7|30일(한국 날짜, 오래된 차례, 결제가 없는 날은 0). 결제 기준은 매출 요약과 같다(받아들인 현금 결제의 결제액, 결제일 기준).
 * 결제 일별 집계 표가 아직 없어 결제 표를 결제 시각 인덱스 범위(최대 30일)로 한 번 묶는다({@link DashboardCommerceStore#paidDays}).
 */
@Component
public class OrderTrendGadget implements DashboardGadgetProvider {

    private final DashboardCommerceStore store;

    public OrderTrendGadget(DashboardCommerceStore store) {
        this.store = store;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.ORDER_TREND;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        LocalDate today = query.today();
        LocalDate from = today.minusDays(query.days() - 1L);
        Map<LocalDate, DashboardCommerceStore.Day> byDay = store.paidDays(DashboardGadgetQuery.startOf(from), DashboardGadgetQuery.startOf(today.plusDays(1))).stream()
                .collect(Collectors.toMap(DashboardCommerceStore.Day::day, Function.identity()));
        List<DashboardOrderTrendView.Day> days = new ArrayList<>(query.days());
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            DashboardCommerceStore.Day row = byDay.get(d);
            days.add(new DashboardOrderTrendView.Day(d, row == null ? 0 : row.amount(), row == null ? 0 : row.count()));
        }
        return new DashboardOrderTrendView(days);
    }
}
