package com.community.dashboard.service.gadget;

import com.community.dashboard.dto.DashboardGadgetQuery;
import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;

/**
 * 가젯 종류 하나의 데이터를 만드는 빈 — 종류마다 하나({@code DashboardGadgetService} 가 {@link #type()} 으로 모은다). 종류 권한(그중 하나라도)은 서비스가 먼저 거르고,
 * 종류 안의 항목별 권한(예: 오늘 일정의 광고·포인트 상점·배치)은 제공자가 {@link DashboardGadgetQuery#actor()} 로 거른다.
 *
 * <p><b>제공자가 없는 종류</b>는 「서버가 아직 줄 수 없음」으로 null + {@code unavailable} 이 된다 — 지금 원천이 없는 종류(방문 현황 · 알림 상태 · 글 활동 · 가입 추이 · 게시판 순위 ·
 * 회원 현황 · 제재). 커머스 종류(GROUPBUY_PROGRESS · GROUPBUY_QNA · SALES_SUMMARY · ORDER_TREND · RECONCILE)는 2단계에서 붙였다({@code DashboardCommerceStore}).
 *
 * <p>트랜잭션은 서비스가 연다(읽기 전용). 서버 부담 원칙(admin-dashboard-contract)을 지킨다 — 숫자는 인덱스로 센 COUNT, 목록은 최대 10행, 추이는 일별·시간별 집계 표만.
 */
public interface DashboardGadgetProvider {

    DashboardGadgetType type();

    DashboardGadgetData load(DashboardGadgetQuery query);
}
