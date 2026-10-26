package com.community.point.dto.response;

import com.community.point.dto.PointExpiring;
import java.util.List;

/**
 * {@code GET /api/admin/members/{id}/point-lots} — admin-web {@code PointMemberLots}. 잔액 나눔 · 소멸 예정 · 미회수 · 묶음 한 쪽 · 최근 미회수 기록.
 *
 * @param revokeDebt 미회수 포인트(0 이상) — 다음 적립에서 먼저 차감된다
 * @param expiring   가장 가까운 소멸 예정(한국 시간 날짜와 그날 소멸할 양) — 없으면 null
 * @param shortfalls 최근 것이 먼저, 최대 5건
 */
public record PointMemberLotsView(long freeBalance, long paidBalance, long revokeDebt, PointExpiring expiring, PointLotPageView lots,
                                  List<PointRevokeShortfallView> shortfalls) {
}
