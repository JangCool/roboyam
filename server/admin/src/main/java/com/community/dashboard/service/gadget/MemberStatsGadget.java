package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.dashboard.dto.response.gadget.DashboardMemberStatsView;
import com.community.dashboard.repository.master.DashboardGadgetStore;
import org.springframework.stereotype.Component;

/**
 * 회원 현황 — 오늘(한국 날짜 0시부터 내일 0시 앞까지) 가입·탈퇴(완료) 수와 지금 정지 중·탈퇴 대기(예약) 회원 수(member.read). 네 COUNT 모두 인덱스로만 센다({@link DashboardGadgetStore#memberStats}).
 * 휴면은 기능이 없어 넣지 않는다(사용자 결정 2026-11-03).
 */
@Component
public class MemberStatsGadget implements DashboardGadgetProvider {

    private final DashboardGadgetStore store;

    public MemberStatsGadget(DashboardGadgetStore store) {
        this.store = store;
    }

    @Override
    public DashboardGadgetType type() {
        return DashboardGadgetType.MEMBER_STATS;
    }

    @Override
    public DashboardGadgetData load(DashboardGadgetQuery query) {
        long[] n = store.memberStats(DashboardGadgetQuery.startOf(query.today()), DashboardGadgetQuery.startOf(query.today().plusDays(1)));
        return new DashboardMemberStatsView(n[0], n[1], n[2], n[3]);
    }
}
