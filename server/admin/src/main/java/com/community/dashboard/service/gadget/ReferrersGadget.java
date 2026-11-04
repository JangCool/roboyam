package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardReferrersView;
import com.community.dashboard.repository.master.DashboardAnalyticsStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 유입 경로(site.read · 수집 항목 {@code referrers}) — 오늘의 유입처 일별 집계({@code analytics_referrer_daily}) 그 날 줄만: 분류별 합 한 번 + 도메인 상위 5 한 번.
 * 분류는 다섯 모두 낸다(없으면 0, 많은 차례 — 같으면 정해진 차례).
 */
@Component
public class ReferrersGadget implements DashboardGadgetProvider {

    /** 분류 차례(같은 수일 때) — 수집 쪽 ReferrerClassifier 와 같은 이름 */
    static final List<String> GROUPS = List.of("SEARCH", "SOCIAL", "DIRECT", "OTHER_SITE", "CAMPAIGN");
    static final int DOMAINS = 5;

    private final DashboardAnalyticsStore store;

    public ReferrersGadget(DashboardAnalyticsStore store) {
        this.store = store;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.REFERRERS;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        Map<String, Long> byGroup = new HashMap<>();
        store.referrerGroups(query.today()).forEach(c -> byGroup.merge(c.key(), c.count(), Long::sum));
        List<DashboardReferrersView.Group> groups = new ArrayList<>();
        long total = 0;
        for (String g : GROUPS) {
            long n = byGroup.getOrDefault(g, 0L);
            total += n;
            groups.add(new DashboardReferrersView.Group(g, n));
        }
        groups.sort(Comparator.comparingLong(DashboardReferrersView.Group::visits).reversed()); // 안정 정렬 — 같으면 GROUPS 차례
        List<DashboardReferrersView.Domain> domains = store.referrerDomains(query.today(), DOMAINS).stream()
                .map(c -> new DashboardReferrersView.Domain(c.key(), c.count())).toList();
        return new DashboardReferrersView(total, groups, domains);
    }
}
