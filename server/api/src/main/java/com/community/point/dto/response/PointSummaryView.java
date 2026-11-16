package com.community.point.dto.response;

import com.community.point.dto.PointExpiring;

/**
 * {@code GET /api/me/points} — user-web {@code PointSummary}(+ community {@code api-point.md} 의 나머지 칸). 커머스 1-1 에서 무상·유상·소멸 예정·미회수를 더했다.
 *
 * @param balance             보유 포인트 합계(= 무상 + 유상, 0 이상, 한 번도 없으면 0)
 * @param freeBalance         무상(적립) 포인트 — 받은 날부터 {@code free_valid_days} 뒤 소멸
 * @param paidBalance         유상(충전) 포인트 — 소멸 없음. 충전 기능(3단계) 전에는 늘 0
 * @param chargeAvailable     충전 기능이 열렸나 — 충전 기능이 아직 없어(3단계) 늘 false(화면은 적립/충전으로 나누지 않는다, commerce D13)
 * @param expiring            가장 가까운 소멸 예정(한국 시간 날짜와 그날 소멸할 양의 합) — 없으면 null
 * @param revokeDebt          미회수 포인트(0 이상) — 다음 적립에서 먼저 차감된다
 * @param ticketCount         안 쓴 닉네임 변경권 수(포인트 상점 — 커머스 1-2)
 * @param attendanceAvailable 출석 기능이 있는가 — 커머스 1-3 부터 늘 true
 * @param restriction         포인트 적립·사용을 막는 기능 제한(포인트 동결) — 없으면 null. 동결 중에는 적립을 조용히 건너뛰므로 화면이 미리 알린다(공개 QA P-5)
 */
public record PointSummaryView(long balance, long freeBalance, long paidBalance, boolean chargeAvailable, PointExpiring expiring, long revokeDebt, int ticketCount,
                               boolean attendanceAvailable, PointTodayView today, PointRulesView rules, PointRestrictionView restriction) {
}
